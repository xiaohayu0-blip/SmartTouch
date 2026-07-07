package com.smarttouch.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 拦截所有Controller抛出的异常，统一返回Result格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================== 参数校验异常 ====================

    /**
     * 处理 @Valid 校验失败异常
     * 提取第一个字段校验错误信息返回
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", msg);
        return Result.error(400, msg);
    }

    // ==================== 业务异常 ====================

    /** 处理自定义业务异常 */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.OK) // 业务异常返回200，通过code区分
    public Result<?> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // ==================== 兜底异常 ====================

    /** 处理未预期的运行时异常，记录完整堆栈便于排查 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> handleUnknown(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(500, "服务器内部错误，请联系管理员");
    }
}
