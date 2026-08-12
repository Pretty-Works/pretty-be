package HK.PrettyWorks_BE.notification.constant;

// 알림 종류. 화면은 이 코드로 아이콘·색을 고르고, 문구는 titleFormat으로 발생 시점에 완성해 저장한다.
//
// ── 표기 규칙 (화면 파싱과 짝을 이룬다) ──────────────────────────────────────
// 화면은 /^'(.+?)'\s*(.*)$/ 로 문구를 가른다. 맨 앞 작은따옴표 안의 값이 굵은 배지(윗줄)가 되고
// 나머지 문장이 본문(아랫줄)이 된다. 그래서 표기 역할을 이렇게 나눈다.
//
//   '...'  배지로 떼어낼 이름. 문구 맨 앞에만 쓴다.
//   [...]  문장 안에 남는 항목 이름.
//
// 프로젝트 하위 알림은 배지가 프로젝트명이라 앞자리가 '%s'다. 반면 일정 알림은 화면이 배지를
// "일정"으로 고정하므로 따옴표로 시작하지 않는다 — 여기서 따옴표를 붙이면 일정 제목이 배지로
// 잘려나가 본문에서 사라진다.
//
// ── 조사 마커 {이가} ────────────────────────────────────────────────────────
// 앞 글자 받침에 따라 "이/가"가 갈린다. 이걸 인자로 받지 않고 마커로 두는 이유는 두 가지다.
//  1) 호출부에 위치 인자를 하나 더 얹지 않는다. 이 알림들은 이미 nullable 인자가 많아
//     순서를 헷갈리기 쉬운데, 조사까지 인자로 만들면 그 위험을 키운다.
//  2) 인자는 조립 전에 70자로 잘린다(NotificationEvent.cutArgs). 조사를 미리 계산해 넘기면
//     원본 마지막 글자로 판정하므로, 잘린 이름("긴이름…")에 붙는 조사가 어긋난다.
//     마커는 조립 후에 풀리므로 실제로 화면에 보이는 글자를 기준으로 판정한다.
public enum NotificationType {

    // ── 프로젝트 자체에 일어난 일 ──
    // "프로젝트에/에서/가"를 유지한다. 빼면 "제외되었습니다"만 남아 무엇에서 빠졌는지 모르는 문구가 된다.
    PROJECT_MEMBER_ADDED("'%s' 프로젝트에 참여자로 추가되었습니다"),

    // target이 none이다 — 제외된 사람은 그 프로젝트 상세로 못 들어간다(참여중 멤버만, MEMBER_001).
    PROJECT_MEMBER_REMOVED("'%s' 프로젝트에서 제외되었습니다"),
    PROJECT_STATUS_CHANGED("'%s' 프로젝트가 %s 상태로 변경되었습니다"),
    PROJECT_PERIOD_CHANGED("'%s' 프로젝트 기간이 %s ~ %s 로 변경되었습니다"),

    // ── 프로젝트 '안'의 항목 ──
    // 배지는 프로젝트명, 항목 이름은 대괄호. "프로젝트에"를 반복하지 않는다 — 배지가 이미 프로젝트다.
    MILESTONE_COMPLETED("'%s' 마일스톤 [%s]{이가} 완료되었습니다"),
    EXPENSE_CREATED("'%s' %s원 지출이 등록되었습니다"),

    // 담당자와 작성자가 다를 때만 발행된다. 본인 할 일은 NotificationPublisher가 행위자를 걸러낸다.
    TASK_ASSIGNED("'%s' 할일 [%s]{이가} 배정되었습니다"),
    TASK_DELETED("'%s' 할일 [%s]{이가} 삭제되었습니다"),
    TASK_DUE_DATE_CHANGED("'%s' 할일 [%s]의 마감일이 %s 로 변경되었습니다"),

    // HIGH 우선순위 게시글에만 발행된다(전체 발행하면 스팸이라 팀 결정으로 제한).
    // 문구에 "중요"를 넣지 않는 것도 팀 결정이다 — 우선순위는 게시판에서 보면 된다.
    POST_CREATED("'%s' 게시판에 [%s]{이가} 등록되었습니다"),
    POST_UPDATED("'%s' 게시판의 [%s]{이가} 수정되었습니다"),

