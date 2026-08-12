# seed_notifications.sql의 POST_UPDATED 72건 전부가 깨진 게시글 제목을 인용하고 있던 버그를 고친다.
#
# 근본 원인: gen_notifications_extra_148.pl이 참조한 스테이징 파일(/tmp/stage/post_updated_picks.tsv)을
# 만드는 과정에서 중간 단계 스크립트가 파일을 `open(my $fh, "<", $file)`로(= :encoding(UTF-8) 레이어
# 없이 raw byte로) 읽은 뒤 `binmode(STDOUT, ":utf8")`가 걸린 STDOUT으로 그대로 흘려보냈다. UTF-8
# 바이트를 코드포인트로 착각해 다시 UTF-8로 인코딩한 전형적인 "이중 인코딩" 깨짐이라, target_id
# (project_id)·actor_id 등 다른 필드는 멀쩡하고 제목 텍스트만 깨졌다.
#
# 고치는 법: 깨지지 않은 target_id(project_id)+actor_id 조합으로 seed_posts.sql(원본, 안 깨짐)에서
# 진짜 게시글 제목을 다시 조회해 그 부분만 교체한다. 프로젝트 3에는 같은 작성자의 HIGH 게시글이
# 2개 있어 첫 선정 당시 골랐던 쪽(게시글 id=17)으로 고정한다.
#
# 실행: perl src/main/resources/db/fix_post_updated_encoding.pl   (저장소 루트에서)
use strict; use warnings; use utf8;
binmode(STDOUT, ":utf8"); binmode(STDERR, ":utf8");

my $DB = 'src/main/resources/db';

# project_id => 올바른 게시글 제목 (seed_posts.sql에서 project_id+actor_id로 재조회해 확정)
my %CORRECT_TITLE = (
    2 => '환율 API 지연 이슈 — 원인 조사 경과',
    3 => '미문서화 배치 잡 35개 목록 및 초기 파악 현황',   # post id=17 (post id=19는 같은 작성자의 다른 HIGH 글)
    4 => '데이터 이관 실사 — 미문서화 구간 상세',
    6 => '규격 해석 이견 — 금융위 가이드라인 재확인 결과',
    7 => '서빙 아키텍처 최적화 상세 — 피처 캐싱 적용 방식',
    9 => '시공 업체 최종 확정 공지',
);

sub esc { my ($s) = @_; $s =~ s/'/''/g; return $s; }

open(my $in, '<:raw:encoding(UTF-8)', "$DB/seed_notifications.sql") or die;
my @lines = <$in>; close $in;
s/\r?\n\z// for @lines;

my $fixed = 0;
for my $l (@lines) {
    next unless $l =~ /^\(\d+, /;
    next unless $l =~ /'POST_UPDATED'/;
    my ($pid) = $l =~ /'PROJECT', (\d+),/;
    next unless defined $pid && exists $CORRECT_TITLE{$pid};
    my $correct = $CORRECT_TITLE{$pid};
    my $logical = "'$correct' 게시글이 수정되었습니다";
    my $new_field = "'" . esc($logical) . "'";
    # 기존 title 필드(콤마로 구분된 세 번째 필드, 'POST_UPDATED' 바로 뒤)를 통째로 교체
    my $replaced = ($l =~ s/(?<='POST_UPDATED', )'(?:[^']|'')*'(?=, \(SELECT)/$new_field/);
    die "치환 실패: $l" unless $replaced;
    $fixed++;
}
die "예상 건수(72)와 다름: $fixed" unless $fixed == 72;

open(my $out, '>:raw:encoding(UTF-8)', "$DB/seed_notifications.sql") or die;
print $out join("\r\n", @lines), "\r\n";
close $out;

print STDERR "POST_UPDATED 제목 교정: ${fixed}건\n";
