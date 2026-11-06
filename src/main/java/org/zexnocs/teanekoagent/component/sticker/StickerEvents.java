package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 复用 Core 事件总线，决策与只读通知均有在途上限，后台插件参与须明确选择。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：无参与插件的精简宿主无需插件目录。
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.4-l
 */
@Component
public final class StickerEvents implements IStickerLifecycle {
    private final ObjectProvider<IEventService> events;
    private final ObjectProvider<PluginRegistry> plugins;
    private final IFileConfigService files;
    private final ILogger logger;
    private final AtomicInteger pending=new AtomicInteger();
    private final AtomicLong warning=new AtomicLong();
    /**
     * @param events 原事件服务
     * @param plugins 原插件目录
     * @param files 本地参与列表
     * @param logger 脱敏诊断
     */
    public StickerEvents(ObjectProvider<IEventService> events,ObjectProvider<PluginRegistry> plugins,IFileConfigService files,ILogger logger){this.events=events;this.plugins=plugins;this.files=files;this.logger=logger;}
    /**
     * @param source 原候选
     * @param settings 原预算
     * @return 同步监听结束后的固定决定
     */
    @Override public CompletionStage<Decision> before(Stickers.Source source,StickerSettings settings){
        var bus=events.getIfAvailable();if(bus==null){return CompletableFuture.completedStage(new Decision(true,""));}
        if(pending.incrementAndGet()>64){pending.decrementAndGet();return CompletableFuture.completedStage(new Decision(false,""));}
        StickerCollectingEvent event=null;
        try{
            var id=UUID.nameUUIDFromBytes(StableDigest.sha256(source.scope().toString(),source.self(),source.message(),Integer.toString(source.image())).getBytes(StandardCharsets.UTF_8));
            event=new StickerCollectingEvent(new StickerCollectingEvent.Candidate(id,source.scope(),source.manual(),source.image(),source.occurredAt()),selected());var draft=event;
            var physical=bus.pushEventWithFuture(event).asStage();physical.whenComplete((ignored,failure)->pending.decrementAndGet());
            return physical.thenApply(value->value).toCompletableFuture().orTimeout(5,TimeUnit.SECONDS).handle((result,failure)->{
                var decision=draft.freeze();if(failure!=null){throw new CompletionException(failure);}if(result==null||!result.isSuccess()){throw new IllegalStateException("素材采集监听未成功完成。");}return decision;
            });
        }catch(RuntimeException failure){if(event!=null){event.freeze();}pending.decrementAndGet();return CompletableFuture.failedStage(failure);}
    }
    /** @param notice 已提交快照，拥塞或监听失败不改变业务结果 */
    @Override public void committed(Notice notice){
        var bus=events.getIfAvailable();if(bus==null){return;}if(pending.incrementAndGet()>64){pending.decrementAndGet();return;}
        try{bus.pushEventWithFuture(new StickerCommittedEvent(notice,selected())).asStage().whenComplete((result,failure)->{pending.decrementAndGet();if(failure!=null||result==null||!result.isSuccess()){warn(notice, failure);}});}
        catch(RuntimeException failure){pending.decrementAndGet();warn(notice, failure);}
    }
    /** @return 当前明确参与且已经启用的插件 */
    private Set<String> selected(){
        var names=List.copyOf(files.requireCurrent(AgentStickerFileConfig.class).getEventPlugins());
        if(names.size()>16||names.stream().anyMatch(v->!v.matches("[a-z][a-z0-9_.-]{0,63}"))||names.stream().distinct().count()!=names.size()){throw new IllegalArgumentException("素材事件插件列表无效。");}
        // 2026/11/05：无插件选择的精简宿主无需插件目录，避免方法引用接收者被提前求值。
        if (names.isEmpty()) { return Set.of(); }
        var registry=plugins.getIfAvailable();if(registry==null){throw new IllegalStateException("素材事件插件缺少原目录。");}
        return names.stream().filter(registry::isEnabled).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    /**
     * 2026/11/06：提交后的通知失败关联原操作，保留安全根因但不重放业务。
     * @param notice 已提交通知
     * @param failure 监听器异常；未返回成功状态时为空
     */
    private void warn(Notice notice, @org.jspecify.annotations.Nullable Throwable failure) {
        long now = System.currentTimeMillis();
        long previous = warning.get();
        if (now - previous > 60000 && warning.compareAndSet(previous, now)) {
            logger.error(StickerEvents.class.getName(), "素材提交通知未被全部观察；业务已提交，不自动重放。操作="
                    + notice.operationId() + "，" + (failure == null ? "监听器未返回成功结果。"
                    : org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure)));
        }
    }
}
