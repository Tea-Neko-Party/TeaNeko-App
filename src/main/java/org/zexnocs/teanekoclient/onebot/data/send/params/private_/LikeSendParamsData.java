package org.zexnocs.teanekoclient.onebot.data.send.params.private_;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.Map;

/**
 * 点赞的发送参数数据。
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@Getter
@Builder
@SuppressWarnings("rawtypes")
public class LikeSendParamsData implements ISendParamsData<Map> {
    public final static String ACTION = "send_like";

    @JsonProperty("user_id")
    private final long userId;

    @JsonProperty("times")
    private final int times;

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