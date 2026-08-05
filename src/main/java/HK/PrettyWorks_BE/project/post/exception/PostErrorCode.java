package HK.PrettyWorks_BE.project.post.exception;

import HK.PrettyWorks_BE.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PostErrorCode implements ErrorCode {
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_001", "존재하지 않는 게시글입니다."),
    POST_NOT_IN_PROJECT(HttpStatus.NOT_FOUND, "POST_002", "해당 프로젝트의 게시글이 아닙니다."),
    PROJECT_CLOSED(HttpStatus.BAD_REQUEST, "POST_003", "완료 또는 보관된 프로젝트에는 게시글을 추가/수정할 수 없습니다."),
    NO_PERMISSION(HttpStatus.FORBIDDEN, "POST_004", "게시글에 대한 권한이 없습니다."),
    AUTHOR_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_005", "작성자를 찾을 수 없습니다.");


    private final HttpStatus status;
    private final String errorCode;
    private final String message;
}