package HK.PrettyWorks_BE.task.policy;

import HK.PrettyWorks_BE.task.domain.TaskEntity;

// 할 일 관련 권한 규칙. 순수 판정(엔티티 받아 boolean 반환, DB·throw 없음).
public final class TaskPolicy {

    public static final int MAX_CREATE_BATCH_SIZE = 50;

    private TaskPolicy() {
    }

    // 수정: 담당자와 작성자 둘 다 가능.
    //
    // 한쪽만 열면 반대쪽이 막힌다 — 담당자만이면 배정한 PM이 자기가 만든 할 일의 오타도 못 고치고,
    // 작성자만이면 배정받은 사람이 마감일 조정조차 못 한다. 둘 다 이해관계자다.
    public static boolean canModify(TaskEntity task, Long userId) {
        return task.getAssigneeId().equals(userId) || task.getCreatorId().equals(userId);
    }

    // 완료 토글: 담당자만. 일을 한 사람이 체크해야 한다 —
    // 작성자가 남의 일을 완료 처리하면 완료율이 실제 진척과 어긋난다.
    public static boolean canToggle(TaskEntity task, Long userId) {
        return task.getAssigneeId().equals(userId);
    }

    // 삭제: 작성자만. 담당자에게 열어 주면 "하기 싫으면 삭제"가 되어 배정이 무의미해진다.
    public static boolean canDelete(TaskEntity task, Long userId) {
        return task.getCreatorId().equals(userId);
    }
}
