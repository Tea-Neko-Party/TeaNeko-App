package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionRuntime;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

// 2026/11/04：组件数据与系统按职责统一归类，持久标识保持不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 按实例安装表达合同，实际保留资料后才使用对应语法。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * 修改记录：<br>
 * 2026/11/04：组件或系统迁入统一的 component 分类，保持原执行语义。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * <br>2026/11/05：原生表情目录随表达组件独立装配并复核。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Component
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class ExpressionFeature implements IAgentFeature {
    private final ExpressionRuntime runtime;
    private final @org.jspecify.annotations.Nullable NativeFaceService faces;
    /**
     * 2026/11/05：保留独立旧宿主的纯文本合同；默认 Spring 装配使用完整原生目录服务。
     * @param runtime 原表达运行时
     */
    public ExpressionFeature(ExpressionRuntime runtime) {
        this.runtime = runtime;
        this.faces = null;
    }
    /**
     * 2026/11/05：保存编译及独立原生表情端口。
     * @param faces 不读取图库的原生目录服务
     * @param runtime 表达运行时
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ExpressionFeature(ExpressionRuntime runtime, NativeFaceService faces) {
        this.runtime = runtime;
        this.faces = faces;
    }
    /** @return 宿主插件 */
    @Override public String pluginId(){
        return "agent.expression";
    }
    /** @return 能力 ID */
    @Override public String id(){return "expression";}
    /** @return 默认安装 */
    @Override public boolean defaultEnabled(){return true;}
    /** @return 唯一资料声明 */
    @Override public Set<String> promptNames(){return Set.of(ExpressionRuntime.FRAGMENT, NativeFaceService.FRAGMENT);}
    /** @return 不进行模型调用的合同来源 */
    @Override public IAgentPromptSource prompts(){
        return new IAgentPromptSource(){
            /**
             * 注入当前完整合同。
             * 2026/11/05：移除包迁移后重复或未使用的类型导入。
             * @param definition 定义
             * @param request 请求
             * @param snapshot 快照
             * @param control 控制
             * @return 完整合同或空
             */
            @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition,AgentRunRequest request,AgentRuntimeSnapshot snapshot,ExecutionControl control){
                control.checkActive();
                if (!runtime.enabled(request)) { return CompletableFuture.completedStage(List.of()); }
                var fragments = new java.util.ArrayList<AgentPromptFragment>();
                fragments.add(new AgentPromptFragment(ExpressionRuntime.FRAGMENT, runtime.contract(request), null));
                if (faces != null) { fragments.addAll(faces.fragments(request)); }
                return CompletableFuture.completedStage(List.copyOf(fragments));
            }
            /**
             * 配置或目录变更时拒绝旧合同。
             * 2026/11/05：移除包迁移后重复或未使用的类型导入。
             * @param request 请求
             * @param input 原布局
             * @param control 控制
             * @return 校验阶段
             */
            @Override public CompletionStage<Void> verify(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){
                control.checkActive();
                // 2026/11/05：原生目录由表达组件复核，只有 FACE 不读取图库状态。
                if (faces != null) { faces.verify(request, input); }
                for(var fragment:input.fragments()){
                    if(fragment.name().equals(ExpressionRuntime.FRAGMENT)&&(!runtime.enabled(request)||!fragment.text().equals(runtime.contract(request)))){
                        return CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
                    }
                }
                return CompletableFuture.completedStage(null);
            }
            /**
             * 原合同允许只读重放校验。
             * 2026/11/05：移除包迁移后重复或未使用的类型导入。
             * @param request 请求
             * @param input 布局
             * @param control 控制
             * @return 校验通过时为真
             */
            @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){return verify(request,input,control).thenApply(ignored->true);}
        };
    }
}
