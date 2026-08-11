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

    // 참석자 명단 변경 권한: 작성자만 가능.
    // 참석자도 회의록 내용은 고칠 수 있지만(canEdit) 명단은 못 건드린다 —
    // 그래서 참석자가 수정할 때는 자기 자신을 명단에서 뺄 수 없다.
    public static boolean canManageAttendees(MeetingEntity meeting, Long userId) {
        return meeting.getAuthorId().equals(userId);
    }
}