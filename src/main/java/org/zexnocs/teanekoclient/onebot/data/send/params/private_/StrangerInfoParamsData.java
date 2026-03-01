package org.zexnocs.teanekoclient.onebot.data.send.params.private_;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.response.params._private.StrangerInfoGetResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

/**
 * 获取陌生人信息
 * <p>对应的响应类型为 StrangerInfoGetResponseData。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class StrangerInfoParamsData implements ISendParamsData<StrangerInfoGetResponseData> {
    public static final String ACTION = "get_stranger_info";

    @JsonProperty("user_id")
    long userId;

    @JsonProperty("no_cache")
    @Builder.Default
    boolean noCache = false;

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
    public Class<StrangerInfoGetResponseData> getResponseDataType() {
        return StrangerInfoGetResponseData.class;
    }
}