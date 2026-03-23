package org.zexnocs.teanekocore._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 配置类，用于配置 Jackson 序列化和反序列化相关的设置。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Configuration
public class JacksonConfig {
    /**
     * 配置 ObjectMapper，使其支持单值数组、忽略未知属性等。
     *
     * @return {@link ObjectMapper }
     */
    @Bean("customObjectMapper")
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .findAndAddModules()
                .build();
    }
}
