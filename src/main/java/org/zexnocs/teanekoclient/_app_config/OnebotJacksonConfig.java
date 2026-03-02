package org.zexnocs.teanekoclient._app_config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageDeserializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * 符合 onebot 的 Object Mapper 配置类。
 *
 * @author zExNocs
 * @date 2026/03/03
 * @since 4.0.11
 */
@Configuration
public class OnebotJacksonConfig {
    /**
     * 符合 onebot 的 Object Mapper 配置。
     *
     * @return {@link ObjectMapper }
     */
    @Bean("onebotObjectMapper")
    public ObjectMapper onebotObjectMapper(OnebotMessageDeserializer deserializer) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(OnebotMessage.class, deserializer);
        return JsonMapper.builder()
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .addModule(module)
                .findAndAddModules()
                .build();
    }
}
