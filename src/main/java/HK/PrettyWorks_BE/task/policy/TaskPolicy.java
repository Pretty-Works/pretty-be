package HK.PrettyWorks_BE.task.policy;

import HK.PrettyWorks_BE.task.domain.TaskEntity;

// 할 일 관련 권한 규칙. 순수 판정(엔티티 받아 boolean 반환, DB·throw 없음).
public final class TaskPolicy {

    public static final int MAX_CREATE_BATCH_SIZE = 50;

    private TaskPolicy() {
    }

    // 수정·삭제·완료 토글 공통: 본인 할 일만 가능.
    // 현재는 담당자 = 작성자라 assigneeId로 판정한다(추후 타인 배정이 생기면 작성자 기준으로 분리).
    public static boolean canModify(TaskEntity task, Long userId) {
        return task.getAssigneeId().equals(userId);
    }
}
