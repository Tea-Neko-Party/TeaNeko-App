package org.zexnocs.teanekoagent.sticker;

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.plugin.PluginScopedEvent;

import java.util.Set;

/**
 * 事务提交后的只读观察事件，监听失败不改变已提交结果。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class StickerCommittedEvent extends AbstractEvent<IStickerLifecycle.Notice> implements PluginScopedEvent {
    private final Set<String> plugins;
    /**
     * @param notice 真实提交快照
     * @param plugins 明确参与插件
     */
    public StickerCommittedEvent(IStickerLifecycle.Notice notice,Set<String> plugins){super(notice);this.plugins=Set.copyOf(plugins);}
    /** @param notice 禁止替换 */
    @Override public void setData(IStickerLifecycle.Notice notice){throw new UnsupportedOperationException("素材提交通知不可修改。");}
    /** @return 只读通知不可取消 */
    @Override public boolean isCancelled(){return false;}
    /** @param cancelled 禁止取消已提交结果 */
    @Override public void setCancelled(boolean cancelled){if(cancelled){throw new UnsupportedOperationException("已提交的素材变化不可取消。");}}
    /**
     * @param cancelled 禁止取消
     * @return 始终未取消
     */
    @Override public boolean getAndSetCancelled(boolean cancelled){setCancelled(cancelled);return false;}
    /**
     * @param pluginId 所属插件
     * @return 是否明确允许观察
     */
    @Override public boolean acceptsPlugin(String pluginId){return plugins.contains(pluginId);}
    /** @return 允许只读异步观察 */
    @Override public boolean allowsAsyncListeners(){return true;}
}
