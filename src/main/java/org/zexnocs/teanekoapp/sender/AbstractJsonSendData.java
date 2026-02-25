package org.zexnocs.teanekoapp.sender;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * 一个用于快速定义发送数据的抽象类，提供了默认实现与使用 JSON 进行数据转换的功能。
 * 子类可以继承这个抽象类，并添加更多的 {@link JsonProperty} 来定义需要发送的数据结构。
 *
 * @author zExNocs
 * @date 2026/02/23
 * @since 4.0.9
 */
public abstract class AbstractJsonSendData<R> implements ISendData<R> {
    /// echo
    @Getter
    @JsonProperty("echo")
    private final String echo;

    /// 发送数据的内容，可以是任何需要发送的数据结构
    @Getter
    @JsonIgnore
    private final IClient client;

    /// 用于翻译成 json 的 mapper
    @JsonIgnore
    protected final ObjectMapper mapper;

    @Getter
    @JsonIgnore
    private final Class<R> responseType;

    @Getter
    @JsonIgnore
    private final String senderToken;

    /**
     * 构造函数，初始化发送数据的内容、mapper 和响应类型，并生成一个唯一的 echo 以标识这条发送数据。
     *
     * @param client 发送数据的客户端
     * @param mapper 用于翻译成 json 的 mapper
     * @param responseType 响应类型的 Class 对象，用于在接收响应时进行类型转换
     */
    public AbstractJsonSendData(@NonNull IClient client,
                                @NonNull ObjectMapper mapper,
                                @NonNull Class<R> responseType) {
        this(client, mapper, responseType, null);
    }

    /**
     * 构造函数，初始化发送数据的内容、mapper 和响应类型，并生成一个唯一的 echo 以标识这条发送数据。
     *
     * @param client 发送数据的客户端
     * @param mapper 用于翻译成 json 的 mapper
     * @param responseType 响应类型的 Class 对象，用于在接收响应时进行类型转换
     * @param senderToken  发送器的 token，可以用于标识发送器身份
     */
    public AbstractJsonSendData(@NonNull IClient client,
                                @NonNull ObjectMapper mapper,
                                @NonNull Class<R> responseType,
                                @Nullable String senderToken) {
        this.client = client;
        this.mapper = mapper;
        this.responseType = responseType;
        this.echo = UUID.randomUUID().toString();
        this.senderToken = senderToken;
    }

    /**
     * 将数据转化成字符串形式，通常是JSON字符串，以便发送给客户端。
     *
     * @return 转化后的字符串形式数据
     */
    @Override
    public @NonNull String toSendString() {
        return mapper.writeValueAsString(this);
    }
}
