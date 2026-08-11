# #140에서 도입됐지만 근거 데이터가 없어 0건으로 비워뒀던 마지막 4종 알림
# (POST_UPDATED/SCHEDULE_PARTICIPANT_REMOVED/SCHEDULE_TIME_CHANGED/SCHEDULE_DELETED)의
# 근거 이벤트를 만들고, 그로부터 파생되는 notifications 236건(id 10310~10545)을 생성한다.
#
# 실행 전제: /tmp/stage/ 아래 스테이징 tsv(high_posts.tsv, post_updated_picks.tsv,
# target_project_members.tsv, removed_picks.tsv, time_picks.tsv, deleted_project_owners.tsv,
# project_dates.tsv)가 이미 있어야 한다 — seed_posts.sql/seed_projects.sql/seed_schedules.sql을
# 직접 파싱해서 만든 참조용 파일들이라 이 스크립트엔 그 추출 과정이 없다(재실행하려면 먼저
# 그 tsv들을 다시 뽑아야 함 — 각 tsv의 컬럼 의미는 아래 코드에서 split하는 부분 참고).
#
# 출력은 SQL이 아니라 3개의 SQL "조각" 파일이다(/tmp/stage/out_*.sql) — seed_posts.sql/
# seed_schedules.sql엔 UPDATE문을, seed_notifications.sql엔 새 INSERT VALUES 행을 각각
# 파일 끝에 이어 붙이는 방식으로 반영했다(기존 INSERT 블록의 세미콜론을 건드리지 않기 위함).
#
# 실행: perl src/main/resources/db/gen_notifications_extra_148.pl  (저장소 루트에서)
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");
use Time::Local qw(timegm);

srand(20260817); # 고정 시드 — 이 세션 표기 관례(2026081x)를 따름, 재실행해도 같은 결과

sub esc { my ($s) = @_; $s =~ s/'/''/g; return $s; }

# yyyy-mm-dd[ hh:mm[:ss]] -> epoch days (UTC 기준, 일수 비교용)
sub to_days {
    my ($s) = @_;
    my ($y,$mo,$d) = $s =~ /^(\d{4})-(\d{2})-(\d{2})/;
    return int(timegm(0,0,0,$d,$mo-1,$y) / 86400);
}
my $TODAY = "2026-08-11";
my $TODAY_DAYS = to_days($TODAY);

# read_at 확률(경과일 기반, 기존 시드 관례와 동일) — 읽었으면 생성 시각 + 30분~3일 랜덤 오프셋(오늘 초과 금지)
sub gen_read_at {
    my ($created) = @_;
    my $elapsed = $TODAY_DAYS - to_days($created);
    my $p = $elapsed < 2 ? 0.15 : $elapsed <= 7 ? 0.45 : $elapsed <= 30 ? 0.75 : 0.95;
    return undef if rand() > $p;
    my ($y,$mo,$d,$h,$mi,$s) = $created =~ /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})/;
    my $base = timegm($s,$mi,$h,$d,$mo-1,$y);
    my $offset_min = 30 + int(rand(3*24*60 - 30)); # 30분~3일
    my $read_epoch = $base + $offset_min*60;
    my $today_end = timegm(0,0,0,(split /-/, $TODAY)[2], (split /-/, $TODAY)[1]-1, (split /-/, $TODAY)[0]) + 18*3600;
    $read_epoch = $today_end if $read_epoch > $today_end;
    my @t = gmtime($read_epoch);
    return sprintf("%04d-%02d-%02d %02d:%02d:%02d", $t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]);
}

my $nid = 10310; # 다음 notification id (기존 최대 10309 다음)
my @notif_rows;
my @post_updates;
my @schedule_participant_updates;
my @schedule_modified_updates;

sub add_notif {
    my (%a) = @_; # user_emp, type, title, actor_emp, target_type, target_id, created_at
    my $read_at = gen_read_at($a{created_at});
    my $read_sql = defined($read_at) ? "'$read_at'" : "NULL";
    my $target_type_sql = defined($a{target_type}) ? "'$a{target_type}'" : "NULL";
    my $target_id_sql = defined($a{target_id}) ? $a{target_id} : "NULL";
    push @notif_rows, "($nid, (SELECT id FROM users WHERE employee_no='$a{user_emp}'), '$a{type}', '".esc($a{title})."', (SELECT id FROM users WHERE employee_no='$a{actor_emp}'), $target_type_sql, $target_id_sql, $read_sql, '$a{created_at}', '$a{created_at}')";
    $nid++;
}

