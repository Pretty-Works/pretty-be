# 시드 데이터 전수 검증(audit)에서 나온 정합성 문제 9건을 한 번에 고치는 스크립트.
# 실행: perl src/main/resources/db/fix_seed_consistency.pl   (저장소 루트에서)
#
# 시나리오 기준일(=데모 발표일)은 2026-08-14. 날짜 빈도가 이 날까지 일 500건대였다가
# 다음 날부터 70건대로 급락하는 것으로 확인했다.
#
# 고치는 것:
#  1) seed_schedules  회의 미러 22건의 제목이 옛 프로젝트명 → 현재 회의 제목으로 동기화
#  2) seed_schedules  회의 401~426(26건) 미러 일정 없음 → schedules + schedule_participants 생성
#  3) seed_notifications  위 26건에 대한 MEETING_CREATED / SCHEDULE_PARTICIPANT_ADDED 알림 생성
#  4) seed_expenses   기준일 이후 지출 1,200건 삭제(미래에 쓴 돈은 존재할 수 없음)
#  5) seed_tasks      기준일 이후 created_at 175건 → 기준일 이전으로 당김(마감일은 미래 유지)
#  6) seed_notifications  read_at < created_at 102건 → created_at 이후로 재계산
#  7) seed_projects   미래 마일스톤 24건의 created_at(=target_date) → 프로젝트 시작일(계획 시점)
#  8) 단발 4건 — 회의 303 author_id≠WRITER / 그 미러 일정 23336 user_id / 게시글 159가
#     프로젝트 종료 후 작성 / 퇴사자(DT24-0055) 수신 알림 10건 삭제
#  9) seed_notifications  MEETING_CREATED 수신자에서 "참석 안 한 오너·PM"이 누락 → 228건 보강
#     (MeetingService: recipients = 참석자 전원 + getManagerIds(오너·PM), 작성자·퇴사자 제외)
#
# 알림 id는 삭제·추가 후 1..N으로 다시 매기고, users.last_seen_notification_id 도 같이 remap 한다.
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");
use Time::Local qw(timegm);

my $DB    = 'src/main/resources/db';
my $TODAY = '2026-08-14';
my $TODAY_END = timegm(0,0,18,14,7,2026);   # 2026-08-14 18:00

