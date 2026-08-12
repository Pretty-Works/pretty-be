package HK.PrettyWorks_BE.notification.constant;

// 알림 클릭 시 이동할 대상의 종류. 서버는 종류와 식별자만 주고 URL 조립은 화면이 한다.
//
// 실제로 무엇을 싣는지는 NotificationTarget 팩토리를 보면 된다. 여기서는 종류의 의미만 적는다.
//
// PROJECT  — 프로젝트 상세로 간다. target_id = projectId.
//            마일스톤·지출·할 일처럼 단독 화면이 없고 탭으로만 존재하는 것들이 여기 붙는다.
// POST     — 게시글 상세로 간다. target_id = postId, target_project_id = projectId.
//            경로가 /projects/{projectId}/posts/{postId} 라 두 값이 다 필요하다.
// MEETING  — 회의록 상세. POST와 같은 이유로 두 값을 쓴다.
// SCHEDULE — 일정 상세(모달). target_id = scheduleId. 일정은 프로젝트에 속하지 않는다.
//
// target_type이 NULL인 알림도 있다. 두 가지 경우다.
//  1) 이동할 곳이 아예 없음 — PROJECT_MEMBER_REMOVED(제외돼서 프로젝트 상세 접근 불가, MEMBER_001).
//  2) 특정 리소스가 아니라 날짜로 보냄 — SCHEDULE_PARTICIPANT_REMOVED / SCHEDULE_DELETED.
//     내가 빠졌거나 사라진 일정은 열 수 없지만 "그 시간이 어떻게 됐는지"는 궁금하므로
//     target_date에 날짜를 담아 그 달의 캘린더로 보낸다(type은 NULL로 둔다 — 여는 리소스가 없다).
public enum NotificationTargetType {
    PROJECT,
    POST,
    MEETING,
    SCHEDULE
}
