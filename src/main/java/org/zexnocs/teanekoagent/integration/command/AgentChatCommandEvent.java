package org.zexnocs.teanekoagent.integration.command;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.plugin.IPluginScopedEvent;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 向扩展提供原生聊天命令的可信正文，未被处理的正文继续进入普通对话。
 * <br>2026/11/07：通用入口只协调认领与完整反馈，不依赖插件业务契约。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class AgentChatCommandEvent extends AbstractEvent<CommandData<ITeaNekoMessageData>>
        implements IPluginScopedEvent {
    private final String text;
    private boolean handled;

    /**
     * 固定本次已通过命令入口校验的来源与正文。
     * @param command 原生命令，不能使用模型派生命令
     * @param text 已去除语法前缀并保留内部空白的正文
     * @throws IllegalArgumentException 命令具有父执行控制时
     */
    public AgentChatCommandEvent(CommandData<ITeaNekoMessageData> command, String text) {
        super(command);
        if (command.getParentExecution() != null) {
            throw new IllegalArgumentException("派生命令不能进入原生聊天扩展入口。");
        }
        this.text = text;
    }

    /** @return 当前用户消息的原始正文 */
    public String text() {
        return text;
    }

    /**
     * 只在尚未认领时调用处理器，登记的阶段包含真实业务及平台反馈。
     * @param handler 返回空值表示不处理本条正文，不得留下未登记的后台业务
     * @return 本次处理器认领成功时为真
     */
    public synchronized boolean tryHandle(Supplier<Optional<CompletionStage<Void>>> handler) {
        if (handled) {
            return false;
        }
        var stage = handler.get();
        if (stage.isEmpty()) {
            return false;
        }
        trackProcessing(stage.orElseThrow());
        handled = true;
        return true;
    }

    /** @return 事件通知中已有扩展认领正文时为真 */
    public synchronized boolean handled() {
        return handled;
    }

    /**
     * 允许已通过插件目录启用检查的扩展识别正文。
     * @param pluginId 当前监听器的归属标识
     * @return 始终允许进入同步识别链
     */
    @Override
    public boolean acceptsPlugin(String pluginId) {
        return true;
    }

    /** @return 认领属于顺序决策，不允许异步监听改变归属 */
    @Override
    public boolean allowsAsyncListeners() {
        return false;
    }
}
