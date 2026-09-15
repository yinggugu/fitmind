package com.fitmind.exception;

import com.fitmind.common.Result;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MultipartException;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> business(BusinessException exception) {
        return ResponseEntity.status(exception.getCode()).body(Result.error(exception.getCode(), exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<Void>> validation(Exception exception) {
        org.springframework.validation.BindingResult result = exception instanceof MethodArgumentNotValidException
            ? ((MethodArgumentNotValidException) exception).getBindingResult() : ((BindException) exception).getBindingResult();
        String message = result.getFieldErrors().stream().map(error -> error.getField() + ": " + error.getDefaultMessage()).collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Result.error(400, message));
    }
    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class,
        MissingServletRequestPartException.class, MultipartException.class, ServletRequestBindingException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Result<Void>> badRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Result.error(400, "请求参数不正确"));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unknown(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(500, "服务器内部错误"));
    }
}
