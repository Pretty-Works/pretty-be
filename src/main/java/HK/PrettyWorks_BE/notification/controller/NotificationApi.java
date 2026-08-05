package HK.PrettyWorks_BE.notification.controller;

import HK.PrettyWorks_BE.global.base.CursorResponse;
import HK.PrettyWorks_BE.notification.dto.res.NotificationResponse;
import HK.PrettyWorks_BE.notification.dto.res.UnseenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

// 알림 API의 Swagger 문서 전용 인터페이스. 컨트롤러(NotificationController)가 implements 한다.
@Tag(name = "알림", description = "상단바 종 아이콘의 인앱 알림 목록·뱃지·읽음 처리 API.")
public interface NotificationApi {

    @Operation(
            summary = "알림 목록 조회",
            description = """
                    종 아이콘 드롭다운에 표시할 알림을 최신순으로 조회합니다. 본인에게 온 알림만 조회됩니다.

                    [커서 페이지네이션] page 번호가 아니라 cursor를 씁니다. 알림은 계속 위로 쌓이므로
                    offset 방식이면 스크롤 도중 새 알림이 도착할 때 경계가 밀려 같은 항목이 두 번 오거나 빠집니다.
                    첫 페이지는 cursor 없이 호출하고, 다음 페이지는 응답의 nextCursor를 그대로 넘기세요.
                    hasNext가 false면 더 없습니다. 전체 개수는 내려주지 않습니다.

                    [읽음 표시] 읽은 알림도 목록에 계속 남습니다. read가 false인 항목만 강조하세요.
                    드롭다운을 여는 것(`PATCH /seen`)으로는 read가 바뀌지 않습니다 — 뱃지만 꺼지고
                    강조는 유지되므로, 사용자는 무엇을 아직 처리하지 않았는지 계속 볼 수 있습니다.

                    [target] 클릭 시 이동할 대상입니다. type과 id만 주며 URL 조립은 화면이 합니다
                    (예: PROJECT + 2 → /projects/2). 대상이 없는 알림은 null입니다.

                    [actor] 알림을 발생시킨 사람입니다. 마감 임박처럼 시간이 원인인 알림은 null입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공 (알림이 없으면 items가 빈 배열)"),
            @ApiResponse(responseCode = "400", description = "size 범위 위반 (1~100)")
    })
    ResponseEntity<CursorResponse<NotificationResponse>> getNotifications(
            Long userId,
            @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 생략합니다.", example = "1043") Long cursor,
            @Parameter(description = "가져올 알림 수 (1~100)", example = "20") int size
    );

    @Operation(
            summary = "안 본 알림 유무 조회 (뱃지)",
            description = """
                    종 아이콘에 **빨간 점**을 켤지 판단합니다. 30초 주기 폴링을 권장합니다.

                    마지막으로 알림함을 연 뒤에 새 알림이 왔는지만 봅니다. 개수는 내려주지 않습니다 —
                    화면이 점만 찍으므로 셀 이유가 없고, 세지 않는 편이 훨씬 가볍습니다.

                    한 번도 알림함을 연 적이 없으면 알림이 하나라도 있을 때 true입니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공")
    })
    ResponseEntity<UnseenResponse> getUnseen(Long userId);

    @Operation(
            summary = "알림함 확인 처리 (뱃지 끄기)",
            description = """
                    빨간 점을 끕니다. 사용자가 드롭다운을 **열 때** 호출하세요.

                    ⚠️ 읽음 처리가 아닙니다. 목록의 read 값은 이 API로 바뀌지 않으므로,
                    안을 클릭하지 않아도 뱃지는 꺼지고 **안 읽은 항목의 강조는 그대로 남습니다.**
                    개별 읽음은 항목을 클릭할 때 `PATCH /{notificationId}/read`로 따로 처리합니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공 (알림이 하나도 없어도 성공)")
    })
    ResponseEntity<Void> markSeen(Long userId);

    @Operation(
            summary = "알림 개별 읽음 처리",
            description = """
                    알림 항목을 **클릭할 때** 호출합니다. 본인의 알림만 처리할 수 있습니다.

                    이미 읽은 알림을 다시 호출해도 성공하며 최초 읽은 시각이 유지됩니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "처리 성공"),
            @ApiResponse(responseCode = "404", description = "없는 알림이거나 본인의 알림이 아님 (NOTIFICATION_001)")
    })
    ResponseEntity<Void> read(
            Long userId,
            @Parameter(description = "읽음 처리할 알림 ID", example = "1043") Long notificationId
    );
}
