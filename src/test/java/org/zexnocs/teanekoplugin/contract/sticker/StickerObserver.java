package org.zexnocs.teanekoplugin.contract.sticker;

import org.zexnocs.teanekoplugin.agent.expression.sticker.IStickerLifecycle;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerCollectingEvent;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerCommittedEvent;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试监听器仍由真实 Core scanner、插件校验与事件分发器调用。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：插件监听夹具迁入业务插件包，事件协议保持不变。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.event.core.EventListener
@PluginOwned("test.stickers")
public final class StickerObserver {
    public final List<IStickerLifecycle.Notice> notices=new CopyOnWriteArrayList<>();
    public volatile StickerCollectingEvent candidate;
    public boolean veto;
    /** @param event 只能建议和拒绝的真实草稿 */
    @EventHandler public void collecting(StickerCollectingEvent event){
        candidate=event;
        event.suggest("只记录可辨认的表情动作");
        if(veto){
            event.setCancelled(true);
            event.setCancelled(false);
        }
    }
    /** @param event 已经提交的只读通知 */
    @EventHandler public void committed(StickerCommittedEvent event){
        notices.add(event.getData());
        assertThrows(UnsupportedOperationException.class,()->event.setCancelled(true));
    }
}
