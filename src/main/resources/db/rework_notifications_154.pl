# feat/#154(알림 문구·타겟 라우팅 개편) — Java 쪽 NotificationType 문구·NotificationTarget 라우팅
# 변경에 시드를 맞춘다. 기존 2,319행 중 프로젝트 4종(PROJECT_MEMBER_ADDED/REMOVED,
# PROJECT_STATUS_CHANGED/PERIOD_CHANGED) 103행만 그대로 두고 나머지 문구를 새 템플릿으로
# 재작성하며, 게시글·회의록 알림은 target을 POST/MEETING + target_project_id로 재구성한다.
# 빠져 있던 3종(SCHEDULE_TIME_CHANGED/TASK_DELETED/추가 SCHEDULE_PARTICIPANT_REMOVED)도
# 실제 사건(seed_schedules.sql UPDATE, 기존 tasks 재사용)과 함께 새로 만든다.
#
# 실행 순서: 이 스크립트 → resequence_notifications_by_time.pl (id를 created_at 순으로 재배정)
# 실행: perl src/main/resources/db/rework_notifications_154.pl   (저장소 루트에서)
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");
use Time::Local qw(timegm);

my $DB = 'src/main/resources/db';
my $STAGE = '/tmp/n154';
my $TODAY = '2026-08-14';
my $TODAY_END = timegm(0,0,18,14,7,2026);

