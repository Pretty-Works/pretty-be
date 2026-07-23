package HK.PrettyWorks_BE.global.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    /**
     * Custom Exception 전용 ExceptionHandler (@RequestBody)
     */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<CustomErrorResponse> applicationException(BaseException e) {
        ErrorCode code = e.getCode();
        logging(code);

        return ResponseEntity
                .status(code.getStatus())
                .body(CustomErrorResponse.from(code));
    }

    /**
     * 요청 데이터 Validation 전용 ExceptionHandler (@RequestBody)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomErrorResponse> methodArgumentNotValidException(MethodArgumentNotValidException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        return convert(GlobalErrorCode.VALIDATION_ERROR, extractErrorMessage(fieldErrors));
    }

    /**
     * 요청 데이터 Validation 전용 ExceptionHandler (@ModelAttribute)
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<CustomErrorResponse> bindException(BindException e) {
        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        return convert(GlobalErrorCode.VALIDATION_ERROR, extractErrorMessage(fieldErrors));
    }

    private String extractErrorMessage(List<FieldError> fieldErrors) {
        if (fieldErrors.size() == 1) {
            return fieldErrors.get(0).getDefaultMessage();
        }

        StringBuffer buffer = new StringBuffer();
        for (FieldError error : fieldErrors) {
            buffer.append(error.getDefaultMessage()).append("\n");
        }
        return buffer.toString();
    }

    /**
     * 존재하지 않는 Endpoint 전용 ExceptionHandler
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<CustomErrorResponse> noHandlerFoundException() {
        return convert(GlobalErrorCode.NOT_SUPPORTED_URI_ERROR);
    }

    /**
     * PathVariable / RequestParam 타입 불일치 전용 ExceptionHandler (enum, Long 등 모든 타입 변환 실패)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CustomErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("[파라미터 타입 불일치] name={}, value={}", e.getName(), e.getValue());
        return convert(GlobalErrorCode.VALIDATION_ERROR,
                String.format("'%s' 파라미터 값이 잘못되었습니다: %s", e.getName(), e.getValue()));
    }

    /**
     * HTTP Request Method 오류 전용 ExceptionHandler
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CustomErrorResponse> httpRequestMethodNotSupportedException() {
        return convert(GlobalErrorCode.NOT_SUPPORTED_METHOD_ERROR);
    }

    /**
     * MediaType 전용 ExceptionHandler
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CustomErrorResponse> httpMediaTypeNotSupportedException() {
        return convert(GlobalErrorCode.NOT_SUPPORTED_MEDIA_TYPE_ERROR);
    }

    /**
     * 요청 JSON 형식이 잘못된 경우
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        return convert(GlobalErrorCode.BAD_JSON);
    }

    /**
     * 데이터베이스 제약 조건에 위배된 경우 (@Column)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<CustomErrorResponse> handleConstraintViolation(DataIntegrityViolationException e) {
        log.error("[DB 제약조건 위반] {}", e.getMessage(), e);
        return convert(GlobalErrorCode.CONSTRAINT_VIOLATION);
    }

    /**
     * 필수 헤더가 누락된 경우
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CustomErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e) {
        log.error("[필수 헤더 누락] {}", e.getMessage());
        return convert(GlobalErrorCode.MISSING_REQUEST_HEADER);
    }

    /**
     * 필수 multipart 파트가 누락된 경우 (예: data 또는 signature 미전송)
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CustomErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        log.warn("[필수 multipart 파트 누락] {}", e.getRequestPartName());
        return convert(GlobalErrorCode.MISSING_REQUEST_PART,
                String.format("'%s' 파트가 필요합니다.", e.getRequestPartName()));
    }

    /**
     * 업로드 파일이 허용 크기를 초과한 경우
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CustomErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("[업로드 용량 초과] {}", e.getMessage());
        return convert(GlobalErrorCode.PAYLOAD_TOO_LARGE);
    }

    /**
     * 내부 서버 오류 전용 ExceptionHandler
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<CustomErrorResponse> handleAnyException(RuntimeException e, HttpServletRequest request) {
        log.error("[내부 서버 오류] {} {}", request.getMethod(), request.getRequestURI(), e);
        return convert(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 쿼리 파라미터/PathVariable 등 메서드 레벨 유효성 검사 실패 전용 ExceptionHandler (@Validated)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getMessage)
                .orElse(GlobalErrorCode.VALIDATION_ERROR.getMessage());

        return convert(GlobalErrorCode.VALIDATION_ERROR, errorMessage);
    }

    /**
     * JWT 만료
     */
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<CustomErrorResponse> handleExpiredJwt(ExpiredJwtException e) {
        return convert(GlobalErrorCode.EXPIRED_TOKEN_ERROR);
    }

    /**
     * JWT 오류
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<CustomErrorResponse> handleJwtException(JwtException e) {
        return convert(GlobalErrorCode.INVALID_JWT);
    }

    private ResponseEntity<CustomErrorResponse> convert(ErrorCode code) {
        return ResponseEntity
                .status(code.getStatus())
                .body(CustomErrorResponse.from(code));
    }

    private ResponseEntity<CustomErrorResponse> convert(ErrorCode code, String message) {
        return ResponseEntity
                .status(code.getStatus())
                .body(CustomErrorResponse.of(code, message));
    }

    private void logging(ErrorCode code) {
        log.warn("{} | {} | {}", code.getStatus(), code.getErrorCode(), code.getMessage());
    }
}

