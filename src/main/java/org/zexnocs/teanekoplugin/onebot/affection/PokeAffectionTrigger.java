package org.zexnocs.teanekoplugin.onebot.affection;

import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.event.notice.NotifyNoticeReceiveEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekoplugin.general.affection.interfaces.IAffectionService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 戳一戳 触发好感度事件
 * <br>每戳一次 增加 1 点好感度
 * <br>1h 内同一用户对同一目标只能增加一次好感度
 * <br>只在 Onebot 中触发
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@EventListener
public class PokeAffectionTrigger {
    /// 冷却时间
    private static final int POKE_COOLDOWN_MILLIS = 60 * 60 * 1000;

    private final IAffectionService iAffectionService;

    /// senderId, targetId -> lastPokeTimestamp
    private final Map<HashPair<UUID, UUID>, Long> lastPokeTimestamps = new ConcurrentHashMap<>();
    private final ITeaUserService iTeaUserService;
    private final OnebotTeaNekoClient onebotTeaNekoClient;

    public PokeAffectionTrigger(IAffectionService iAffectionService,
                                ITeaUserService iTeaUserService,
                                OnebotTeaNekoClient onebotTeaNekoClient) {
        this.iAffectionService = iAffectionService;
        this.iTeaUserService = iTeaUserService;
        this.onebotTeaNekoClient = onebotTeaNekoClient;
    }

    @EventHandler
    public void handle(NotifyNoticeReceiveEvent event) {
        long time = System.currentTimeMillis();
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
            Long lastPokeTime = lastPokeTimestamps.get(key);
            // 如果没有记录过 或者 已经过了冷却时间 则增加好感度
            if(lastPokeTime == null || time - lastPokeTime >= POKE_COOLDOWN_MILLIS) {
                // 记录新的戳一戳时间
                lastPokeTimestamps.put(key, time);
                // 增加好感度
                iAffectionService.increaseAffectionDailyLimit(senderUUID, targetUUID, 1);
            }
        }
    }
}
