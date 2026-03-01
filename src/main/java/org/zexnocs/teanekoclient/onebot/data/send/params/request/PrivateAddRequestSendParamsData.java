package org.zexnocs.teanekoclient.onebot.data.send.params.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.Map;

/**
 * 添加/拒绝私人好友请求的参数数据
 *
 * @author zExNocs
 * @date 2026/03/01
 * @since 4.0.11
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@SuppressWarnings("rawtypes")
public class PrivateAddRequestSendParamsData implements ISendParamsData<Map> {
    public final static String ACTION = "set_friend_add_request";

    @Override
    public String getAction() {
        return ACTION;
    }

    @JsonProperty("flag")
    private String flag;

    @JsonProperty("approve")
    private boolean approve;

    @JsonProperty("reason")
    private String reason;

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