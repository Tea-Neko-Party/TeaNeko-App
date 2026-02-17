package org.zexnocs.teanekocore.api_response.api;

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
    default Map<String, String> headers() {
        return Map.of();
    }
}