# ---------------- 공용 ----------------
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
sub nv { my ($s)=@_; $s=t_($s); return undef if $s eq 'NULL'; return uq($s); }
sub esc { my ($s)=@_; $s=~s/'/''/g; return $s; }
sub epoch { my ($s)=@_; my ($y,$mo,$d,$h,$mi,$se)= $s =~ /^(\d{4})-(\d{2})-(\d{2})(?: (\d{2}):(\d{2}):(\d{2}))?/;
            return timegm($se//0,$mi//0,$h//0,$d,$mo-1,$y); }
sub fmt { my ($e)=@_; my @t=gmtime($e); return sprintf("%04d-%02d-%02d %02d:%02d:%02d",$t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]); }
sub rd { open(my $f,'<:raw:encoding(UTF-8)',"$DB/$_[0]") or die "open $_[0]: $!"; my @l=<$f>; close $f; s/\r?\n\z// for @l; return @l; }
sub wr { open(my $f,'>:raw:encoding(UTF-8)',"$DB/$_[0]") or die "write $_[0]: $!"; print $f join("\r\n", @{$_[1]}), "\r\n"; close $f; }

# 조사 — Java NotificationType.resolveJosa와 동일 알고리즘.
sub josa_for {
    my ($ch) = @_;
    return '가' unless defined $ch;
    my $cp = ord($ch);
    return '가' if $cp < 0xAC00 || $cp > 0xD7A3;
    return (($cp - 0xAC00) % 28 != 0) ? '이' : '가';
}
# "[이름]" 형태에서 이름의 마지막 글자를 뽑아 조사를 계산한다.
sub josa_after_bracket {
    my ($name) = @_;
    return josa_for(substr($name, -1, 1));
}

# ================= 참조 데이터 로드 =================
my %pname;
open(my $pf, '<:encoding(UTF-8)', "$STAGE/projects.tsv") or die;
while (<$pf>) { chomp; my ($id,$name) = split /\|/, $_, 2; $pname{$id} = $name; }
close $pf;

# posts: (project_id, title) => [postId, authorEmp]  — project 내 제목 중복 0건 확인됨
my %post_by_title;
open(my $pof, '<:encoding(UTF-8)', "$STAGE/posts.tsv") or die;
while (<$pof>) { chomp; my ($id,$pid,$au,$ti,$prio,$ca,$ma) = split /\|/, $_, 7;
    $post_by_title{"$pid|$ti"} = $id; }
close $pof;

# meetings: (project_id, title, date) => meetingId — 날짜까지 키에 넣어 동일 프로젝트 내 제목 중복(p6) 구분
my %meeting_by_title_date;
open(my $mf, '<:encoding(UTF-8)', "$STAGE/meetings.tsv") or die;
while (<$mf>) { chomp; my ($id,$pid,$au,$ti,$d) = split /\|/, $_, 5;
    $meeting_by_title_date{"$pid|$ti|$d"} = $id; }
close $mf;

# schedules: id => {owner,title,start,end,type}
my %sched;
open(my $sf, '<:encoding(UTF-8)', "$STAGE/schedules.tsv") or die;
while (<$sf>) { chomp; my ($id,$owner,$ti,$st,$et,$ty) = split /\|/, $_, 6;
    $sched{$id} = {owner=>$owner, ti=>$ti, st=>$st, et=>$et, ty=>$ty}; }
close $sf;

# ================= 1) 기존 2,319행 변환 =================
my @lines = rd('seed_notifications.sql');
my (@out, @block); my $inblock = 0; my $pending_header;
my %stat;

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
        my @f = sf($b); next unless @f == 10;

        my $type = uq($f[2]);
        my $title = uq($f[3]);
        my $tt = nv($f[5]);
        my $tid = nv($f[6]);

        my ($new_title, $new_tt, $new_tid, $new_tpid, $new_tdate);
        $new_tt = $tt; $new_tid = $tid; $new_tpid = undef; $new_tdate = undef;

        if ($type =~ /^PROJECT_(MEMBER_ADDED|MEMBER_REMOVED|STATUS_CHANGED|PERIOD_CHANGED)$/) {
            $new_title = $title;   # 변경 없음
            $stat{$type}++;
        }
        elsif ($type eq 'MILESTONE_COMPLETED') {
            $title =~ /^'((?:[^']|'')*)' 마일스톤이 완료되었습니다$/
                or die "MILESTONE_COMPLETED 패턴 불일치: [$title]";
            my $goal = $1; $goal =~ s/''/'/g;
            my $proj = $pname{$tid} // die "project $tid 이름 없음(MILESTONE_COMPLETED)";
            my $josa = josa_after_bracket($goal);
            $new_title = "'$proj' 마일스톤 [$goal]${josa} 완료되었습니다";
            $stat{$type}++;
        }
        elsif ($type eq 'EXPENSE_CREATED') {
            $title =~ s/ 프로젝트에 (\d[\d,]*원 지출이 등록되었습니다)$/ $1/
                or die "EXPENSE_CREATED 패턴 불일치: [$title]";
            $new_title = $title;
            $stat{$type}++;
        }
        elsif ($type eq 'TASK_ASSIGNED') {
            $title =~ /^'((?:[^']|'')*)' 프로젝트에 할 일이 배정되었습니다: ((?:[^']|'')*)$/
                or die "TASK_ASSIGNED 패턴 불일치: [$title]";
            my ($proj, $content) = ($1, $2); $proj=~s/''/'/g; $content=~s/''/'/g;
            my $josa = josa_after_bracket($content);
            $new_title = "'$proj' 할일 [$content]${josa} 배정되었습니다";
            $stat{$type}++;
        }
        elsif ($type eq 'TASK_DELETED') {
            $title =~ /^'((?:[^']|'')*)' 프로젝트에서 할 일이 삭제되었습니다: ((?:[^']|'')*)$/
                or die "TASK_DELETED 패턴 불일치: [$title]";
            my ($proj, $content) = ($1, $2); $proj=~s/''/'/g; $content=~s/''/'/g;
            my $josa = josa_after_bracket($content);
            $new_title = "'$proj' 할일 [$content]${josa} 삭제되었습니다";
            $stat{$type}++;
        }
        elsif ($type eq 'TASK_DUE_DATE_CHANGED') {
            $title =~ /^'((?:[^']|'')*)' 프로젝트의 할 일 마감일이 (\d{4}-\d{2}-\d{2}) 로 변경되었습니다: ((?:[^']|'')*)$/
                or die "TASK_DUE_DATE_CHANGED 패턴 불일치: [$title]";
            my ($proj, $date, $content) = ($1, $2, $3); $proj=~s/''/'/g; $content=~s/''/'/g;
            $new_title = "'$proj' 할일 [$content]의 마감일이 $date 로 변경되었습니다";
            $stat{$type}++;
        }
        elsif ($type eq 'POST_CREATED') {
            $title =~ /^'((?:[^']|'')*)' 프로젝트에 중요 게시글이 등록되었습니다: ((?:[^']|'')*)$/
                or die "POST_CREATED 패턴 불일치: [$title]";
            my ($proj, $ptitle) = ($1, $2); $proj=~s/''/'/g; $ptitle=~s/''/'/g;
            my $postId = $post_by_title{"$tid|$ptitle"}
                // die "POST_CREATED: post 못 찾음 project=$tid title=[$ptitle]";
            my $josa = josa_after_bracket($ptitle);
            $new_title = "'$proj' 게시판에 [$ptitle]${josa} 등록되었습니다";
            $new_tt = 'POST'; $new_tid = $postId; $new_tpid = $tid;
            $stat{$type}++;
        }
        elsif ($type eq 'POST_UPDATED') {
            $title =~ /^'((?:[^']|'')*)' 게시글이 수정되었습니다$/
                or die "POST_UPDATED 패턴 불일치: [$title]";
            my $ptitle = $1; $ptitle =~ s/''/'/g;
            my $proj = $pname{$tid} // die "project $tid 이름 없음(POST_UPDATED)";
            my $postId = $post_by_title{"$tid|$ptitle"}
                // die "POST_UPDATED: post 못 찾음 project=$tid title=[$ptitle]";
            my $josa = josa_after_bracket($ptitle);
            $new_title = "'$proj' 게시판의 [$ptitle]${josa} 수정되었습니다";
            $new_tt = 'POST'; $new_tid = $postId; $new_tpid = $tid;
            $stat{$type}++;
        }
        elsif ($type eq 'MEETING_CREATED') {
            $title =~ /^'((?:[^']|'')*)' 프로젝트에 회의록이 등록되었습니다: ((?:[^']|'')*)$/
                or die "MEETING_CREATED 패턴 불일치: [$title]";
            my ($proj, $mtitle) = ($1, $2); $proj=~s/''/'/g; $mtitle=~s/''/'/g;
            my $created = uq($f[8]);
            my $mdate = substr($created, 0, 10);   # created_at = 회의일 09:05
            my $meetingId = $meeting_by_title_date{"$tid|$mtitle|$mdate"}
                // die "MEETING_CREATED: meeting 못 찾음 project=$tid title=[$mtitle] date=$mdate";
            my $josa = josa_after_bracket($mtitle);
            $new_title = "'$proj' 회의록에 [$mtitle]${josa} 등록되었습니다";
            $new_tt = 'MEETING'; $new_tid = $meetingId; $new_tpid = $tid;
            $stat{$type}++;
        }
        elsif ($type eq 'SCHEDULE_PARTICIPANT_ADDED') {
            $title =~ /^'((?:[^']|'')*)' 일정에 참가자로 추가되었습니다$/
                or die "SCHEDULE_PARTICIPANT_ADDED 패턴 불일치: [$title]";
            my $sti = $1; $sti =~ s/''/'/g;
            $new_title = "[$sti]에 참가자로 추가되었습니다";
            $stat{$type}++;
        }
        elsif ($type eq 'SCHEDULE_PARTICIPANT_REMOVED') {
            $title =~ /^'((?:[^']|'')*)' 일정에서 제외되었습니다$/
                or die "SCHEDULE_PARTICIPANT_REMOVED 패턴 불일치: [$title]";
            my $sti = $1; $sti =~ s/''/'/g;
            $new_title = "[$sti]에서 제외되었습니다";
            my $s = $sched{$tid} // die "SCHEDULE_PARTICIPANT_REMOVED: schedule $tid 없음";
            $new_tt = undef; $new_tid = undef; $new_tdate = substr($s->{st}, 0, 10);
            $stat{$type}++;
        }
        elsif ($type eq 'SCHEDULE_DELETED') {
            $title =~ /^'((?:[^']|'')*)' 일정이 삭제되었습니다$/
                or die "SCHEDULE_DELETED 패턴 불일치: [$title]";
            my $sti = $1; $sti =~ s/''/'/g;
            my $josa = josa_after_bracket($sti);
            $new_title = "[$sti]${josa} 삭제되었습니다";
            $new_tt = undef; $new_tid = undef;
            $new_tdate = substr(uq($f[8]), 0, 10);   # 참조할 일정이 없어 알림 시각의 날짜를 쓴다
            $stat{$type}++;
        }
        else {
            die "알 수 없는 타입(또는 처리 누락): $type";
        }

        # $new_title은 위 모든 분기에서 순수 논리 문자열(이스케이프 안 됨)로만 조립했다.
        # 배지("'프로젝트명'")의 리터럴 홑따옴표까지 포함해서 여기서 딱 한 번만 이스케이프한다 —
        # 분기 안에서 부분적으로 esc()를 걸면 배지 경계의 홑따옴표가 이스케이프 안 된 채 남아
        # SQL 문자열이 중간에 끊기는 버그가 난다(실제로 한 번 냈다 — #148 rewrite 스크립트와
        # 동일한 유형의 실수).
        $f[3] = " '" . esc($new_title) . "'";
        $f[5] = defined($new_tt) ? " '$new_tt'" : ' NULL';
        $f[6] = defined($new_tid) ? " $new_tid" : ' NULL';
        splice(@f, 7, 0, defined($new_tpid) ? " $new_tpid" : ' NULL');
        splice(@f, 8, 0, defined($new_tdate) ? " '$new_tdate'" : ' NULL');

        push @block, '(' . join(',', @f) . ')';
        next;
    }
    $flush->(); $inblock=0; $pending_header=undef; push @out,$l;
}
$flush->();

