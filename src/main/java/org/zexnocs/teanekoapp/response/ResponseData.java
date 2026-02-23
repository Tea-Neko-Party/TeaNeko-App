package org.zexnocs.teanekoapp.response;


import lombok.Getter;

/**
 * 用于接收从 client 的响应信息。
 * 由各个 client 的适配器转化成该接口，并作为数据推送 ResponseEvent 事件。
 *
 * @author zExNocs
 * @date 2026/02/22
 * @since 4.0.8
 */
@Getter
public class ResponseData {
    /// 状态，是否是成功的响应
    /// 一般常见 {"status": "ok"} 与 {"status": "error"}，分别对应 success = true 与 success = false
    private boolean success;

    /// echo 字段，唯一标识符，用于匹配发送信息时注册的 future 与客户端响应的信息
    private String echo;

    /// 数据的原始字符串，将会在 {@link org.zexnocs.teanekoapp.response.interfaces.IResponseService} 中被解析成对应的对象
    private String rawData;
}
