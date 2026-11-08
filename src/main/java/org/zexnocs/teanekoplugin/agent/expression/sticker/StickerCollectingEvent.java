package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.plugin.IPluginScopedEvent;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 同步采集草稿，只允许拒绝或补充低信任识别提示，真实来源不可修改。
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
public final class StickerCollectingEvent extends AbstractEvent<StickerCollectingEvent.Candidate> implements IPluginScopedEvent {
    private final Set<String> plugins;
    private boolean denied,frozen;
    private String hint="";
    /**
     * @param candidate 脱敏真实来源
     * @param plugins 当前允许参与的插件
     */
    public StickerCollectingEvent(Candidate candidate,Set<String> plugins){super(candidate);this.plugins=Set.copyOf(plugins);}
    /** @param hint 只作为数据交给视觉任务的提示 */
    public synchronized void suggest(String hint){mutable();if(hint==null||hint.length()>256){throw new IllegalArgumentException("素材识别提示最多256字符。");}this.hint=hint;}
    /** @return 当前建议 */
    public synchronized String suggestion(){return hint;}
    /** @return 最终不可变决定 */
    public synchronized IStickerLifecycle.Decision freeze(){frozen=true;return new IStickerLifecycle.Decision(!denied,hint);}
    /** @throws IllegalStateException 当事件已冻结时 */
    private void mutable(){if(frozen){throw new IllegalStateException("素材采集事件已经冻结。");}}
    /** @param data 不允许替换的真实来源 */
    @Override public void setData(Candidate data){throw new UnsupportedOperationException("素材候选来源不可替换。");}
    /** @return 是否已拒绝 */
    @Override public synchronized boolean isCancelled(){return denied;}
    /** @param cancelled 只能增加拒绝 */
    @Override public synchronized void setCancelled(boolean cancelled){mutable();if(cancelled){denied=true;}}
    /**
     * @param cancelled 只能增加拒绝
     * @return 原拒绝状态
     */
    @Override public synchronized boolean getAndSetCancelled(boolean cancelled){boolean previous=denied;setCancelled(cancelled);return previous;}
    /** 通知结束后即冻结，不给迟到回调修改机会。 */
    @Override public void _afterNotify(){freeze();}
    /**
     * @param pluginId 监听器归属
     * @return 是否在明确允许列表
     */
    @Override public boolean acceptsPlugin(String pluginId){return plugins.contains(pluginId);}
    /** @return 禁止异步决策监听 */
    @Override public boolean allowsAsyncListeners(){return false;}
    /**
     * 不含图片字节、URL、原话或作者信息的候选定位。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param proposalId 稳定候选 ID
     * @param scope 真实区域
     * @param manual 是否主动请求
     * @param image 原图片序号
     * @param occurredAt 原时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.9.0-l
     */
    public record Candidate(UUID proposalId,AgentScope scope,boolean manual,int image,Instant occurredAt){}
}