    // 참석자 + 프로젝트 관리자(오너·PM)가 수신 후보. 행위자 본인은 NotificationPublisher가 걸러낸다.
    // 회의록 '수정' 알림(MEETING_UPDATED)은 폐기했다 — 오타 하나만 고쳐도 참석자 전원이 받아 시끄러웠다.
    // 상수까지 지웠으므로 다시 추가하지 말 것. type이 EnumType.STRING이라 DB에 없는 이름이 남으면
    // 그 행을 읽는 순간 No enum constant 예외로 해당 사용자의 알림 목록 조회가 통째로 500이 된다.
    MEETING_CREATED("'%s' 회의록에 [%s]{이가} 등록되었습니다"),

    // ── 일정 ──
    // 화면이 배지를 "일정"으로 고정하므로 따옴표로 시작하지 않는다. 일정은 프로젝트에 속하지 않는다.
    SCHEDULE_PARTICIPANT_ADDED("[%s]에 참가자로 추가되었습니다"),

    // 아래 둘은 그 일정을 열 수 없어(내가 빠졌거나 이미 사라짐) 날짜로 보낸다 — NotificationTarget.date.
    SCHEDULE_PARTICIPANT_REMOVED("[%s]에서 제외되었습니다"),
    SCHEDULE_TIME_CHANGED("[%s]의 시간이 %s ~ %s 로 변경되었습니다"),
    SCHEDULE_DELETED("[%s]{이가} 삭제되었습니다");

    // 조사 마커. String.format은 '{' '}'를 지정자로 보지 않아 그대로 통과시키므로,
    // 포맷을 끝낸 뒤 이 자리를 실제 글자로 바꾼다.
    private static final String JOSA_MARKER = "{이가}";

    private static final char HANGUL_FIRST = 0xAC00;
    private static final char HANGUL_LAST = 0xD7A3;
    // 한글 음절 한 글자는 초성19 × 중성21 × 종성28 로 배열된다. 종성 인덱스가 0이면 받침이 없다.
    private static final int JONGSUNG_COUNT = 28;

    private final String titleFormat;

    NotificationType(String titleFormat) {
        this.titleFormat = titleFormat;
    }

    public String title(Object... args) {
        return resolveJosa(String.format(titleFormat, args));
    }

    // {이가}를 앞 이름의 받침에 맞는 글자로 바꾼다.
    //
    // 판정 대상은 마커 '바로 앞 글자'가 아니라 '대괄호 안의 마지막 글자'다.
    // 문자열상 마커 앞에는 항상 ']'가 있어서, 그걸 보면 언제나 틀린다.
    //
    //   할일 [검색 API 개선]{이가} 배정되었습니다
    //                    ↑ 판정 대상은 '선'(받침 ㄴ) → "이"
    private static String resolveJosa(String title) {
        int marker = title.indexOf(JOSA_MARKER);
        if (marker < 0) {
            return title;
        }

        return title.substring(0, marker)
                + josaFor(lastNameChar(title, marker))
                + title.substring(marker + JOSA_MARKER.length());
    }

    // 마커 앞의 닫는 괄호·따옴표를 건너뛰고 이름의 마지막 글자를 찾는다.
    private static char lastNameChar(String title, int marker) {
        int i = marker - 1;
        while (i >= 0 && (title.charAt(i) == ']' || title.charAt(i) == '\'')) {
            i--;
        }

        return i < 0 ? ' ' : title.charAt(i);
    }

    // 한글이 아니면 "가"로 둔다. 영문 약어는 대체로 모음 발음으로 끝나기 때문이다
    // (API→아이, QA→에이, UI→아이, v2→이). AS·CS처럼 "에스"로 끝나면 어긋나지만 소수라
    // 이쪽이 덜 틀린다.
    private static String josaFor(char last) {
        if (last < HANGUL_FIRST || last > HANGUL_LAST) {
            return "가";
        }

        return (last - HANGUL_FIRST) % JONGSUNG_COUNT != 0 ? "이" : "가";
    }
}
