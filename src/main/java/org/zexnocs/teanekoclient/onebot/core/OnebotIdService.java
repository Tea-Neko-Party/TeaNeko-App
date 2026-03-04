package org.zexnocs.teanekoclient.onebot.core;

import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.event.meta.LifecycleMetaEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 用于记录 onebot 相关 ID 的服务。
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.0.12
 */
@EventListener
public class OnebotIdService {

    /**
     * 当前 bot 的 ID
     */
    @Getter
    private long botId;

    /**
     * 监听生命周期元事件，当事件发生时，更新 botId 的值为事件数据中的 selfId。
     * 这确保了在 bot 启动或重启时，botId 始终保持最新和正确的值。
     *
     * @param event 生命周期元事件，包含了 bot 的相关信息，例如 selfId。
     */
    @EventHandler
    public void onLifecycleMetaEvent(LifecycleMetaEvent event) {
        this.botId = event.getData().getSelfId();
    }
}
