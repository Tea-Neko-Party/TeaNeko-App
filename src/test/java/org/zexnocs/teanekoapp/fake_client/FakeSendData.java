package org.zexnocs.teanekoapp.fake_client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import tools.jackson.databind.ObjectMapper;

/**
 * 一个用于测试的假发送消息类
 *
 * @author zExNocs
 * @date 2026/02/23
 */
@Getter
@AllArgsConstructor
public class FakeSendData implements ISendData<FakeResponseData> {

    /// 发送数据的内容，可以是任何需要发送的数据结构
    @JsonIgnore
    private final IClient client;

    /// 用于翻译成 json 的 mapper
    @JsonIgnore
    private final ObjectMapper mapper;

    /// echo
    @JsonProperty("echo")
    private final String echo;

    /// 还剩多少次可以 success
    @JsonProperty("count")
    private int count;

    /**
     * 将数据转化成字符串形式，通常是JSON字符串，以便发送给客户端。
     *
     * @return 转化后的字符串形式数据
     */
    @Override
    public @NonNull String toSendString() {
        count--;
        return mapper.writeValueAsString(this);
    }

    /**
     * 获取发送数据的响应类型，用于后续处理响应数据。
     * <p>如果为 null 则表示响应数据为 null。
     *
     * @return 响应类型的Class对象
     */
    @Override
    @JsonIgnore
    public @Nullable Class<FakeResponseData> getResponseType() {
        return FakeResponseData.class;
    }
}
