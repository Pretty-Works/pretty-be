# "일정은 보통 미래 일정도 있다"는 피드백 반영 — 지금까지 schedules 최대 날짜가 정확히
# 기준일(2026-08-14)에서 끊겨 있던 것을, 기준일 이후 3주(~9월 첫째 주)까지 확장한다.
#
# A) 부서 주간회의(14개 팀 × 3주 = 42건): 참조 주(2026-08-10~08-14)의 마지막 인스턴스를 그대로
#    복제 — 매주 같은 이름으로 반복되는 게 실제로도 자연스러운 회의라 복제해도 문제없다.
#    참가자도 그 주 그대로 복제(레퍼런스 주 자체가 이미 재직/휴직 상태를 반영해서 만들어진
#    데이터라 별도 필터링 불필요 — RESIGNED는애초에 그 주 데이터에 없다).
#
# B) 핵심계정 개인 업무 일정(13명 × 3주 = 39건): 복제 대신 새로 작성. ONGOING 프로젝트에 실제로
#    연결된(오너 또는 멤버) 사람만 대상으로 하고, 그 프로젝트의 가장 최근 실제 회의(seed_meetings.sql)
#    를 이어받는 후속 작업으로 제목을 지었다 — 창작이 아니라 이미 있는 서사의 다음 단계.
#    퇴사자(DT24-0055)·ONGOING 연결이 없는 나머지 계정은 대상에서 제외했다(휴직자 DT22-0209,
#    신입 DT26-0002도 ONGOING 연결이 없어 제외 — 부서 주간회의로는 이미 포함돼 있다).
#
# 실행: perl src/main/resources/db/extend_schedules_future.pl   (저장소 루트에서)
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");
use Time::Local qw(timegm);

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
sub esc { my ($s)=@_; $s=~s/'/''/g; return $s; }
sub epoch { my ($s)=@_; my ($y,$mo,$d,$h,$mi,$se)= $s =~ /^(\d{4})-(\d{2})-(\d{2})(?: (\d{2}):(\d{2}):(\d{2}))?/;
            return timegm($se//0,$mi//0,$h//0,$d,$mo-1,$y); }
sub fmt { my ($e)=@_; my @t=gmtime($e); return sprintf("%04d-%02d-%02d %02d:%02d:%02d",$t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]); }
sub rd { open(my $f,'<:raw:encoding(UTF-8)',"$DB/$_[0]") or die "open $_[0]: $!"; my @l=<$f>; close $f; s/\r?\n\z// for @l; return @l; }

my @WEEK_OFFSETS = (7, 14, 21);   # 08-10 기준 +1주, +2주, +3주 (08-17, 08-24, 08-31 각 주)

# ================= A) 부서 주간회의 복제 =================
my @lines = rd('seed_schedules.sql');
my (%sched_row, %sched_part, $maxid);
my $tb = ''; my $cur_sid;
for my $l (@lines) {
    if ($l =~ /^INSERT INTO (\w+) /) { $tb = $1; next; }
    next unless $l =~ /^\(/;
    if ($tb eq 'schedules') {
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f = sf($b); next unless @f==9;
        my $id = t_($f[0]);
        $maxid = $id if !defined($maxid) || $id > $maxid;
        if (uq($f[2]) =~ /주간회의$/ && substr(uq($f[3]),0,10) eq '2026-08-10') {
            $sched_row{$id} = \@f;
        }
    } elsif ($tb eq 'schedule_participants') {
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f = sf($b); next unless @f==5;
        my $sid = t_($f[0]);
        push @{$sched_part{$sid}}, \@f if $sched_row{$sid};
    }
}
printf STDERR "A) 참조 주 부서 주간회의 발견: %d건\n", scalar keys %sched_row;
die "부서 주간회의 14개를 못 찾음" unless scalar(keys %sched_row) == 14;

my (@new_sched_lines, @new_part_lines);
my $nid = $maxid + 1;
for my $offset (@WEEK_OFFSETS) {
    for my $sid (sort { $a <=> $b } keys %sched_row) {
        my @f = @{ $sched_row{$sid} };
        my @nf = @f;
        $nf[0] = $nid;
        for my $idx (3,4,7,8) {   # start_at, end_at, created_at, modified_at
            my $v = uq($f[$idx]);
            my $shifted = fmt(epoch($v) + $offset*86400);
            $nf[$idx] = " '$shifted'";
        }
        push @new_sched_lines, '(' . join(',', @nf) . '),';
        for my $pf (@{$sched_part{$sid} || []}) {
            my @npf = @$pf;
            $npf[0] = $nid;
            for my $idx (3,4) {
                my $v = uq($pf->[$idx]);
                $npf[$idx] = " '" . fmt(epoch($v) + $offset*86400) . "'";
            }
            push @new_part_lines, '(' . join(',', @npf) . '),';
        }
        $nid++;
    }
}
printf STDERR "A) 신규 부서 주간회의: %d건, 참가자: %d건\n", scalar(@new_sched_lines), scalar(@new_part_lines);

# ================= B) 핵심계정 개인 업무 일정 =================
# emp => { pid, title_w1, title_w2, title_w3, hour }
my %PLAN = (
    'DT22-0024' => { pid=>8, hour=>10, t=>['인증 심사 서류 초안 작성','인증 심사 서류 검토 반영','인증 심사 일정 조율'] },
    'DT22-0007' => { pid=>8, hour=>14, t=>['취약점 조치 항목 후속 확인','로그 보관 정책 문서 정비','심사 대응 자료 최종 점검'] },
    'DT22-0016' => { pid=>5, hour=>11, t=>['아키텍처 설계 최종 확정','개발 착수 준비','1차 개발 범위 조율'] },
    'DT22-0019' => { pid=>2, hour=>10, t=>['QA 이슈 조치 현황 확인','오픈 준비 체크리스트 점검','정식 오픈 일정 조율'] },
    'DT22-0018' => { pid=>2, hour=>14, t=>['QA 발견 화면 이슈 수정','오픈 전 UI 최종 점검','오픈 대응 화면 모니터링 준비'] },
    'DT22-0055' => { pid=>2, hour=>16, t=>['오픈 전 배포 파이프라인 점검','모니터링 알림 설정 점검','오픈 당일 배포 리허설'] },
    'DT22-0022' => { pid=>9, hour=>11, t=>['이중화 구축 진행 상황 점검','네트워크 구간 이중화 검증','전원 계열 이중화 검증'] },
    'DT22-0023' => { pid=>6, hour=>10, t=>['데이터 검증 잔여 항목 확인','전송 방식 적용 범위 점검','시행일 대응 일정 재점검'] },
    'DT22-0102' => { pid=>6, hour=>13, t=>['신규 데이터 표준 항목 반영','전송 방식 테스트 케이스 정리','검증 결과 문서화'] },
    'DT22-0104' => { pid=>6, hour=>15, t=>['데이터 검증 오류 케이스 분석','전송 방식 연동 테스트','잔여 오류 재검증'] },
    'DT22-0095' => { pid=>4, hour=>11, t=>['1차 산출물 피드백 반영','로드맵 초안 작성','로드맵 검토 회의 준비'] },
    'DT23-0008' => { pid=>4, hour=>14, t=>['현행 시스템 진단 자료 정리','개선 로드맵 자료 취합','산출물 최종본 정리'] },
    'DT22-0017' => { pid=>1, hour=>10, t=>['2차 스프린트 진행 상황 점검','자동 심사 확대 적용 준비','오픈 일정 재점검'] },
);

my @WEEK_MONDAYS = ('2026-08-17', '2026-08-24', '2026-08-31');

for my $emp (sort keys %PLAN) {
    my $p = $PLAN{$emp};
    for my $wi (0..2) {
        my $day = epoch($WEEK_MONDAYS[$wi]) + 1*86400;   # 화요일 고정(부서 주간회의=월요일과 겹치지 않게)
        my $start = fmt($day + $p->{hour}*3600);
        my $end   = fmt($day + ($p->{hour}+2)*3600);
        my $created = fmt($day + 9*3600);   # 파일 전체 관례: created_at은 그날 09:00:00
        my $title = esc($p->{t}[$wi]);
        push @new_sched_lines,
            "($nid, (SELECT id FROM users WHERE employee_no='$emp'), '$title', '$start', '$end', FALSE, 'PERSONAL', '$created', '$created'),";
        push @new_part_lines,
            "($nid, (SELECT id FROM users WHERE employee_no='$emp'), TRUE, '$created', '$created'),";
        $nid++;
    }
}
printf STDERR "B) 핵심계정 개인 업무 일정: %d건\n", scalar(keys %PLAN) * 3;

# 마지막 종결자는 세미콜론으로
$new_sched_lines[-1] =~ s/,$/;/;
$new_part_lines[-1]  =~ s/,$/;/;

# ================= 파일에 반영 =================
open(my $so, '>>:raw:encoding(UTF-8)', "$DB/seed_schedules.sql") or die;
print $so "\r\n-- 미래 일정 보강(기준일 이후 3주, ~9월 첫째 주): A) 부서 주간회의 복제 42건 ";
print $so "+ B) 핵심계정(ONGOING 프로젝트 연결) 개인 업무 일정 39건 — extend_schedules_future.pl\r\n";
print $so "INSERT INTO schedules (id, user_id, title, start_at, end_at, all_day, type, created_at, modified_at) VALUES\r\n";
print $so join("\r\n", @new_sched_lines), "\r\n";
print $so "INSERT INTO schedule_participants (schedule_id, user_id, is_writer, created_at, modified_at) VALUES\r\n";
print $so join("\r\n", @new_part_lines), "\r\n";
close $so;

printf STDERR "완료. 마지막 schedule id: %d\n", $nid-1;
