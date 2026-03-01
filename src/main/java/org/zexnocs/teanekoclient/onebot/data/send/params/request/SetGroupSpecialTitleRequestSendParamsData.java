package org.zexnocs.teanekoclient.onebot.data.send.params.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.Map;

/**
 * 设置群头衔请求参数数据。
 * <p>对应的响应类型为 Map。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@SuppressWarnings("rawtypes")
public class SetGroupSpecialTitleRequestSendParamsData implements ISendParamsData<Map> {
    public final static String ACTION = "set_group_special_title";

    @Override
    public String getAction() {
        return ACTION;
    }

    @JsonProperty("group_id")
    private long groupId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("special_title")
    private String specialTitle;

    @JsonProperty("duration")
    private long duration;

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