print STDERR "===== 기존 행 변환 =====\n";
printf STDERR "  %-28s %d\n", $_, $stat{$_} for sort keys %stat;

# ================= 2) 빠진 3종 신규 생성 =================
my @new_notif; my @sched_updates; my @part_updates;
my $tmp_id = 5000000;   # 임시 id(뒤에서 resequence 스크립트가 전부 다시 매긴다)

sub read_at_for {
    my ($ca) = @_;
    my $elapsed = int((epoch($TODAY) - epoch($ca)) / 86400);
    my $p = $elapsed < 2 ? 0.15 : $elapsed <= 7 ? 0.45 : $elapsed <= 30 ? 0.75 : 0.95;
    return 'NULL' if rand() > $p;
    my $e = epoch($ca) + 1800 + int(rand(3*24*3600));
    $e = $TODAY_END if $e > $TODAY_END;
    return "'" . fmt($e) . "'";
}

srand(20260819);

# --- SCHEDULE_TIME_CHANGED (8건): seed_schedules.sql에 modified_at 편집 이벤트도 같이 만든다 ---
open(my $tcf, '<:encoding(UTF-8)', "$STAGE/pick_time_changed.tsv") or die;
while (<$tcf>) {
    chomp; my ($sid, $owner, $sti, $st, $et, $members_csv) = split /\|/, $_, 6;
    my @members = split /,/, $members_csv;
    my $edit = fmt(epoch($st) - 40*60);   # 회의 40분 전 편집(fix_seed_consistency.pl과 같은 서사)
    push @sched_updates, "UPDATE schedules SET modified_at = '$edit' WHERE id = $sid;";

    my ($sd,$sh) = $st =~ /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})/;
    my ($ed,$eh) = $et =~ /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})/;
    my $title = "[$sti]의 시간이 $sd $sh ~ $ed $eh 로 변경되었습니다";   # 논리 문자열, 이스케이프는 삽입 시 1회
    for my $emp (@members) {
        $tmp_id++;
        my $ra = read_at_for($edit);
        push @new_notif, "($tmp_id, (SELECT id FROM users WHERE employee_no='$emp'), 'SCHEDULE_TIME_CHANGED', '".esc($title)."', (SELECT id FROM users WHERE employee_no='$owner'), 'SCHEDULE', $sid, NULL, NULL, $ra, '$edit', '$edit')";
    }
}
close $tcf;
print STDERR "SCHEDULE_TIME_CHANGED 신규: ".(($tmp_id-5000000))." 건\n";
my $after_tc = $tmp_id;

