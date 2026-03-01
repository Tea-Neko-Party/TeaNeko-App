package org.zexnocs.teanekoclient.onebot.data.send.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.Map;

/**
 * 群禁言参数数据类。
 * <p>对应的响应类型为 Map。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
@SuppressWarnings("rawtypes")
public class GroupBanParamsData implements ISendParamsData<Map> {
    public final static String ACTION = "set_group_ban";

    /**
     * 群号。
     */
    @JsonProperty("group_id")
    private final long groupId;

    /**
     * 成员 QQ 号。
     */
    @JsonProperty("user_id")
    private final long userId;

    /**
     * 禁言时长，单位为秒。
     * 0 表示取消禁言。
     */
    @JsonProperty("duration")
    private final long duration;

    @Override
    public String getAction() {
        return ACTION;
    }

    /**
     * 获取反应数据的类型。
     *
     * @return 反应数据的类型。
     */
    @Override
    public Class<Map> getResponseDataType() {
        return Map.class;
    }
}