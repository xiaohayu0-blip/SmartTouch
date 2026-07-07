package com.smarttouch.common;

import lombok.Getter;

/**
 * 业务异常
 * 用于业务逻辑中主动抛出的可预期异常，GlobalExceptionHandler统一拦截处理
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
