package org.zexnocs.teanekoapp.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 当找不到合适的消息内容类时，使用该默认的消息内容类。该类会将 json 转化成 map
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@AllArgsConstructor
public class DefaultTeaNekoContent implements ITeaNekoContent {
    /// 转化后的数据 map
    private final ConcurrentHashMap<String, Object> dataMap;

    /**
     * 默认的消息内容类，不转化成指令。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    public @NonNull String[] toCommandArgs() {
        return new String[0];
    }

    /**
     * 获取类型。
     * 该方法应当加上 {@link JsonIgnore} 注解防止被序列化。
     *
     * @return {@link String} 类型字符串
     */
    @Override
    @JsonIgnore
    public @NonNull String getType() {
        return "";
    }
}
