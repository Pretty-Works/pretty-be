package HK.PrettyWorks_BE.notification.event;

import HK.PrettyWorks_BE.notification.constant.NotificationTargetType;
import HK.PrettyWorks_BE.notification.constant.NotificationType;

import java.util.Arrays;
import java.util.List;

// 알림 발행 이벤트. 종류마다 클래스를 만들면 알림이 늘어날 때마다 클래스가 같이 불어나므로
// 한 종류로 두고 type으로 구분한다. 진짜 도메인 이벤트가 필요해지면 그때 나눈다.
//
// recipientIds에서 행위자·퇴사자·탈퇴 멤버를 빼는 건 발행하는 쪽 책임이다.
// 리스너가 일괄로 빼면 본인에게도 보내야 하는 알림(보안 알림 등)을 만들 수 없다.
public record NotificationEvent(
        NotificationType type,
        List<Long> recipientIds,
        String title,
        Long actorId,
        NotificationTargetType targetType,
        Long targetId
) {

    // notifications.title 칼럼 길이. 넘기면 저장이 깨지면서 업무 트랜잭션까지 함께 롤백된다.
    private static final int MAX_TITLE_LENGTH = 200;

    // 문구에 끼워 넣는 문자열 인자 하나의 상한.
    //
    // 70인 근거: 고정 문구가 가장 긴 TASK_DUE_DATE_CHANGED(29자)에 문자열 인자 2개와 날짜 1개가
    // 모두 최대로 들어와도 29 + 70 + 70 + 10 = 179자로 MAX_TITLE_LENGTH 안에 들어온다.
    // 인자가 늘어나는 알림을 새로 만들 때는 이 계산을 다시 해야 한다.
    private static final int MAX_ARG_LENGTH = 70;

    // 문구를 호출부가 직접 넘기지 않고 type에서 뽑는다. 둘을 따로 받으면 엉뚱한 문구가 붙은
    // 알림을 만들 수 있고, 그 상태로 저장되면 나중에 고칠 방법이 없다(문구는 발생 시점 기록이라).
    public static NotificationEvent of(NotificationType type,
                                       List<Long> recipientIds,
                                       Long actorId,
                                       NotificationTargetType targetType,
                                       Long targetId,
                                       Object... titleArgs) {
        return new NotificationEvent(
                type, recipientIds, fitTitle(type.title(cutArgs(titleArgs))), actorId, targetType, targetId);
    }

    /**
     * 문장을 조립하기 <b>전에</b> 긴 문자열 인자를 줄인다.
     *
     * <p>프로젝트명(100자)·게시글 제목(200자)·마일스톤 목표(200자)처럼 긴 값이 그대로 들어오면
     * 완성된 문장이 칼럼 상한을 넘는다. 이때 조립 후에 뒤를 자르면 문장 끝(서술어)과 닫는 따옴표가
     * 먼저 사라져, 무슨 알림인지 알 수 없게 되고 화면의 배지 파싱(맨 앞 따옴표 쌍)까지 실패한다.
     * 인자를 먼저 줄이면 문장 구조는 항상 온전하고, 잘리는 건 긴 이름의 뒷부분뿐이다.
     *
     * <p>날짜·금액 같은 짧은 값은 상한에 걸리지 않고, 문자열이 아닌 인자는 건드리지 않는다.
     */
    private static Object[] cutArgs(Object... titleArgs) {
        return Arrays.stream(titleArgs)
                .map(arg -> arg instanceof String text ? cut(text) : arg)
                .toArray();
    }

    private static String cut(String value) {
        if (value.length() <= MAX_ARG_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_ARG_LENGTH - 1) + "…";
    }

    // 인자를 미리 줄여서 정상적으로는 여기 걸리지 않는다. 인자가 더 많은 알림이 추가돼
    // 위 계산이 깨졌을 때 INSERT 실패로 업무 트랜잭션까지 롤백되는 것만 막는 최후 방어선이다.
    private static String fitTitle(String title) {
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }
}
