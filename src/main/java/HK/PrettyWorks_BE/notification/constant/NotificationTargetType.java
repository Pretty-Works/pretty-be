package HK.PrettyWorks_BE.notification.constant;

// 알림 클릭 시 이동할 대상. 서버는 종류와 ID만 주고 URL 조립은 화면이 한다.
//
// 프로젝트 하위 알림(마일스톤·지출·할 일·게시글·회의록)은 전부 target을 PROJECT로 두고 id에 projectId를 담는다.
// 이유는 저장 구조다 — target_type + target_id가 '한 쌍'이라 projectId와 postId를 같이 실을 수 없는데,
// 화면 경로가 /projects/{projectId}/... 라 projectId 없이는 상세로도 못 간다. 그래서 프로젝트를 담는다.
// 마일스톤·지출처럼 단독 화면이 없는 것들은 어차피 탭이라 이걸로 충분하고,
// 게시글·회의록은 목록까지만 이동한다(어느 글인지는 문구에 제목이 실려 있다).
// 상세까지 보내려면 두 id를 담을 수 있게 스키마를 바꿔야 한다 — 지금은 그만한 값어치가 없다는 판단.
//
// 일정은 프로젝트에 속하지 않아(projectId가 없다) PROJECT로 보낼 수 없다.
// id에 scheduleId를 담고, 화면은 그 일정이 있는 캘린더로 이동한다.
//
// target을 null로 두는 알림도 있다 — 이동시킬 곳이 없거나, 있어도 수신자가 못 들어가는 경우다.
// SCHEDULE_DELETED(일정이 사라짐), PROJECT_MEMBER_REMOVED(제외돼서 프로젝트 상세 접근 불가).
// 여기에 억지로 대상을 실으면 화면이 열리지 않는 곳으로 안내하게 된다.
public enum NotificationTargetType {
    PROJECT,
    SCHEDULE
}
