package org.zexnocs.teanekoclient.onebot.data.receive.message;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.message.ContentPartScanner;
import org.zexnocs.teanekoapp.message.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentPart;
import org.zexnocs.teanekoapp.message.content.DefaultTeaNekoContentPart;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于解析 {@link OnebotContent} 的反序列化器。
 * <p>需要使用 spring 自带的 {@link ObjectMapper} 来反序列化 {@link OnebotContent} 对象
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Component
public class OnebotMessageDeserializer extends ValueDeserializer<OnebotContent> {

    private final ContentPartScanner scanner;

    @Autowired
    public OnebotMessageDeserializer(ContentPartScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * 反序列化 JSON 内容为 {@link OnebotContent} 对象。
     *
     * @param p    Parser used for reading JSON content
     * @param cTxt Context that can be used to access information about
     *             this deserialization activity.
     * @return Deserialized value
     */
    @Override
    public OnebotContent deserialize(JsonParser p, DeserializationContext cTxt) throws JacksonException {
        var root = cTxt.readTree(p);
        // 提取 type 字段和 data node
        String type = root.has("type") ? root.get("type").asString() : null;
        var dataNode = root.get("data");
        // 提取信息数据
        ITeaNekoContentPart content;
        // 先尝试从 onebot 中提取到 contentType
        Class<? extends ITeaNekoContentPart> clazz = scanner.getContentClass(OnebotContent.PREFIX + type);
        // 如果为空，则尝试从 tea neko 中获取到默认 contentType
        if(clazz == null) {
            clazz = scanner.getContentClass(TeaNekoContent.PREFIX + type);
        }

        if (clazz != null) {
            // 如果找到了对应的 contentType，则直接反序列化为该类型
            content = cTxt.readTreeAsValue(dataNode, clazz);
        } else {
            // 否则使用 DefaultTeaNekoContent
            var mapType = cTxt.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
            Map<String, Object> rawData = cTxt.readTreeAsValue(dataNode, mapType);

            if (rawData == null || rawData.isEmpty()) {
                content = new DefaultTeaNekoContentPart(new ConcurrentHashMap<>());
            } else {
                rawData.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
                content = new DefaultTeaNekoContentPart(new ConcurrentHashMap<>(rawData));
            }
        }

        return OnebotContent.builder()
                .type(type)
                .contentPart(content)
                .build();
    }
}