# --- SCHEDULE_PARTICIPANT_REMOVED 추가(7건): left_at 세팅 + modified_at 편집 ---
open(my $prf, '<:encoding(UTF-8)', "$STAGE/pick_participant_removed.tsv") or die;
while (<$prf>) {
    chomp; my ($sid, $owner, $sti, $st, $et, $members_csv) = split /\|/, $_, 6;
    my @members = split /,/, $members_csv;
    my $removed_emp = $members[-1];
    my $edit = fmt(epoch($st) - 30*60);
    push @part_updates, "UPDATE schedule_participants SET left_at = '$edit' WHERE schedule_id = $sid AND user_id = (SELECT id FROM users WHERE employee_no='$removed_emp');";
    push @sched_updates, "UPDATE schedules SET modified_at = '$edit' WHERE id = $sid;";

    my $date = substr($st, 0, 10);
    my $title = "[$sti]에서 제외되었습니다";   # 논리 문자열, 이스케이프는 삽입 시 1회
    $tmp_id++;
    my $ra = read_at_for($edit);
    push @new_notif, "($tmp_id, (SELECT id FROM users WHERE employee_no='$removed_emp'), 'SCHEDULE_PARTICIPANT_REMOVED', '".esc($title)."', (SELECT id FROM users WHERE employee_no='$owner'), NULL, NULL, NULL, '$date', $ra, '$edit', '$edit')";
}
close $prf;
print STDERR "SCHEDULE_PARTICIPANT_REMOVED 신규: ".($tmp_id-$after_tc)." 건\n";
my $after_pr = $tmp_id;

