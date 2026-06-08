package org.zexnocs.teanekocore.api_response.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * 请求数据接口。
 * 使用该接口必须要在类上使用 {@link APIRequestData} 注解。
 * 其变量可以使用 {@link APIRequestParam} 注解来标记名称和默认值。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public interface IAPIRequestData {
    /**
     * 获取运行时覆盖的 API base URL。
     * <br>默认返回 {@code null}，表示使用 {@link APIRequestData#baseUrl()} 中声明的静态 base URL。
     * <br>需要从文件配置或数据库读取 API 地址的请求可以覆盖该方法。
     *
     * @return 运行时 API base URL；为空时使用注解配置
     */
    @JsonIgnore
    default String getBaseUrlOverride() {
        return null;
    }

    /**
     * 获取运行时覆盖的 API path。
     * <br>默认返回 {@code null}，表示使用 {@link APIRequestData#path()} 中声明的静态 path。
     *
     * @return 运行时 API path；为空时使用注解配置
     */
    @JsonIgnore
    default String getPathOverride() {
        return null;
    }

    /**
     * 获取运行时覆盖的 HTTP method。
     * <br>默认返回 {@code null}，表示使用 {@link APIRequestData#method()} 中声明的静态 method。
     *
     * @return 运行时 HTTP method；为空时使用注解配置
     */
    @JsonIgnore
    default String getMethodOverride() {
        return null;
    }

    /**
     * 获取请求头。
     *
     * @return 请求头映射
     */
    default Map<String, String> headers() {
        return Map.of();
    }
}
