package org.zexnocs.teanekoclient.onebot.core;

import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.IChatTargetStore;
import org.zexnocs.teanekoclient.onebot.event.message.OnebotMessageReceiveEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从真实 OneBot 消息有界观察账号与聊天显示名，不记录内容或伪造私聊关系。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@EventListener
public final class OnebotChatObserver {
    private final IChatTargetStore store;
    private final IFileConfigService files;
    private final ILogger logger;
    private final Map<String, Instant> recent = new ConcurrentHashMap<>();

    /**
     * @param store 已知聊天目录
     * @param files 本地控制面开关
     * @param logger 脱敏诊断
     */
    public OnebotChatObserver(IChatTargetStore store, IFileConfigService files, ILogger logger) {
        this.store = store;
        this.files = files;
        this.logger = logger;
    }

    /**
     * @param event 真实平台消息，显示元数据不作为权限
     */
    @EventHandler
    public void observe(OnebotMessageReceiveEvent event) {
        var message = event.getData();
        if (message == null || message.getOnebotRawMessageData() == null) {
            return;
        }
        try {
            if (!files.requireCurrent(WebSecurityFileConfig.class).isEnabled()) {
                return;
            }
            long self = message.getOnebotRawMessageData().getSelfId();
            if (self <= 0) {
                return;
            }
            boolean group = message.getMessageType() == TeaNekoMessageType.GROUP;
            if (!group && message.getMessageType() != TeaNekoMessageType.PRIVATE
                    && message.getMessageType() != TeaNekoMessageType.PRIVATE_TEMP) {
                return;
            }
            String platform = group ? message.getUserData().getGroupId() : message.getUserData().getUserIdInPlatform();
            if (platform == null) {
                return;
            }
            Instant now = Instant.now();
            String key = self + ":" + message.getScopeId();
            synchronized (recent) {
                recent.values().removeIf(time -> time.plusSeconds(60).isBefore(now));
                if (recent.containsKey(key) || recent.size() >= 4096) {
                    return;
                }
                recent.put(key, now);
            }
            String name = group ? message.getScopeName() : message.getUserData().getNickname();
            name = name.length() > 128 ? name.substring(0, 128) : name;
            var value = new ChatTargets.Observation("onebot", Long.toString(self), group ? "GROUP" : "PRIVATE",
                    platform, message.getScopeId(), name, "MESSAGE");
            store.remember(List.of(value), () -> {}).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    logger.warn(getClass().getName(), "聊天目录元数据尚未保存，将在下一观察窗口继续。");
                }
            });
        } catch (RuntimeException ignored) {
            // 目录观测不阻断原消息处理，也不把原话或令牌写入诊断。
        }
    }
}
