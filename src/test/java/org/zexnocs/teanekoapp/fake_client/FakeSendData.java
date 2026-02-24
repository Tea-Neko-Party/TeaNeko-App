package org.zexnocs.teanekoapp.fake_client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.AbstractJsonSendData;
import tools.jackson.databind.ObjectMapper;

/**
 * 一个用于测试的假发送消息类
 *
 * @author zExNocs
 * @date 2026/02/23
 */
@Getter
public class FakeSendData extends AbstractJsonSendData<FakeResponseData> {

    /// 还剩多少次可以 success
    @JsonProperty("count")
    private int count;

    /**
     * 构造函数，初始化发送数据的内容、mapper 和响应类型，并生成一个唯一的 echo 以标识这条发送数据。
     *
     * @param client       发送数据的客户端
     * @param mapper       用于翻译成 json 的 mapper
     */
    public FakeSendData(@NonNull IClient client,
                        @NonNull ObjectMapper mapper,
                        int count) {
        super(client, mapper, FakeResponseData.class);
        this.count = count;
    }

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
}
