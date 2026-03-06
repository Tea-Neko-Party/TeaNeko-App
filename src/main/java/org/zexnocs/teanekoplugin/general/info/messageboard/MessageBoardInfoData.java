package org.zexnocs.teanekoplugin.general.info.messageboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 留言信息数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MessageBoardInfoData {
    @JsonProperty("message")
    private String message;

    @JsonProperty("sender_id")
    private UUID senderId;

    @JsonProperty("time")
    private long time;
}
