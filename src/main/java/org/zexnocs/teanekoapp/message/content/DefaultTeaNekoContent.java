package org.zexnocs.teanekoapp.message.content;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
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
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class DefaultTeaNekoContent implements ITeaNekoContent {
    /// 转化后的数据 map
    private ConcurrentHashMap<String, Object> dataMap;

    /**
     * 默认的消息内容类，不转化成指令。
     *
     * @return {@link String[] } 转化后的字符串数组
     */
    @Override
    public @NonNull String[] toCommandArgs() {
        return new String[0];
    }

}
