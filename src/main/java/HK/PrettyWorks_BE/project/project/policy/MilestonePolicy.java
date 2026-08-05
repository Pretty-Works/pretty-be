package HK.PrettyWorks_BE.project.project.policy;

import HK.PrettyWorks_BE.project.project.domain.MilestoneEntity;

import java.util.List;

// 마일스톤 완료 순서 규칙. 순수 판정(목록과 위치를 받아 boolean 반환, DB·throw 없음).
//
// 마일스톤은 목표일 순서대로만 진행되어야 한다. 중간을 건너뛰고 체크하면 완료율이 실제 진척과
// 어긋나고, 타임라인에서 어디까지 왔는지도 읽을 수 없게 된다.
//
// 완료만 막으면 부족하다 — 1·2·3을 완료한 뒤 2만 취소하면 완료 구간에 구멍이 뚫린다.
// 두 방향을 모두 막아야 상태가 항상 "앞쪽 연속 완료 + 뒤쪽 연속 미완료"로 유지된다.
public final class MilestonePolicy {

    private MilestonePolicy() {
    }

    /**
     * 완료 가능 여부: 앞선 마일스톤이 모두 완료여야 한다(= 미완료 중 첫 번째).
     *
     * @param ordered 목표일 오름차순(동률이면 id)으로 정렬된 목록
     */
    public static boolean canComplete(List<MilestoneEntity> ordered, int index) {
        return ordered.subList(0, index).stream().allMatch(MilestoneEntity::isDone);
    }

    // 취소 가능 여부: 뒤따르는 마일스톤이 모두 미완료여야 한다(= 완료 중 마지막).
    public static boolean canUndo(List<MilestoneEntity> ordered, int index) {
        return ordered.subList(index + 1, ordered.size()).stream()
                .noneMatch(MilestoneEntity::isDone);
    }

    // 지금 상태를 뒤집을 수 있는지. 완료된 것은 취소 규칙을, 미완료인 것은 완료 규칙을 본다.
    //
    // 프로젝트 수정으로 목표일이 바뀌면 구멍이 생길 수 있는데(계획 변경까지 막지는 않는다),
    // 그때도 이 판정은 그대로 성립한다 — 구멍 앞뒤로 손댈 수 있는 지점이 남아 스스로 메워진다.
    public static boolean canToggle(List<MilestoneEntity> ordered, int index) {
        return ordered.get(index).isDone()
                ? canUndo(ordered, index)
                : canComplete(ordered, index);
    }
}
