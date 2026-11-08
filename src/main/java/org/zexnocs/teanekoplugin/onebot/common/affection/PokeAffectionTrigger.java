package org.zexnocs.teanekoplugin.onebot.common.affection;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.notice.NotifyNoticeReceiveEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekoplugin.general.engagement.affection.interfaces.IAffectionService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 戳一戳 触发好感度事件
 * <br>每戳一次 增加 1 点好感度
 * <br>1h 内同一用户对同一目标只能增加一次好感度
 * <br>只在 Onebot 中触发
 *
 * @see org.zexnocs.teanekoplugin.general.engagement.affection.AffectionService
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@EventListener
@PluginOwned("onebot.common")
public class PokeAffectionTrigger {
    /// 冷却时间
    private static final Duration POKE_COOLDOWN = Duration.ofHours(1);

    private final IAffectionService iAffectionService;

    /// senderId, targetId -> lastPokeTimestamp
    private final Map<HashPair<UUID, UUID>, Instant> lastPokeTimestamps = new ConcurrentHashMap<>();
    private final ITeaUserService iTeaUserService;
    private final OnebotTeaNekoClient onebotTeaNekoClient;
    private final PluginRegistry plugins;

    /**
     * 2026/11/07：保留好感度为可选集成，不让其停用影响其他 OneBot 功能。
     * @param iAffectionService 好感度业务服务
     * @param iTeaUserService 用户定位服务
     * @param onebotTeaNekoClient 官方客户端
     * @param plugins 当前插件准入目录
     */
    public PokeAffectionTrigger(IAffectionService iAffectionService,
                                ITeaUserService iTeaUserService,
                                OnebotTeaNekoClient onebotTeaNekoClient, PluginRegistry plugins) {
        this.iAffectionService = iAffectionService;
        this.iTeaUserService = iTeaUserService;
        this.onebotTeaNekoClient = onebotTeaNekoClient;
        this.plugins = plugins;
    }

    /**
     * 2026/11/07：只有好感度插件当前启用时才处理可选戳一戳集成。
     * @param event 当前平台通知
     */
    @EventHandler
    public void handle(NotifyNoticeReceiveEvent event) {
        if (!plugins.isEnabled("general.engagement")) {
            return;
        }
        var token = plugins.capture("general.engagement");
        var time = Instant.now();
        var data = event.getData();
        long groupId = data.getGroupID();
        long senderId = data.getUserID();
        long targetId = data.getTargetID();
        var senderUUID = iTeaUserService.get(onebotTeaNekoClient, String.valueOf(senderId));
        var targetUUID = iTeaUserService.get(onebotTeaNekoClient, String.valueOf(targetId));
        if(senderUUID == null || targetUUID == null) {
            return;
        }
        if(groupId != 0) {
            // 判断是否在冷却时间内
            var key = HashPair.of(senderUUID, targetUUID);
            var lastPokeTime = lastPokeTimestamps.get(key);
            // 如果没有记录过 或者 已经过了冷却时间 则增加好感度
            if(lastPokeTime == null || !time.isBefore(lastPokeTime.plus(POKE_COOLDOWN))) {
                // 记录新的戳一戳时间
                lastPokeTimestamps.put(key, time);
                // 增加好感度
                plugins.check(token);
                iAffectionService.increaseAffectionDailyLimit(senderUUID, targetUUID, 1);
            }
        }
    }
}
