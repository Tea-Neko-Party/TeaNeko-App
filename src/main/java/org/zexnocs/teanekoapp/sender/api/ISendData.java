package org.zexnocs.teanekoapp.sender.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 发送给客户端的数据接口。
 * 可自定义实现类来满足不同客户端需求。
 *
 * @author zExNocs
 * @date 2026/02/22
 */
public interface ISendData {
    /**
     * 将数据转化成字符串形式，通常是JSON字符串，以便发送给客户端。
     *
     * @return 转化后的字符串形式数据
     */
    @NonNull
    String toSendString();

    /**
     * 获取发送数据的 echo，用于后续匹配响应数据。
     *
     * @return {@link String } echo字符串，不能为空
     */
    @NonNull
    String getEcho();

    /**
     * 获取发送数据的响应类型，用于后续处理响应数据。
     * 如果为 null 则表示响应数据为 null。
     * 如果要解析成 JSON，应该加上 {@link com.fasterxml.jackson.annotation.JsonIgnore} 注解以避免被序列化。
     *
     * @return 响应类型的Class对象
     */
    @Nullable
    Class<?> getResponseType();
}
