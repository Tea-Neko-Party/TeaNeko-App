package org.zexnocs.teanekoagent.component;

import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.framework.composition.SystemContext;
import org.zexnocs.teanekocore.framework.composition.SystemDescriptor;
import org.zexnocs.teanekocore.framework.composition.SystemResult;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * 原 System 的版本守卫适配，不改变描述、业务状态或异步完成时点。
 *
 * 修改记录：<br>
 * 2026/11/04：从插件运行器迁入系统目录，通过检查函数保留原版本仲裁。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.0-l
 */
public final class AgentGuardedSystem implements IEntitySystem {
    private final IEntitySystem delegate;
    private final Consumer<AgentPluginSelection> check;

    /**
     * @param delegate 由 Spring 管理的原系统
     * @param check 原插件版本检查，不访问其他轮次的可变状态
     */
    public AgentGuardedSystem(IEntitySystem delegate, Consumer<AgentPluginSelection> check) {
        this.delegate = delegate;
        this.check = check;
    }

    /** @return 保持原读写键和执行顺序的声明 */
    @Override
    public SystemDescriptor descriptor() {
        return delegate.descriptor();
    }

    /**
     * 执行前后检查本轮固定选择，不将未完成的异步工作标记为成功。
     * @param context 原轮次上下文与控制
     * @return 原步骤完成且版本仍有效后的结果
     * @throws Exception 当原系统同步执行失败时
     */
    @Override
    public CompletionStage<SystemResult> execute(SystemContext context) throws Exception {
        var selection = context.snapshot().require(AgentComponents.PLUGIN_SELECTION);
        check.accept(selection);
        return delegate.execute(context).thenApply(result -> {
            check.accept(selection);
            return result;
        });
    }
}
