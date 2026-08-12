# BE는 알림 목록을 `ORDER BY id DESC`로 내려준다 — 실서비스에서는 AUTO_INCREMENT id가 곧
# 생성 순서라 안전한 가정이지만, 이 시드는 각 생성 단계(#140/#148/extra/fix/90일필터)를 거치며
# id가 "파일에 쓰여진 순서"로만 매겨져서 created_at과 무관해졌다. 화면은 createdAt을 보여주므로
# id DESC로 내려온 목록이 뒤죽박죽으로 보인다.
#
# created_at 오름차순(동시각이면 기존 id 오름차순)으로 전체를 다시 정렬해 id를 1..N으로
# 재부여한다 — 그러면 id DESC == createdAt DESC가 실서비스와 동일하게 성립한다. 21개로 흩어져
# 있던 INSERT 블록도 이 김에 하나로 합친다(더 나눠 관리할 이유가 없다).
#
# 실행: perl src/main/resources/db/resequence_notifications_by_time.pl   (저장소 루트에서)
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");

my $DB = 'src/main/resources/db';

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

open(my $in, '<:raw:encoding(UTF-8)', "$DB/seed_notifications.sql") or die;
my @lines = <$in>; close $in;
s/\r?\n\z// for @lines;

my (@head, @tail, @rows);
my $section = 'head';   # head -> notifications 블록들 -> tail(UPDATE users ...)
for my $l (@lines) {
    if ($l =~ /^INSERT INTO notifications /) { $section = 'body'; next; }   # 헤더 줄들은 버리고 새로 하나만 쓴다
    if ($section eq 'body') {
        if ($l =~ /^\(\d+, /) {
            my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
            my @f = sf($b);
            next unless @f == 10;
            push @rows, { id=>t_($f[0]), created=>uq($f[8]), fields=>\@f };
            next;
        }
        if ($l =~ /^\s*$/) { next; }   # 블록 사이 빈 줄은 버림(새로 하나로 합치므로 불필요)
        if ($l =~ /^--/) { next; }     # 이 지점의 블록 구분용 주석도 버림(헤더 쪽 설명 주석과 중복)
        $section = 'tail';             # UPDATE users ... 구간 시작
    }
    if ($section eq 'head') { push @head, $l; next; }
    push @tail, $l;
}

my $n = scalar @rows;
printf STDERR "정렬 전 알림 행: %d\n", $n;

# created_at 오름차순, 동시각이면 기존 id 오름차순(안정 정렬)
my @sorted = sort {
    $a->{created} cmp $b->{created} || $a->{id} <=> $b->{id}
} @rows;

my %map;   # 옛 id => 새 id
for my $i (0..$#sorted) { $map{ $sorted[$i]{id} } = $i + 1; }

my @body_out;
for my $i (0..$#sorted) {
    my @f = @{ $sorted[$i]{fields} };
    $f[0] = ($i + 1);   # 다른 행들과 스타일 일치: '(1, ' — 필드[0]엔 원래 선행 공백이 없다
    my $term = ($i == $#sorted) ? ');' : '),';
    push @body_out, '(' . join(',', @f) . $term;
}

# last_seen_notification_id remap. 삭제된 id를 가리키던 경우는 없다(이 스크립트는 행을 안 지움).
my @tail_out;
for my $l (@tail) {
    if ($l =~ /^UPDATE users SET last_seen_notification_id = (\d+) WHERE employee_no = '(DT\d{2}-\d{4})';$/) {
        my ($old,$emp) = ($1,$2);
        my $new = $map{$old};
        die "last_seen remap 실패: old id $old (emp=$emp)를 못 찾음" unless defined $new;
        push @tail_out, "UPDATE users SET last_seen_notification_id = $new WHERE employee_no = '$emp';";
        next;
    }
    push @tail_out, $l;
}

my @final = (@head,
    'INSERT INTO notifications (id, user_id, type, title, actor_id, target_type, target_id, read_at, created_at, modified_at) VALUES',
    @body_out,
    '',
    @tail_out);

open(my $out, '>:raw:encoding(UTF-8)', "$DB/seed_notifications.sql") or die;
print $out join("\r\n", @final), "\r\n";
close $out;

printf STDERR "재정렬 완료: id 1~%d, created_at 오름차순 == id 오름차순\n", $n;
