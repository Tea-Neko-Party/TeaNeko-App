package org.zexnocs.teanekoclient.web;

import lombok.Getter;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;

/**
 * 独立生产网页客户端，沿用原注册、工具箱、命令及 Agent 入口，不复用其他平台身份。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@TeaNekoClient(value = WebTeaNekoClient.ID, description = "网页聊天")
public final class WebTeaNekoClient implements ITeaNekoClient {
    public static final String ID = "web";
    @Getter private final WebTransport client;
    @Getter private final WebToolbox teaNekoToolbox;

    /**
     * @param client 原类型化传输
     * @param teaNekoToolbox 原平台工具箱
     */
    public WebTeaNekoClient(WebTransport client, WebToolbox teaNekoToolbox) {
        this.client = client;
        this.teaNekoToolbox = teaNekoToolbox;
    }

    /** @return 与原客户端工具范围和 scope 对齐的固定标识 */
    @Override
    public String getClientId() {
        return ID;
    }
}
