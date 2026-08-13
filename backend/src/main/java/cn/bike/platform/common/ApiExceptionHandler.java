package cn.bike.platform.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(40400, exception.getMessage()));
    }

    /** 输入: 资源状态冲突异常; 输出: 统一的 HTTP 409 响应. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(40900, exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(40000, exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
        var firstError = exception.getBindingResult().getFieldErrors().stream().findFirst();
        var message = firstError
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.error(40001, message));
    }

    /** 输入: MVC 参数解析异常; 输出: 不暴露框架细节的 HTTP 400 响应. */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(40002, "请求格式或参数类型不正确"));
    }

    /** 输入: 权限校验异常; 输出: 与安全过滤器一致的 HTTP 403 响应. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(40300, "没有操作权限"));
    }

    /** 输入: 超过服务端限制的上传; 输出: 统一的 HTTP 413 响应. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(41300, "单张凭证照片不能超过 8 MB"));
    }

    /** 输入: 不支持的 HTTP 方法; 输出: 带 Allow 头的 HTTP 405 响应. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        var headers = new HttpHeaders();
        if (exception.getSupportedHttpMethods() != null) {
            headers.setAllow(exception.getSupportedHttpMethods());
        }
        return new ResponseEntity<>(ApiResponse.error(40500, "请求方法不支持"), headers,
                HttpStatus.METHOD_NOT_ALLOWED);
    }

    /** 输入: 不支持的请求媒体类型; 输出: HTTP 415 响应. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(41500, "请求内容类型不支持"));
    }

    /** 输入: 无法生成客户端要求的响应类型; 输出: HTTP 406 响应. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(HttpMediaTypeNotAcceptableException exception) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.error(40600, "无法生成客户端要求的响应类型"));
    }

    /** 输入: 未匹配控制器的资源路径; 输出: 统一的 HTTP 404 响应. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(40400, "接口不存在"));
    }

    /** 输入: 未预期异常; 输出: 记录完整日志并向客户端隐藏内部实现的 HTTP 500 响应. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        LOG.error("未处理的接口异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(50000, "服务暂时不可用"));
    }
}