# ---------------- 공용 ----------------
sub sf {   # 괄호/따옴표 인식 필드 분해
    my ($s)=@_; my @f; my $c=''; my $q=0; my $d=0; my @ch=split //,$s;
    for (my $i=0;$i<@ch;$i++){ my $x=$ch[$i];
        if($x eq "'"){ if($q && $i+1<@ch && $ch[$i+1] eq "'"){$c.="''";$i++;next;} $q=!$q;$c.=$x;next; }
        if(!$q){ if($x eq '('){$d++;$c.=$x;next;} if($x eq ')'){$d--;$c.=$x;next;} if($x eq ',' && $d==0){push @f,$c;$c='';next;} }
        $c.=$x; }
    push @f,$c; return @f;
}
sub t_  { my ($s)=@_; $s=~s/^\s+//; $s=~s/\s+$//; return $s; }
sub uq  { my ($s)=@_; $s=t_($s); $s=~s/^'//; $s=~s/'$//; $s=~s/''/'/g; return $s; }
sub nv  { my ($s)=@_; $s=t_($s); return undef if $s eq 'NULL'; return uq($s); }   # NULL → undef
sub esc { my ($s)=@_; $s=~s/'/''/g; return $s; }
sub en  { my ($s)=@_; return ($s =~ /employee_no='(DT\d{2}-\d{4})'/) ? $1 : undef; }
sub epoch { my ($s)=@_; my ($y,$mo,$d,$h,$mi,$se)= $s =~ /^(\d{4})-(\d{2})-(\d{2})(?: (\d{2}):(\d{2}):(\d{2}))?/;
            return timegm($se//0,$mi//0,$h//0,$d,$mo-1,$y); }
sub fmt { my ($e)=@_; my @t=gmtime($e); return sprintf("%04d-%02d-%02d %02d:%02d:%02d",$t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]); }
sub rd  { my ($file)=@_; open(my $f,'<:raw:encoding(UTF-8)',"$DB/$file") or die "open $file: $!";
          my @l=<$f>; close $f; s/\r?\n\z// for @l; return @l; }
sub wr  { my ($file,$lines)=@_; open(my $f,'>:raw:encoding(UTF-8)',"$DB/$file") or die "write $file: $!";
          print $f join("\r\n", @$lines), "\r\n"; close $f; }

my %STAT;

# ================= 참조 데이터 적재 =================
my (%ustatus,%udept);
for my $l (rd('seed_users.sql')) {
    next unless $l =~ /^\('DT\d{2}-\d{4}'/;
    my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
    my @f=sf($b); next unless @f==13;
    $ustatus{uq($f[0])}=uq($f[9]); $udept{uq($f[0])}=uq($f[7]);
}
my (%pstart,%pactive,%powner);
{
    my $tb='';
    for my $l (rd('seed_projects.sql')) {
        if ($l =~ /^INSERT INTO (\w+) /) { $tb=$1; next; }
        next unless $l =~ /^\(/;
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b);
        if ($tb eq 'projects' && @f==10) { $pstart{t_($f[0])} = uq($f[3]); }
        elsif ($tb eq 'project_members' && @f==8) {
            next unless uq($f[4]) eq 'ACTIVE';
            my ($pid,$e)=(t_($f[0]), en($f[1]));
            $pactive{$pid}{$e}=1; $powner{$pid}=$e if t_($f[2]) eq '1';
        }
    }
}
my (%mproj,%mauth,%mtitle,%mdate,%mattend);
{
    my $tb='';
    for my $l (rd('seed_meetings.sql')) {
        if ($l =~ /^INSERT INTO (\w+) /) { $tb=$1; next; }
        next unless $l =~ /^\(/;
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b);
        if ($tb eq 'meetings' && @f==14) {
            my $id=t_($f[0]);
            $mproj{$id}=t_($f[1]); $mauth{$id}=en($f[2]); $mtitle{$id}=uq($f[4]); $mdate{$id}=uq($f[5]);
        } elsif ($tb eq 'meeting_attendees' && @f==7) {
            push @{$mattend{t_($f[0])}}, [ en($f[1]), uq($f[4]) ];   # [empno, role]
        }
    }
}
# 회의 303 author 교정은 아래 (8)에서 파일을 고치지만, 이후 계산이 새 값을 쓰도록 여기서도 맞춰둔다
$mauth{303} = 'DT22-0162';

# MEETING_CREATED 수신자 = 참석자 ∪ 관리자(오너·PM) − 작성자 − 퇴사자
sub meeting_recipients {
    my ($mid)=@_;
    my $pj=$mproj{$mid}; my $au=$mauth{$mid};
    my %r; $r{$_->[0]}=1 for @{$mattend{$mid}||[]};
    for my $e (keys %{$pactive{$pj}||{}}) {
        $r{$e}=1 if (($powner{$pj}//'') eq $e) || (($udept{$e}//'') eq 'PM');
    }
    return sort grep { $_ ne $au && (($ustatus{$_}//'') ne 'RESIGNED') } keys %r;
}

# ================= (8-a) seed_meetings: 회의 303 author 교정 =================
{
    my @lines = rd('seed_meetings.sql'); my $n=0;
    for my $l (@lines) {
        if ($l =~ /^\(303, 30, \(SELECT id FROM users WHERE employee_no='DT22-0154'\)/) {
            $l =~ s/^\(303, 30, \(SELECT id FROM users WHERE employee_no='DT22-0154'\)/(303, 30, (SELECT id FROM users WHERE employee_no='DT22-0162')/;
            $n++;
        }
    }
    die "meeting 303 author 교정 실패(${n}건)" unless $n==1;
    wr('seed_meetings.sql', \@lines);
    $STAT{'8 회의303 author 교정'} = $n;
}

# ================= (7) seed_projects: 미래 마일스톤 created_at =================
{
    my @lines = rd('seed_projects.sql'); my $tb=''; my $n=0;
    for my $l (@lines) {
        if ($l =~ /^INSERT INTO (\w+) /) { $tb=$1; next; }
        next unless $tb eq 'milestones' && $l =~ /^\(/;
        my $term = ($l =~ /\);$/) ? ');' : '),';
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b); next unless @f==6;
        my $created = uq($f[4]);
        next unless substr($created,0,10) gt $TODAY;
        my $pid = t_($f[0]);
        my $plan = ($pstart{$pid} // substr($created,0,10)) . ' 09:00:00';
        $f[4] = " '$plan'"; $f[5] = " '$plan'";
        $l = '(' . join(',', @f) . $term;
        $n++;
    }
    die "마일스톤 교정 건수 이상: $n (24 기대)" unless $n==24;
    wr('seed_projects.sql', \@lines);
    $STAT{'7 마일스톤 created_at'} = $n;
}

# ================= (8-c) seed_posts: 게시글 159 =================
{
    my @lines = rd('seed_posts.sql'); my $n=0;
    for my $l (@lines) {
        next unless $l =~ /^\(159, 28, /;
        $n += ($l =~ s/'2024-04-22 09:00:00', '2024-04-22 09:00:00'/'2024-04-19 09:00:00', '2024-04-19 09:00:00'/);
    }
    die "게시글 159 교정 실패(${n}건)" unless $n==1;
    wr('seed_posts.sql', \@lines);
    $STAT{'8 게시글159 날짜'} = $n;
}

# ================= (5) seed_tasks: 미래 created_at =================
{
    my @lines = rd('seed_tasks.sql'); my $n=0; my $i=0;
    for my $l (@lines) {
        next unless $l =~ /^\(/;
        my $term = ($l =~ /\);$/) ? ');' : '),';
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b); next unless @f==8;
        my $created = uq($f[6]);
        next unless substr($created,0,10) gt $TODAY;
        die "미래 created_at 인데 완료 처리된 task 발견: $l" if defined nv($f[4]);
        my $due = uq($f[5]);
        # 기준일 직전 2주에 결정적으로 분산, 단 마감일보다는 앞서야 한다
        my $cand = epoch('2026-08-01') + ($i % 14)*86400 + 9*3600;
        my $due_minus1 = epoch($due) - 86400 + 9*3600;
        $cand = $due_minus1 if $cand >= epoch($due);
        my $new = fmt($cand);
        $f[6] = " '$new'"; $f[7] = " '$new'";
        $l = '(' . join(',', @f) . $term;
        $n++; $i++;
    }
    die "task 교정 건수 이상: $n (175 기대)" unless $n==175;
    wr('seed_tasks.sql', \@lines);
    $STAT{'5 task created_at'} = $n;
}

# ================= (4) seed_expenses: 기준일 이후 지출 삭제 =================
{
    my @lines = rd('seed_expenses.sql');
    my (@out, @block); my $inblock=0; my $removed=0; my $kept=0;
    my $flush = sub {
        return unless @block;
        for my $k (0..$#block) {
            my $isl = ($k == $#block);
            $block[$k] =~ s/[,;]$//; $block[$k] =~ s/\)$//;
            push @out, $block[$k] . ($isl ? ');' : '),');
        }
        @block = ();
    };
    for my $l (@lines) {
        if ($l =~ /^INSERT INTO expenses /) { $flush->(); $inblock=1; push @out,$l; next; }
        if ($inblock && $l =~ /^\(/) {
            my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
            my @f=sf($b);
            if (@f==12 && uq($f[3]) gt $TODAY) { $removed++; next; }
            $kept++; push @block, $l; next;
        }
        $flush->(); $inblock=0; push @out,$l;
    }
    $flush->();
    die "지출 삭제 건수 이상: $removed (1200 기대)" unless $removed==1200;
    wr('seed_expenses.sql', \@out);
    $STAT{'4 미래 지출 삭제'} = $removed;
}

# ================= (1)(2)(8-b) seed_schedules =================
my @new_sched;   # [schedule_id, meeting_id]
my %stitle;      # schedule_id => 교정 후 제목
{
    my @lines = rd('seed_schedules.sql');
    my $tb=''; my $title_fixed=0; my $writer_fixed=0; my $maxid=0;
    for my $l (@lines) {
        if ($l =~ /^INSERT INTO (\w+) /) { $tb=$1; next; }
        next unless $tb eq 'schedules' && $l =~ /^\(/;
        my $term = ($l =~ /\);$/) ? ');' : '),';
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b); next unless @f==9;
        my $id=t_($f[0]);
        $maxid = $id if $id > $maxid;
        # (1) id정렬 미러 구간의 제목 동기화
        if ($id <= 288 && exists $mtitle{$id} && uq($f[2]) ne $mtitle{$id}) {
            $f[2] = " '" . esc($mtitle{$id}) . "'"; $title_fixed++;
        }
        # (8-b) 회의 303 미러 일정의 작성자
        if ($id == 23336) {
            $f[1] = " (SELECT id FROM users WHERE employee_no='DT22-0162')"; $writer_fixed++;
        }
        $l = '(' . join(',', @f) . $term;
    }
    die "미러 제목 교정 건수 이상: $title_fixed (22 기대)" unless $title_fixed==22;
    die "일정 23336 작성자 교정 실패" unless $writer_fixed==1;
    # 교정 후 확정된 일정 제목 (SCHEDULE_* 알림 문구 재동기화에 쓴다)
    for my $l (@lines) {
        next unless $l =~ /^\(\d+, \(SELECT id FROM users/;
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b); next unless @f==9;
        $stitle{t_($f[0])} = uq($f[2]);
    }

    # (2) 회의 401~426 미러 일정 생성
    my @mids = sort { $a <=> $b } grep { $_ >= 401 && $_ <= 426 } keys %mtitle;
    die "미러 생성 대상 회의 수 이상: ".scalar(@mids)." (26 기대)" unless @mids==26;
    my (@srows,@prows);
    my $sid = $maxid;
    for my $mid (@mids) {
        $sid++;
        push @new_sched, [$sid, $mid];
        my $ti = $mtitle{$mid}; my $dt = $mdate{$mid};
        my $long = ($ti =~ /킥오프|착수|점검|완료|회고|검증|마무리|승인|보고/) ? 1 : 0;
        my $h = $long ? 10 + ($mid % 7) : 10 + ($mid % 8);
        my $st = epoch($dt) + $h*3600;
        my $et = $st + ($long ? 3600 : 1800);
        my $ct = fmt(epoch($dt) + 9*3600);
        push @srows, sprintf("(%d, (SELECT id FROM users WHERE employee_no='%s'), '%s', '%s', '%s', FALSE, 'MEETING', '%s', '%s')",
                             $sid, $mauth{$mid}, esc($ti), fmt($st), fmt($et), $ct, $ct);
        for my $a (@{$mattend{$mid}}) {
            my ($e,$role) = @$a;
            push @prows, sprintf("(%d, (SELECT id FROM users WHERE employee_no='%s'), %s, '%s', '%s')",
                                 $sid, $e, ($role eq 'WRITER' ? 'TRUE' : 'FALSE'), $ct, $ct);
        }
    }
    push @lines, '';
    push @lines, '-- 정합성 보정: 회의 401~426(2차 Tier2 보강분) 미러 일정 — 기존 회의 미러 규칙(A단계) 그대로 적용';
    push @lines, 'INSERT INTO schedules (id, user_id, title, start_at, end_at, all_day, type, created_at, modified_at) VALUES';
    push @lines, map { $srows[$_] . ($_==$#srows ? ';' : ',') } 0..$#srows;
    push @lines, 'INSERT INTO schedule_participants (schedule_id, user_id, is_writer, created_at, modified_at) VALUES';
    push @lines, map { $prows[$_] . ($_==$#prows ? ';' : ',') } 0..$#prows;

    wr('seed_schedules.sql', \@lines);
    $STAT{'1 미러 제목 동기화'} = $title_fixed;
    $STAT{'2 미러 일정 생성'}   = scalar(@srows) . '건 + 참가자 ' . scalar(@prows) . '건';
    $STAT{'8 일정23336 작성자'} = $writer_fixed;
}

# ================= (3)(6)(8-d)(9) seed_notifications =================
{
    my @lines = rd('seed_notifications.sql');
    my %del = map { $_ => 1 } qw(9179 9182 9184 9189 9192 9861 9864 9866 9871 9874);

    # 기존 MEETING_CREATED 수신자 집합 수집(중복 추가 방지용): (회의제목, 수신자)
    my %have_mc;
    for my $l (@lines) {
        next unless $l =~ /^\(\d+, /;
        next unless $l =~ /'MEETING_CREATED'/;
        my ($recv) = $l =~ /^\(\d+, \(SELECT id FROM users WHERE employee_no='(DT\d{2}-\d{4})'\)/;
        my ($ti)   = $l =~ /회의록이 등록되었습니다: ((?:[^']|'')*)',/;
        my ($ct)   = $l =~ /'(\d{4}-\d{2}-\d{2}) \d{2}:\d{2}:\d{2}'\)?,?$/;
        next unless defined $recv && defined $ti;
        $ti =~ s/''/'/g;
        # 같은 프로젝트에 동일 제목 회의가 있어(p6 회의 49·50) 제목만으로는 구분이 안 된다 → 회의 날짜 포함.
        # 시각까지 쓰면 안 된다 — MEETING_CREATED의 기존 created_at은 09:05인데 회의일 09:00과 어긋난다.
        $have_mc{"$ti|$recv|".($ct//'')} = 1;
    }

    my (@out,@block); my $inblock=0; my $removed=0; my $readfix=0; my $titlesync=0;
    my $flush = sub {
        return unless @block;
        for my $k (0..$#block) {
            $block[$k] =~ s/[,;]$//; $block[$k] =~ s/\)$//;
            push @out, $block[$k] . ($k==$#block ? ');' : '),');
        }
        @block=();
    };
    for my $l (@lines) {
        if ($l =~ /^INSERT INTO notifications /) { $flush->(); $inblock=1; push @out,$l; next; }
        if ($inblock && $l =~ /^\(\d+, /) {
            my $term_b=$l; $term_b=~s/[,;]$//; $term_b=~s/\)$//; $term_b=~s/^\(//;
            my @f=sf($term_b);
            if (@f==10) {
                my $id=t_($f[0]);
                if ($del{$id}) { $removed++; next; }              # (8-d) 퇴사자 수신 삭제
                my $ra=nv($f[7]); my $ca=uq($f[8]);
                my $touched = 0;
                if (defined $ra && $ra lt $ca) {                  # (6) read_at 재계산
                    my $ne = epoch($ca) + 1800 + (($id % 48) * 3600);
                    $ne = $TODAY_END if $ne > $TODAY_END;
                    $f[7] = " '" . fmt($ne) . "'";
                    $readfix++; $touched=1;
                }
                # (10) SCHEDULE_* 알림 문구가 인용한 일정 제목을 교정 후 제목과 재동기화
                if (uq($f[5]) eq 'SCHEDULE') {
                    my $sid = t_($f[6]);
                    my $want = $stitle{$sid};
                    if (defined $want) {
                        my $cur = uq($f[3]);
                        if ($cur =~ /^'(.*?)' 일정/ && $1 ne $want) {
                            $cur =~ s/^'.*?' 일정/'$want' 일정/;
                            $f[3] = " '" . esc($cur) . "'";
                            $titlesync++; $touched=1;
                        }
                    }
                }
                $l = '(' . join(',', @f) . ($l =~ /\);$/ ? ');' : '),') if $touched;
            }
            push @block,$l; next;
        }
        $flush->(); $inblock=0; push @out,$l;
    }
    $flush->();
    die "퇴사자 알림 삭제 건수 이상: $removed (10 기대)" unless $removed==10;
    die "read_at 교정 건수 이상: $readfix (102 기대)" unless $readfix==102;

    # (9) 기존 회의(1~308)에서 빠진 관리자 수신자 보강 + (3) 신규 회의 401~426 알림
    my (@mc_rows, @sp_rows);
    my %sched_of = map { $_->[1] => $_->[0] } @new_sched;
    for my $mid (sort { $a <=> $b } keys %mtitle) {
        next unless $mid <= 308 || ($mid >= 401 && $mid <= 426);
        my $pj = $mproj{$mid}; my $au = $mauth{$mid};
        my $d  = $mdate{$mid};
        my $ct_mc = fmt(epoch($d) + 9*3600 + 300);   # MEETING_CREATED 기존 규칙: 회의일 09:05
        my $ct    = fmt(epoch($d) + 9*3600);         # 일정 계열 기존 규칙: 09:00
        for my $r (meeting_recipients($mid)) {
            next if $have_mc{"$mtitle{$mid}|$r|$d"};             # 이미 있는 건 건드리지 않음
            $have_mc{"$mtitle{$mid}|$r|$d"} = 1;
            push @mc_rows, [ $r, 'MEETING_CREATED',
                "'".$mtitle{$mid}."' 프로젝트에 회의록이 등록되었습니다: ".$mtitle{$mid},
                $au, 'PROJECT', $pj, $ct_mc, $mid ];
        }
        # 신규 미러 일정에 대한 참가자 추가 알림
        next unless $sched_of{$mid};
        for my $a (@{$mattend{$mid}}) {
            my ($e,$role)=@$a;
            next if $role eq 'WRITER' || $e eq $au;
            next if ($ustatus{$e}//'') eq 'RESIGNED';
            push @sp_rows, [ $e, 'SCHEDULE_PARTICIPANT_ADDED',
                "'".$mtitle{$mid}."' 일정에 참가자로 추가되었습니다",
                $au, 'SCHEDULE', $sched_of{$mid}, $ct, $mid ];
        }
    }
    # 알림 제목의 프로젝트명은 projects.name 이어야 한다 — MEETING_CREATED 문구 재조립
    my %pname;
    { my $tb='';
      for my $l (rd('seed_projects.sql')) {
        if ($l =~ /^INSERT INTO (\w+) /) { $tb=$1; next; }
        next unless $tb eq 'projects' && $l =~ /^\(/;
        my $b=$l; $b=~s/[,;]$//; $b=~s/\)$//; $b=~s/^\(//;
        my @f=sf($b); next unless @f==10; $pname{t_($f[0])}=uq($f[1]); } }
    for my $r (@mc_rows) {
        $r->[2] = "'".$pname{$r->[5]}."' 프로젝트에 회의록이 등록되었습니다: ".$mtitle{$r->[7]};
    }

    # read_at 확률(기존 규칙)
    srand(20260818);
    my $mk = sub {
        my ($row,$id)=@_;
        my ($recv,$type,$title,$actor,$tt,$ti,$ct) = @$row;
        my $elapsed = int((epoch($TODAY) - epoch($ct))/86400);
        my $p = $elapsed < 2 ? 0.15 : $elapsed <= 7 ? 0.45 : $elapsed <= 30 ? 0.75 : 0.95;
        my $ra = 'NULL';
        if (rand() <= $p) {
            my $e = epoch($ct) + 1800 + int(rand(3*24*3600));
            $e = $TODAY_END if $e > $TODAY_END;
            $ra = "'".fmt($e)."'";
        }
        return sprintf("(%d, (SELECT id FROM users WHERE employee_no='%s'), '%s', '%s', (SELECT id FROM users WHERE employee_no='%s'), '%s', %s, %s, '%s', '%s')",
                       $id, $recv, $type, esc($title), $actor, $tt, $ti, $ra, $ct, $ct);
    };

    # 기존 마지막 id 다음부터 부여(뒤에서 전체 재번호하므로 임시값)
    my $next = 1; $next++ for grep { /^\(\d+, / } @out;
    my @added;
    my $tmp = 900000;
    push @added, $mk->($_, ++$tmp) for @mc_rows;
    push @added, $mk->($_, ++$tmp) for @sp_rows;

    if (@added) {
        push @out, '';
        push @out, '-- 정합성 보정: MEETING_CREATED 수신자 누락분(참석 안 한 오너·PM — MeetingService의';
        push @out, '-- getManagerIds 규칙) + 회의 401~426 신규 미러 일정의 회의록/참가자 추가 알림';
        push @out, 'INSERT INTO notifications (id, user_id, type, title, actor_id, target_type, target_id, read_at, created_at, modified_at) VALUES';
        push @out, map { $added[$_] . ($_==$#added ? ';' : ',') } 0..$#added;
    }

    # ---- id 전체 재번호 + last_seen remap ----
    my %map; my $seq=0;
    for my $l (@out) {
        next unless $l =~ /^\((\d+), \(SELECT id FROM users/;
        $map{$1} = ++$seq unless exists $map{$1};
    }
    for my $l (@out) {
        if ($l =~ /^\((\d+), \(SELECT id FROM users/) {
            my $old=$1; my $new=$map{$old};
            $l =~ s/^\(\Q$old\E, /($new, /;
        } elsif ($l =~ /last_seen_notification_id = (\d+)/) {
            my $old=$1;
            my $new = $map{$old};
            unless (defined $new) {   # 삭제된 id를 가리키던 경우 → 그 앞의 가장 가까운 살아있는 id
                for (my $c=$old-1; $c>=1; $c--) { if ($map{$c}) { $new=$map{$c}; last; } }
            }
            $l =~ s/last_seen_notification_id = \Q$old\E/last_seen_notification_id = $new/;
        }
    }
    wr('seed_notifications.sql', \@out);
    $STAT{'6 read_at 재계산'}       = $readfix;
    $STAT{'10 일정제목 문구 동기화'} = $titlesync;
    $STAT{'8 퇴사자 알림 삭제'}      = $removed;
    $STAT{'9 MEETING_CREATED 보강'} = scalar(@mc_rows);
    $STAT{'3 신규 일정 참가자 알림'} = scalar(@sp_rows);
    $STAT{'알림 최종 건수'}          = $seq;
}

print "===== 수정 완료 =====\n";
printf "  %-28s %s\n", $_, $STAT{$_} for sort keys %STAT;