# --- TASK_DELETED (8건): 기존 tasks 행은 안 지우고, 삭제됐다는 알림만 새로 만든다(기존 시드 관례) ---
open(my $tdf, '<:encoding(UTF-8)', "$STAGE/pick_task_deleted.tsv") or die;
while (<$tdf>) {
    chomp; my ($pid, $creator, $assignee, $content, $created) = split /\|/, $_, 5;
    my $proj = $pname{$pid} // die "project $pid 이름 없음(TASK_DELETED 신규)";
    my $josa = josa_after_bracket($content);
    my $title = "'$proj' 할일 [$content]${josa} 삭제되었습니다";   # 논리 문자열, 이스케이프는 삽입 시 1회
    # 삭제 시점: 배정 후 2~6일 뒤, 기준일 이전
    my $del_epoch = epoch($created) + (2 + int(rand(5)))*86400 + 3600*int(rand(8));
    $del_epoch = $TODAY_END - 86400 if $del_epoch > $TODAY_END;
    my $del = fmt($del_epoch);
    $tmp_id++;
    my $ra = read_at_for($del);
    push @new_notif, "($tmp_id, (SELECT id FROM users WHERE employee_no='$assignee'), 'TASK_DELETED', '".esc($title)."', (SELECT id FROM users WHERE employee_no='$creator'), 'PROJECT', $pid, NULL, NULL, $ra, '$del', '$del')";
}
close $tdf;
print STDERR "TASK_DELETED 신규: ".($tmp_id-$after_pr)." 건\n";

# ================= 파일 반영 =================
if (@new_notif) {
    push @out, '';
    push @out, '-- #154 보강: 확인 안 되던 3종(SCHEDULE_TIME_CHANGED/추가 SCHEDULE_PARTICIPANT_REMOVED/';
    push @out, '-- TASK_DELETED) 90일 창 안 실제 사건으로 신규 생성 — rework_notifications_154.pl';
    push @out, 'INSERT INTO notifications (id, user_id, type, title, actor_id, target_type, target_id, target_project_id, target_date, read_at, created_at, modified_at) VALUES';
    push @out, map { $new_notif[$_] . ($_==$#new_notif ? ';' : ',') } 0..$#new_notif;
}
wr('seed_notifications.sql', \@out);

if (@sched_updates || @part_updates) {
    my @slines = rd('seed_schedules.sql');
    push @slines, '';
    push @slines, '-- #154 보강: SCHEDULE_TIME_CHANGED/PARTICIPANT_REMOVED 신규 알림의 근거 편집 이벤트';
    push @slines, @part_updates, @sched_updates;
    wr('seed_schedules.sql', \@slines);
}

print STDERR "\n완료. 신규 notification 행(임시id 기준): ".scalar(@new_notif)."건\n";
print STDERR "다음 단계: perl src/main/resources/db/resequence_notifications_by_time.pl\n";
