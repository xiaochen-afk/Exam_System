package com.examsystem.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** MVC 异常统一出口；对外不暴露内部堆栈，日志只保留类型和静态代码位置，不记录原始异常消息。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ApiErrors errors;

    public ApiExceptionHandler(ApiErrors errors) {
        this.errors = errors;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiErrors.Body> business(ApiException error, HttpServletRequest request) {
        LOG.warn(
                "event=request_rejected requestId={} status={} code={}",
                ApiErrors.requestId(request),
                error.status().value(),
                error.code());
        return ResponseEntity.status(error.status()).body(errors.body(error, request));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        MissingServletRequestPartException.class,
        MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class
    })
    ResponseEntity<ApiErrors.Body> invalid(Exception ignored, HttpServletRequest request) {
        return business(ApiException.invalid("请求字段、格式或取值不正确"), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiErrors.Body> uploadTooLarge(HttpServletRequest request) {
        return business(
                new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "ATTACHMENT_TOO_LARGE", "单个附件最多 10 MB"),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrors.Body> unexpected(Exception error, HttpServletRequest request) {
        // 只记录方法/行号用于定位，不传 Throwable 给日志框架，避免消息、原因链或 SQL 参数泄露。
        LOG.error(
                "event=request_failed requestId={} exceptionType={} location={}",
                ApiErrors.requestId(request),
                error.getClass().getName(),
                errorLocation(error));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        errors.body(
                                new ApiException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "INTERNAL_ERROR",
                                        "服务暂时无法处理请求"),
                                request));
    }

    private static String errorLocation(Exception error) {
        return Arrays.stream(error.getStackTrace())
                .limit(12)
                .map(
                        frame ->
                                frame.getClassName()
                                        + "#"
                                        + frame.getMethodName()
                                        + ":"
                                        + frame.getLineNumber())
                .collect(Collectors.joining(" <- "));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiErrors.Body> missing(
            NoResourceFoundException error, HttpServletRequest request) {
        return business(ApiException.notFound(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrors.Body> method(
            HttpRequestMethodNotSupportedException error, HttpServletRequest request) {
        return business(
                new ApiException(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "不支持此请求方法"),
                request);
    }
}
