package org.zexnocs.teanekoplugin.general.info.messageboard;

import org.zexnocs.teanekocore.plugin.PluginOwned;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 留言信息数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@PluginOwned("general.info")
public class MessageBoardInfoData {
    @JsonProperty("message")
    private String message;

    @JsonProperty("sender_id")
    private UUID senderId;

    @JsonProperty("created_at")
    private Instant createdAt;

    /**
     * 兼容旧版本以毫秒时间戳保存的留言时间。
     *
     * @param time Unix 毫秒时间戳
     */
    @JsonProperty("time")
    private void setLegacyTime(long time) {
        if (createdAt == null) {
            createdAt = Instant.ofEpochMilli(time);
        }
    }
}
