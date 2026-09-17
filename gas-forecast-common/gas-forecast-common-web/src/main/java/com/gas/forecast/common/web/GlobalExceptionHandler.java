package com.gas.forecast.common.web;

import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.common.core.BusinessResponseCode;
import com.gas.forecast.common.core.ResponseResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

@RestControllerAdvice(basePackages = "com.gas.forecast")
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseResult<Void>> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        log.warn("Business exception, uri={}, code={}, message={}", request.getRequestURI(), exception.getCode(), exception.getMessage());
        return ResponseEntity.ok(ResponseResult.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseResult<Void>> handleValidationException(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? BusinessResponseCode.PARAM_ERROR.getMessage() : error.getDefaultMessage())
                .orElse(BusinessResponseCode.PARAM_ERROR.getMessage());
        log.warn("Validation failed, uri={}, message={}", request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(ResponseResult.error(BusinessResponseCode.PARAM_ERROR, message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ResponseResult<Void>> handleBindException(BindException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? BusinessResponseCode.PARAM_ERROR.getMessage() : error.getDefaultMessage())
                .orElse(BusinessResponseCode.PARAM_ERROR.getMessage());
        log.warn("Bind failed, uri={}, message={}", request.getRequestURI(), message);
        return ResponseEntity.badRequest().body(ResponseResult.error(BusinessResponseCode.PARAM_ERROR, message));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ResponseResult<Void>> handleBadRequestException(Exception exception, HttpServletRequest request) {
        log.warn("Bad request, uri={}, message={}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.badRequest().body(ResponseResult.error(BusinessResponseCode.PARAM_ERROR));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ResponseResult<Void>> handleNotFoundException(NoSuchElementException exception, HttpServletRequest request) {
        log.warn("Resource not found, uri={}, message={}", request.getRequestURI(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ResponseResult.error(BusinessResponseCode.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseResult<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException exception,
                                                                                 HttpServletRequest request) {
        log.warn("Method not allowed, uri={}, method={}, supported={}", request.getRequestURI(), exception.getMethod(), exception.getSupportedHttpMethods());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ResponseResult.error(BusinessResponseCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handleException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception, uri={}", request.getRequestURI(), exception);
        return ResponseEntity.internalServerError().body(ResponseResult.error(BusinessResponseCode.SYSTEM_ERROR));
    }
}
