package HK.PrettyWorks_BE.replan.constant;

// 재계획이 실제로 수행하는 변경 단위.
//
// 선언 순서가 곧 적용 순서다(ReplanApplyService가 이 순서로 실행한다). 순서가 규칙인 이유:
//   - 프로젝트 기간을 먼저 넓혀야 마일스톤·할 일 날짜가 기간 검증을 통과한다(PROJECT_015 / TASK_007)
//   - 참여자를 먼저 넣어야 그 사람에게 할 일을 배정할 수 있다(TASK_009)
// 에이전트가 만들어 준 배열 순서를 그대로 실행하면 논리적으로 옳은 계획이 검증에서 거부된다.
// 그래서 순서는 요청이 아니라 서버가 정한다.
//
// 담당자 교체 항목은 없다. 화면이 재배정을 지원하지 않으므로 재계획도 하지 않는다 —
// 넘길 일이 있으면 화면과 똑같이 TASK_DELETE + TASK_CREATE 로 표현한다.
// 한쪽 경로에만 있으면 같은 조작의 결과(taskId·완료 상태·알림)가 어디서 했느냐에 따라 달라진다.
public enum ReplanOperationType {

    PROJECT_TARGET_DATE_CHANGE,
    PROJECT_MEMBER_ADD,
    MILESTONE_TARGET_DATE_CHANGE,
    TASK_DUE_DATE_CHANGE,
    TASK_CREATE,
    TASK_DELETE
}
