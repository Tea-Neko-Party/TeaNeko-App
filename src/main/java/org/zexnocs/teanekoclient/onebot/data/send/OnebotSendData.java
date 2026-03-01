package org.zexnocs.teanekoclient.onebot.data.send;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.AbstractJsonSendData;
import tools.jackson.databind.ObjectMapper;

/**
 * onebot 发送数据的基类。
 *
 * @param <S> sned params 数据类型，必须实现ISendParamsData接口。
 * @param <R> 响应数据类型。
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
public class OnebotSendData<S extends ISendParamsData<R>, R> extends AbstractJsonSendData<R> {
    /// 发送数据的动作名称，通常对应于 OneBot API 中的一个具体操作，例如 "send_message"。
    @JsonProperty("action")
    protected final String action;

    /// 发送数据的参数，包含了具体的操作细节，例如消息内容、目标用户等。
    /// 这个参数的数据类型由泛型 S 定义，必须实现 ISendParamsData 接口，以确保它具有必要的方法和属性来支持发送数据的功能。
    @JsonProperty("params")
    protected final S params;

    /**
     * 构造函数，初始化发送数据的内容、mapper 和响应类型，并生成一个唯一的 echo 以标识这条发送数据。
     *
     * @param params       发送数据的参数，包含了具体的操作细节
     * @param client       发送数据的客户端
     * @param mapper       用于翻译成 json 的 mapper
     * @param senderToken  发送器的 token，可以用于标识发送器身份
     */
    public OnebotSendData(S params,
                          @NonNull IClient client,
                          @NonNull ObjectMapper mapper,
                          @Nullable String senderToken) {
        super(client, mapper, params.getResponseDataType(), senderToken);
        this.params = params;
        this.action = params.getAction();
    }
}