# ============ 1) POST_UPDATED ============
my %proj_members; # pid => [ [emp,isowner], ... ]
open(my $mf, "<:encoding(UTF-8)", "/tmp/stage/target_project_members.tsv") or die;
while (<$mf>) { chomp; my ($pid,$emp,$isowner) = split /\|/; push @{$proj_members{$pid}}, [$emp,$isowner]; }
close $mf;

open(my $pf, "<:encoding(UTF-8)", "/tmp/stage/post_updated_picks.tsv") or die;
my $pidx = 0;
while (<$pf>) {
    chomp;
    my ($id,$pid,$emp,$created,$title) = split /\|/, $_, 5;
    $pidx++;
    # 수정 시점: 작성일 + (2 + id%7)일, 오늘 18:00 초과 금지
    my ($y,$mo,$d,$h,$mi,$s) = $created =~ /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})/;
    my $base = timegm($s,$mi,$h,$d,$mo-1,$y);
    my $offset_days = 2 + ($id % 7);
    my $mod_epoch = $base + $offset_days*86400;
    my $today_end = timegm(0,0,0,11,7,2026) + 18*3600; # 2026-08-11 18:00
    $mod_epoch = $today_end if $mod_epoch > $today_end;
    my @t = gmtime($mod_epoch);
    my $modified = sprintf("%04d-%02d-%02d %02d:%02d:%02d", $t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]);

    push @post_updates, "UPDATE project_posts SET modified_at = '$modified' WHERE id = $id;";

    for my $m (@{$proj_members{$pid}}) {
        my ($memp, $isowner) = @$m;
        next if $memp eq $emp; # 작성자 본인 제외
        add_notif(
            user_emp => $memp, type => 'POST_UPDATED', title => "'$title' 게시글이 수정되었습니다",
            actor_emp => $emp, target_type => 'PROJECT', target_id => $pid, created_at => $modified,
        );
    }
}
close $pf;
print STDERR "POST_UPDATED: posts=".$pidx.", notif=".scalar(@notif_rows)."\n";

# ============ 2) SCHEDULE_PARTICIPANT_REMOVED ============
my $before_removed = scalar(@notif_rows);
open(my $rf, "<:encoding(UTF-8)", "/tmp/stage/removed_picks.tsv") or die;
while (<$rf>) {
    chomp;
    my ($sid, $owner, $title, $start, $end, $members_csv) = split /\|/;
    my @members = split /,/, $members_csv;
    my $removed_emp = $members[-1]; # 마지막 참가자를 제거 대상으로 결정적 선택

    my ($y,$mo,$d,$h,$mi,$s) = $start =~ /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})/;
    my $start_epoch = timegm($s,$mi,$h,$d,$mo-1,$y);
    my $edit_epoch = $start_epoch - 30*60; # 회의 30분 전 편집
    my @t = gmtime($edit_epoch);
    my $edit = sprintf("%04d-%02d-%02d %02d:%02d:%02d", $t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]);

    push @schedule_participant_updates,
        "UPDATE schedule_participants SET left_at = '$edit' WHERE schedule_id = $sid AND user_id = (SELECT id FROM users WHERE employee_no='$removed_emp');";
    push @schedule_modified_updates, "UPDATE schedules SET modified_at = '$edit' WHERE id = $sid;";

    add_notif(
        user_emp => $removed_emp, type => 'SCHEDULE_PARTICIPANT_REMOVED', title => "'$title' 일정에서 제외되었습니다",
        actor_emp => $owner, target_type => 'SCHEDULE', target_id => $sid, created_at => $edit,
    );
}
close $rf;
print STDERR "SCHEDULE_PARTICIPANT_REMOVED: notif=".(scalar(@notif_rows)-$before_removed)."\n";

# ============ 3) SCHEDULE_TIME_CHANGED ============
my $before_time = scalar(@notif_rows);
open(my $tf, "<:encoding(UTF-8)", "/tmp/stage/time_picks.tsv") or die;
while (<$tf>) {
    chomp;
    my ($sid, $owner, $title, $start, $end, $members_csv) = split /\|/;
    my @members = split /,/, $members_csv;

    my ($y,$mo,$d,$h,$mi,$s) = $start =~ /^(\d{4})-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})/;
    my $start_epoch = timegm($s,$mi,$h,$d,$mo-1,$y);
    my $edit_epoch = $start_epoch - 45*60;
    my @t = gmtime($edit_epoch);
    my $edit = sprintf("%04d-%02d-%02d %02d:%02d:%02d", $t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]);

    push @schedule_modified_updates, "UPDATE schedules SET modified_at = '$edit' WHERE id = $sid;";

    # formatWhen: allDay가 아니므로 'yyyy-MM-dd HH:mm'
    my ($sd,$sh) = $start =~ /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})/;
    my ($ed,$eh) = $end =~ /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})/;
    my $start_fmt = "$sd $sh";
    my $end_fmt = "$ed $eh";

    for my $memp (@members) {
        add_notif(
            user_emp => $memp, type => 'SCHEDULE_TIME_CHANGED',
            title => "'$title' 일정 시간이 $start_fmt ~ $end_fmt 로 변경되었습니다",
            actor_emp => $owner, target_type => 'SCHEDULE', target_id => $sid, created_at => $edit,
        );
    }
}
close $tf;
print STDERR "SCHEDULE_TIME_CHANGED: notif=".(scalar(@notif_rows)-$before_time)."\n";

