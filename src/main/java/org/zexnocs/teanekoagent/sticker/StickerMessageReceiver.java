package org.zexnocs.teanekoagent.sticker;

import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 旁观真实图片，不要求触发回复，不取消其他插件消息。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@EventListener
public final class StickerMessageReceiver {
    private final StickerCollectionService service;
    /** @param service 有界持久采集 */
    public StickerMessageReceiver(StickerCollectionService service){this.service=service;}
    /** @param event 实际原生入站事件 */
    @EventHandler(priority=Integer.MIN_VALUE+2)
    public void onMessage(TeaNekoMessageReceiveEvent<?> event){
        var data=event.getData();if(event.isCancelled()||data==null){return;}
        int count=(int)Math.min(4,data.getMessages().stream().map(ITeaNekoContent::getContentPart).filter(IImageTeaNekoContentPart.class::isInstance).count());
        for(int index=0;index<count;index++){service.collect(data,false,data.getUserData().getUuid(),index).exceptionally(failure->null);}
    }
}
