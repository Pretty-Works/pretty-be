package HK.PrettyWorks_BE.user.controller;

import HK.PrettyWorks_BE.user.dto.res.MyProfileResponse;
import HK.PrettyWorks_BE.user.dto.res.UserSearchResponse;
import HK.PrettyWorks_BE.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Swagger 문서는 UserApi 인터페이스에 있습니다.
@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

    private final UserService userService;

    // 상단바 표시와 화면 요소 노출 판단용. 대상은 항상 토큰의 본인입니다.
    @Override
    @GetMapping("/api/v1/users/me")
    public ResponseEntity<MyProfileResponse> getMyProfile(
            @AuthenticationPrincipal Long userId
    ) {
        MyProfileResponse response = userService.getMyProfile(userId);

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/api/v1/users/search")
    public ResponseEntity<List<UserSearchResponse>> searchUsers(
            @AuthenticationPrincipal Long requesterId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        List<UserSearchResponse> response =
                userService.searchUsers(requesterId, keyword, limit);

        return ResponseEntity.ok(response);
    }
}
