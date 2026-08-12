# CleanupScheduler(notification.retention-days=90, 매일 04:00, 실제 벽시계 기준)와
# 프론트 알림함 문구("최근 90일의 알림만 보관됩니다")에 시드를 맞춘다.
# 시나리오 기준일(2026-08-14) 기준 90일 전인 2026-05-16보다 이른 created_at을 가진
# 알림 행을 전부 제거하고, 남은 행을 id 1..N으로 재번호한다.
# tasks/expenses/meetings 같은 원본 이력은 건드리지 않는다 — "그 사건을 알리는 알림"만
# 최근 것으로 제한한다(실제 서비스에서도 오래된 이벤트 자체는 안 지워지고, 그 이벤트에
# 대한 알림만 90일 지나면 사라지는 것과 같은 모양).
#
# 실행: perl src/main/resources/db/filter_notifications_90day.pl   (저장소 루트에서)
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");
use Time::Local qw(timegm);

my $DB = 'src/main/resources/db';
my $CUTOFF = '2026-05-16';   # 2026-08-14(기준일) - 90일

sub sf {
    my ($s)=@_; my @f; my $c=''; my $q=0; my $d=0; my @ch=split //,$s;
    for (my $i=0;$i<@ch;$i++){ my $x=$ch[$i];
        if($x eq "'"){ if($q && $i+1<@ch && $ch[$i+1] eq "'"){$c.="''";$i++;next;} $q=!$q;$c.=$x;next; }
        if(!$q){ if($x eq '('){$d++;$c.=$x;next;} if($x eq ')'){$d--;$c.=$x;next;} if($x eq ',' && $d==0){push @f,$c;$c='';next;} }
        $c.=$x; }
    push @f,$c; return @f;
}
sub t_ { my ($s)=@_; $s=~s/^\s+//; $s=~s/\s+$//; return $s; }
sub uq { my ($s)=@_; $s=t_($s); $s=~s/^'//; $s=~s/'$//; $s=~s/''/'/g; return $s; }
sub rd { open(my $f,'<:raw:encoding(UTF-8)',"$DB/$_[0]") or die "open $_[0]: $!"; my @l=<$f>; close $f; s/\r?\n\z// for @l; return @l; }
sub wr { open(my $f,'>:raw:encoding(UTF-8)',"$DB/$_[0]") or die "write $_[0]: $!"; print $f join("\r\n",@{$_[1]}),"\r\n"; close $f; }

my @lines = rd('seed_notifications.sql');
my (@out, @block); my $inblock = 0; my $pending_header;
my ($removed, $kept) = (0,0);
my %surv_ids;

# 헤더는 실제로 살아남은 행이 하나라도 있을 때만 낸다 — 전부 컷오프에 걸려 블록이 통째로
# 비면, 세미콜론 없는 빈 "INSERT ... VALUES"만 남아 다음 INSERT문과 붙어버리는 문제를 막는다.
my $flush = sub {
    return unless @block;
    push @out, $pending_header if defined $pending_header;
    for my $k (0..$#block) {
        $block[$k] =~ s/[,;]$//; $block[$k] =~ s/\)$//;
        push @out, $block[$k] . ($k==$#block ? ');' : '),');
    }
    @block = (); $pending_header = undef;
};

for my $l (@lines) {
    if ($l =~ /^INSERT INTO notifications /) { $flush->(); $inblock=1; $pending_header=$l; next; }
    if ($inblock && $l =~ /^\(\d+, /) {
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f = sf($b);
        if (@f == 10) {
            my $created = substr(uq($f[8]), 0, 10);
            if ($created lt $CUTOFF) { $removed++; next; }
            $kept++; $surv_ids{t_($f[0])} = 1;
        }
        push @block, $l; next;
    }
    $flush->(); $inblock=0; $pending_header=undef; push @out,$l;
}
$flush->();

# id 재번호
my %map; my $seq=0;
for my $l (@out) { next unless $l =~ /^\((\d+), \(SELECT id FROM users/; $map{$1} = ++$seq unless exists $map{$1}; }
my @final; my $dropped_lastseen = 0;
for my $l (@out) {
    if ($l =~ /^\((\d+), \(SELECT id FROM users/) {
        my $old=$1; my $new=$map{$old};
        $l =~ s/^\(\Q$old\E, /($new, /;
        push @final, $l; next;
    }
    if ($l =~ /^UPDATE users SET last_seen_notification_id = (\d+) WHERE employee_no = '(DT\d{2}-\d{4})';$/) {
        my ($old,$emp) = ($1,$2);
        my $new = $map{$old};
        unless (defined $new) {
            for (my $c=$old-1; $c>=1; $c--) { if ($map{$c}) { $new=$map{$c}; last; } }
        }
        if (defined $new) { push @final, "UPDATE users SET last_seen_notification_id = $new WHERE employee_no = '$emp';"; }
        else { $dropped_lastseen++; }  # 이 사람은 남은 알림이 0건 -> UPDATE 자체를 안 남김
        next;
    }
    push @final, $l;
}

wr('seed_notifications.sql', \@final);

printf STDERR "제거: %d건, 유지: %d건 (컷오프 %s)\n", $removed, $kept, $CUTOFF;
printf STDERR "id 재번호: 1~%d\n", $seq;
printf STDERR "last_seen_notification_id UPDATE 제거(알림 0건된 사용자): %d명\n", $dropped_lastseen;
