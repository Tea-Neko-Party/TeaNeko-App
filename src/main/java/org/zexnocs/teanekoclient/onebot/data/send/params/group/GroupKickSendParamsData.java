package org.zexnocs.teanekoclient.onebot.data.send.params.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.Map;

/**
 * 踢出群成员的发送参数数据。
 * <p>对应的响应类型为 Map。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
@SuppressWarnings("rawtypes")
public class GroupKickSendParamsData implements ISendParamsData<Map> {
    public final static String ACTION = "set_group_kick";

    @JsonProperty("group_id")
    private final long groupId;

    @JsonProperty("user_id")
    private final long userId;

    // 是否允许再次申请加入群。为 true 时，表示不允许再次申请加入群。
    @JsonProperty("reject_add_request")
    private final boolean rejectAddRequest;

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