package com.wfh.drawio.annotation;

import com.wfh.drawio.model.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Title: RateLimit
 * @Author wangfenghuan
 * @Package com.wfh.drawio.annotation
 * @Date 2026/1/25 20:52
 * @description:
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流key前缀
     * @return
     */
    String key() default "";

    /**
     * 每个时间窗口允许的请求数
     * @return
     */
    int rate() default 10;

    /**
     * 时间窗口（秒）
     * @return
     */
    int rateInterval() default 1;

    RateLimitType limitType() default RateLimitType.USER;

    /**
     * 限流提示信息
     * @return
     */
    String message() default "请求过于频繁请稍后再试";
}
