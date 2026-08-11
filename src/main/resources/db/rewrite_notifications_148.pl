# PR #148(알림 문구·수신자 정책 개선)에 맞춰 seed_notifications.sql을 1회성으로 재작성한 스크립트.
# 실행: perl src/main/resources/db/rewrite_notifications_148.pl  (저장소 루트에서)
#
# 하는 일:
#   1) TASK_ASSIGNED/TASK_DELETED/TASK_DUE_DATE_CHANGED(총 3,948건) 문구를 옛 템플릿
#      (할 일 내용만 인용)에서 새 템플릿("'{프로젝트명}' 프로젝트에 ...: {할 일 내용}")으로 재작성.
#      프로젝트명은 target_id로 seed_projects.sql에서 조회.
#   2) PROJECT_MEMBER_REMOVED(4건)의 target_type/target_id를 NULL로 재작성(제외된 프로젝트로
#      다시 보낼 곳이 없어짐 — #148에서 이동 대상 없는 알림은 NULL 처리로 정책 변경).
#   3) 그 외 모든 행(id·수신자·행위자·read_at/created_at 등)은 절대 건드리지 않음.
# 이미 한 번 실행되어 seed_notifications.sql에 반영 완료된 상태이므로, 다시 실행하면 옛 템플릿
# 패턴이 더 이상 없어 "패턴 불일치" 오류로 즉시 중단한다(안전장치 — 이중 적용 방지). 재실행이
# 필요하면 git으로 seed_notifications.sql을 #148 이전 상태로 되돌린 뒤 실행할 것.
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8");
binmode(STDERR, ":utf8");

# ---- 프로젝트명 맵 ----
my %pname;
{
    open(my $fh, '<:encoding(UTF-8)', 'src/main/resources/db/seed_projects.sql') or die;
    while (my $l = <$fh>) {
        if ($l =~ /^\((\d+), '((?:[^']|'')*)', '[A-Z]+',/) {
            my ($id, $name) = ($1, $2);
            $name =~ s/''/'/g;
            $pname{$id} = $name;
        }
    }
    close $fh;
}
printf STDERR "projects loaded: %d\n", scalar keys %pname;

# ---- 필드 분해 (괄호/따옴표 인식) ----
sub split_fields {
    my ($s) = @_;
    my @f; my $cur=''; my $inq=0; my $depth=0;
    my @ch = split //, $s;
    for (my $i=0; $i<@ch; $i++) {
        my $c = $ch[$i];
        if ($c eq "'") {
            if ($inq && $i+1 < @ch && $ch[$i+1] eq "'") { $cur .= "''"; $i++; next; }
            $inq = !$inq; $cur .= $c; next;
        }
        if (!$inq) {
            if ($c eq '(') { $depth++; $cur .= $c; next; }
            if ($c eq ')') { $depth--; $cur .= $c; next; }
            if ($c eq ',' && $depth==0) { push @f,$cur; $cur=''; next; }
        }
        $cur .= $c;
    }
    push @f, $cur;
    return @f;
}

sub trim { my ($s) = @_; $s =~ s/^\s+//; $s =~ s/\s+$//; return $s; }
sub unquote { my ($s) = @_; $s = trim($s); $s =~ s/^'//; $s =~ s/'$//; return $s; }
sub esc { my ($s) = @_; $s =~ s/'/''/g; return $s; }
sub unesc { my ($s) = @_; $s =~ s/''/'/g; return $s; }

my $infile = 'src/main/resources/db/seed_notifications.sql';
open(my $in, '<:encoding(UTF-8)', $infile) or die;
my @lines = <$in>;
close $in;

my $changed_task = 0;
my $changed_member_removed = 0;
my @errors;

for my $ln (0..$#lines) {
    my $line = $lines[$ln];
    next unless $line =~ /^\(\d+, /;

    my $term = '';
    if ($line =~ /\),\s*\n?$/) { $term = '),' ; }
    elsif ($line =~ /\);\s*\n?$/) { $term = ');'; }
    else { next; }

    my $body = $line;
    $body =~ s/\Q$term\E\s*\n?$//;
    $body =~ s/^\(//;

    my @f = split_fields($body);
    next unless scalar(@f) == 10;

    my $type = unquote($f[2]);

    if ($type eq 'TASK_ASSIGNED' || $type eq 'TASK_DELETED' || $type eq 'TASK_DUE_DATE_CHANGED') {
        my $title = unquote($f[3]);
        my $tid = trim($f[6]);
        my $pname = $pname{$tid};
        unless (defined $pname) { push @errors, "id=".trim($f[0])." type=$type target_id=$tid: 프로젝트명을 찾을 수 없음"; next; }

        # 논리적(비-SQL-escape) 문자열을 먼저 완성하고, 마지막에 딱 한 번만 esc()한다.
        # (템플릿 자체가 넣는 홑따옴표도 SQL에서는 이스케이프 대상이므로, pname/content만
        #  개별로 esc()하면 템플릿의 리터럴 따옴표가 이스케이프 안 되는 버그가 생긴다.)
        my $logical_title;
        if ($type eq 'TASK_ASSIGNED') {
            if ($title =~ /^''((?:[^']|'')*)'' 할 일이 배정되었습니다 \(마감 (\d{4}-\d{2}-\d{2})\)$/) {
                my $content = unesc($1);
                $logical_title = "'$pname' 프로젝트에 할 일이 배정되었습니다: $content";
            } else {
                push @errors, "id=".trim($f[0])." TASK_ASSIGNED 패턴 불일치: [$title]"; next;
            }
        } elsif ($type eq 'TASK_DELETED') {
            if ($title =~ /^배정된 ''((?:[^']|'')*)'' 할 일이 삭제되었습니다$/) {
                my $content = unesc($1);
                $logical_title = "'$pname' 프로젝트에서 할 일이 삭제되었습니다: $content";
            } else {
                push @errors, "id=".trim($f[0])." TASK_DELETED 패턴 불일치: [$title]"; next;
            }
        } elsif ($type eq 'TASK_DUE_DATE_CHANGED') {
            if ($title =~ /^''((?:[^']|'')*)'' 할 일의 마감일이 (\d{4}-\d{2}-\d{2}) 로 변경되었습니다$/) {
                my ($content, $date) = (unesc($1), $2);
                $logical_title = "'$pname' 프로젝트의 할 일 마감일이 $date 로 변경되었습니다: $content";
            } else {
                push @errors, "id=".trim($f[0])." TASK_DUE_DATE_CHANGED 패턴 불일치: [$title]"; next;
            }
        }

        $f[3] = " '" . esc($logical_title) . "'";
        $changed_task++;
        my $newbody = '(' . join(',', @f) . $term;
        $lines[$ln] = $newbody . "\n";
        next;
    }

    if ($type eq 'PROJECT_MEMBER_REMOVED') {
        $f[5] = ' NULL';
        $f[6] = ' NULL';
        $changed_member_removed++;
        my $newbody = '(' . join(',', @f) . $term;
        $lines[$ln] = $newbody . "\n";
        next;
    }
}

if (@errors) {
    print STDERR "=== 오류 " . scalar(@errors) . "건 (파일 미저장) ===\n";
    print STDERR "$_\n" for @errors[0..(($#errors>19)?19:$#errors)];
    die "패턴 불일치로 중단\n";
}

open(my $out, '>:encoding(UTF-8)', $infile) or die;
print $out @lines;
close $out;

printf STDERR "TASK_* 변경: %d건\n", $changed_task;
printf STDERR "PROJECT_MEMBER_REMOVED 변경: %d건\n", $changed_member_removed;
