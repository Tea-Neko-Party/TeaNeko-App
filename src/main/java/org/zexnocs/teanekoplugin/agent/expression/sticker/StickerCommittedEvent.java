package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.plugin.IPluginScopedEvent;

import java.util.Set;

/**
 * 事务提交后的只读观察事件，监听失败不改变已提交结果。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class StickerCommittedEvent extends AbstractEvent<IStickerLifecycle.Notice> implements IPluginScopedEvent {
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
