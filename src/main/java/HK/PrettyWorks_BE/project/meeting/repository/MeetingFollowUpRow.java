package HK.PrettyWorks_BE.project.meeting.repository;

// 회의록의 후속 조치만 뽑아 오는 프로젝션.
//
// 목록 조회(MeetingListResponse)는 본문·후속 조치를 내려주지 않는다 — 여러 건을 통째로 받으면
// 무겁기 때문이다. 그런데 프로젝트 AI 요약의 meeting 섹션은 "후속 액션이 정리됐는지"가 핵심 재료라
// 이 한 칼럼만 따로 읽어야 한다. 상세 조회를 회의 수만큼 부르면 N+1이 된다.
public record MeetingFollowUpRow(Long meetingId, String followUp) {
}