# ============ 4) SCHEDULE_DELETED (가상 — 실제 schedules 행 불필요, target NULL) ============
my $before_deleted = scalar(@notif_rows);
my @deleted_titles = (
    '주간 정기 점검', '이슈 논의', '중간 점검', '실무 협의', '진행 상황 공유',
    '일정 재조율 논의', '착수 준비 미팅', '검토 회의', '현황 점검', '협업 논의',
    '방향성 논의', '리소스 조율 회의',
);
open(my $df, "<:encoding(UTF-8)", "/tmp/stage/deleted_project_owners.tsv") or die;
my $di = 0;
while (<$df>) {
    chomp;
    my ($pid, $owner) = split /\|/;
    next unless $owner;
    my @nonowner = grep { $_->[1] ne "1" } @{$proj_members{$pid}};
    next if scalar(@nonowner) < 1;
    my @recipients = @nonowner[0..(scalar(@nonowner)>=3 ? 2 : $#nonowner)]; # 최대 3명

    # 프로젝트 기간 안, 오늘 이전 날짜 — project_dates.tsv에서 start/target 확인 후 임의 결정적 오프셋
    open(my $pdf, "<:encoding(UTF-8)", "/tmp/stage/project_dates.tsv") or die;
    my ($pstart, $ptarget);
    while (<$pdf>) { chomp; my @p = split /\|/; if ($p[0] eq $pid) { $pstart=$p[2]; $ptarget=$p[3]; last; } }
    close $pdf;
    my ($y,$mo,$d) = $pstart =~ /^(\d{4})-(\d{2})-(\d{2})/;
    my $start_epoch = timegm(0,0,9,$d,$mo-1,$y);
    my $offset_days = 20 + ($pid % 15) * 3;
    my $del_epoch = $start_epoch + $offset_days*86400;
    my $today_end = timegm(0,0,0,11,7,2026) + 18*3600;
    $del_epoch = $today_end - 86400*2 if $del_epoch > $today_end; # 안전하게 이틀 여유
    my @t = gmtime($del_epoch);
    my $del_time = sprintf("%04d-%02d-%02d %02d:%02d:%02d", $t[5]+1900,$t[4]+1,$t[3],$t[2],$t[1],$t[0]);

    my $title = $deleted_titles[$di % scalar(@deleted_titles)];
    $di++;

    for my $r (@recipients) {
        my ($remp) = @$r;
        add_notif(
            user_emp => $remp, type => 'SCHEDULE_DELETED', title => "'$title' 일정이 삭제되었습니다",
            actor_emp => $owner, target_type => undef, target_id => undef, created_at => $del_time,
        );
    }
}
close $df;
print STDERR "SCHEDULE_DELETED: notif=".(scalar(@notif_rows)-$before_deleted)."\n";

print STDERR "총 신규 notification 행: ".scalar(@notif_rows)." (id ${\(10310)} ~ ${\($nid-1)})\n";

# ============ 출력 ============
open(my $o1, ">:encoding(UTF-8)", "/tmp/stage/out_post_updates.sql") or die;
print $o1 "$_\n" for @post_updates;
close $o1;

open(my $o2, ">:encoding(UTF-8)", "/tmp/stage/out_schedule_updates.sql") or die;
print $o2 "$_\n" for (@schedule_participant_updates, @schedule_modified_updates);
close $o2;

open(my $o3, ">:encoding(UTF-8)", "/tmp/stage/out_notif_inserts.sql") or die;
for my $i (0..$#notif_rows) {
    my $term = ($i == $#notif_rows) ? ";" : ",";
    print $o3 $notif_rows[$i] . $term . "\n";
}
close $o3;

print STDERR "완료. 다음 id 시작점: $nid\n";
