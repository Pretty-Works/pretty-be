package HK.PrettyWorks_BE.project.meeting.policy;

import HK.PrettyWorks_BE.project.meeting.domain.MeetingEntity;

public final class MeetingPolicy {

    private MeetingPolicy() {
    }

    // 수정 권한: 작성자이거나, 이 회의록 참석자 명단에 포함된 사람
    public static boolean canEdit(MeetingEntity meeting, Long userId, boolean isParticipant) {
        return meeting.getAuthorId().equals(userId) || isParticipant;
    }

    // 삭제 권한: 작성자만 가능.
    public static boolean canDelete(MeetingEntity meeting, Long userId) {
        return meeting.getAuthorId().equals(userId);
    }
}