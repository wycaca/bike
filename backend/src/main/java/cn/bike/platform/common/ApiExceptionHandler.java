package cn.bike.platform.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(40400, exception.getMessage()));
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
}
