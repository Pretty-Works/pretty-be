package HK.PrettyWorks_BE.global.util;

// 완료율·집행률처럼 "전체 중 몇 %"를 계산하는 공용 유틸.
//
// 분모가 0일 때 무엇을 반환할지는 도메인마다 뜻이 다르지만(마일스톤 없음 / 할 일 없음 / 예산 제한 없음)
// 화면에 0%를 보여준다는 결론은 같아, 규칙을 한 곳에 둔다.
public final class Percent {

    private Percent() {
    }

    // 백분율 내림. 분모가 0이면 나눌 수 없으므로 0을 반환한다.
    // 내림이라 항목별 비율의 합이 100에 못 미칠 수 있으며, 100%를 맞추는 보정은 화면이 담당한다.
    public static int floorRate(long part, long whole) {
        return whole == 0 ? 0 : (int) (part * 100 / whole);
    }
}
