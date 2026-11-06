package org.zexnocs.teanekoapp.teauser;

import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 所有平台真实入站更新公开资料；保存失败只报告本次观测失败，不中断聊天。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.7-l
 * @version 4.8.4-l
 */
@EventListener
public final class ChatIdentityObserver {
    private final ChatIdentityService identities;
    private final ILogger logger;

    /**
     * @param identities 专用资料入口
     * @param logger 项目日志
     */
    public ChatIdentityObserver(ChatIdentityService identities, ILogger logger) {
        this.identities = identities;
        this.logger = logger;
    }

    /**
     * @param event 已适配的真实平台消息
     */
    @EventHandler
    public void observe(TeaNekoMessageReceiveEvent<?> event) {
        identities.observe(event.getData()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.warn(getClass().getName(), "聊天资料写入失败；后续消息将继续更新。", failure);
            }
        });
    }
}
