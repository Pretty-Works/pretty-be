package HK.PrettyWorks_BE.notification.constant;

// 알림 클릭 시 이동할 대상. 서버는 종류와 ID만 주고 URL 조립은 화면이 한다.
//
// 마일스톤·지출은 단독 화면이 없고 프로젝트 상세의 탭이라, id만으로는 어느 프로젝트인지 알 수 없다.
// 그래서 target은 PROJECT로 두고 id에 projectId를 담는다 — 화면이 프로젝트로 이동한 뒤
// 해당 탭을 여는 것이 이동 경로상 자연스럽다.
//
// 일정은 프로젝트에 속하지 않아(projectId가 없다) PROJECT로 보낼 수 없다.
// id에 scheduleId를 담고, 화면은 그 일정이 있는 캘린더로 이동한다.
public enum NotificationTargetType {
    PROJECT,
    SCHEDULE
}
