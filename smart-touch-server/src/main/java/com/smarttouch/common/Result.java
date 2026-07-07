package com.smarttouch.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体
 * 所有Controller返回此对象，前端统一解析
 *
 * @param <T> data字段的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

    /** 状态码：200成功，其他为业务错误码 */
    private int code;

    /** 响应消息 */
    private String msg;

    /** 响应数据 */
    private T data;

    // ==================== 静态工厂方法 ====================

    /** 成功（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(200, "ok", null);
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "ok", data);
    }

    /** 成功（自定义消息+数据） */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(200, msg, data);
    }

    /** 失败（默认错误码500） */
    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    /** 失败（自定义错误码+消息） */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /** 失败（自定义错误码+消息+数据） */
    public static <T> Result<T> error(int code, String msg, T data) {
        return new Result<>(code, msg, data);
    }
}
