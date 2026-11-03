package org.zexnocs.teanekoclient.web;

import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;

/**
 * 网页客户端真实入站事件，可由插件按具体平台类型监听。
 * <br>2026/11/03：在事件实际出队后复核来源，关闭提交与通知之间的撤权间隙。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class WebMessageReceiveEvent extends TeaNekoMessageReceiveEvent<WebMessageData> {
    /** @param data 服务器铸造的原生消息 */
    public WebMessageReceiveEvent(WebMessageData data) {
        super(data);
    }

    /**
     * 2026/11/03：原总线开始通知之前再次检查，撤权后不能启动自动 Agent 监听器。
     * @throws SecurityException 当原来源已经退出、过期、变更成员或消息被清空时
     */
    @Override
    public void _beforeNotify() {
        var data = getData();
        if (data == null) {
            throw new SecurityException("网页入站事件缺少原来源消息。");
        }
        data.checkCommandSource();
    }
}
