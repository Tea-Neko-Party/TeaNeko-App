package org.zexnocs.teanekocore.api_response.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * api 请求数据的配置注解
 * 指定运行时所需的请求数据
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface APIRequestData {

    /**
     * 请求数据的基础 URL
     */
    String baseUrl();

    /**
     * 请求数据的路径
     * 请以斜杠开头，并且不要以斜杠结尾
     */
    String path() default "";

    /**
     * 请求数据的 HTTP 方法
     */
    String method() default "GET";

    /**
     * 如果是 POST 请求，指定请求体的内容类型
     * 如果为 true，则表示请求体为 JSON 格式
     * 如果为 false，则表示请求体为 application 格式
     */
    boolean isJson() default true;

    /**
     * 请求数据的超时时间，单位为毫秒
     * 默认值为 10000 毫秒
     */
    long timeoutInMillis() default 10000L;

    /**
     * 请求数据的重试次数
     * 默认值为 3 次
     */
    int retryCount() default 3;

    /**
     * 失败后多长时间后重试，单位为毫秒
     * 默认为 1000 毫秒
     */
    long retryDelayInMillis() default 1000L;

    /**
     * response 缓存储存的默认时间。
     * 默认半个小时。
     */
    long cacheDurationInMillis() default 30 * 60 * 1000L; // 30 分钟
}
