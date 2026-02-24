package org.zexnocs.teanekoapp.sender.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.IClient;

/**
 * 发送给客户端的数据接口。
 * <p>可自定义实现类来满足不同客户端需求。
 *
 * @author zExNocs
 * @date 2026/02/22
 * @since 4.0.8
 */
public interface ISendData<R> {
    /**
     * 将数据转化成字符串形式，通常是JSON字符串，以便发送给客户端。
     *
     * @return 转化后的字符串形式数据
     */
    @NonNull
    String toSendString();

    /**
     * 获取发送数据的 echo，用于后续匹配响应数据。
     * 建议使用 UUID 的字符串形式以注册 task。
     *
     * @return {@link String } echo字符串，不能为空
     */
    @NonNull
    String getEcho();

    /**
     * 获取发送数据的响应类型，用于后续处理响应数据。
     * <p>如果为 null 则表示响应数据为 null。
     *
     * @return 响应类型的Class对象
     */
    @Nullable
    @JsonIgnore
    Class<R> getResponseType();

    /**
     * 获取客户端。
     *
     * @return {@link IClient } 发送数据的客户端，不能为空
     */
    @NonNull
    @JsonIgnore
    IClient getClient();
}
