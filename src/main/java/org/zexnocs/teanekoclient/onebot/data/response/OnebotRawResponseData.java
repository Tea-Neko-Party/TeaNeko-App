package org.zexnocs.teanekoclient.onebot.data.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.zexnocs.teanekoapp.response.ResponseData;

import java.util.Map;

/**
 * onebot response 数据类，用于构造 {@link org.zexnocs.teanekoapp.response.ResponseData}
 *
 * @see org.zexnocs.teanekoapp.response.ResponseData
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class OnebotRawResponseData extends ResponseData {
    /**
     * 状态。"ok" 或者 "error"。
     */
    @JsonProperty("status")
    private String status;

    /**
     * retcode 响应码
     */
    @JsonProperty("retcode")
    private int retcode;

    /**
     * echo。
     */
    @JsonProperty("echo")
    private String echo;

    /**
     * 参数数据。
     */
    @JsonProperty("data")
    private Map<String, Object> rawData;

    /**
     * 提示消息
     */
    @JsonProperty("message")
    private String message;

    /**
     * 提示消息 (人性化)
     */
    @JsonProperty("wording")
    private String wording;

    /**
     * 是否成功。
     *
     * @return 如果状态为 "ok"，则返回 true；否则返回 false。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return "ok".equalsIgnoreCase(status);
    }
}
