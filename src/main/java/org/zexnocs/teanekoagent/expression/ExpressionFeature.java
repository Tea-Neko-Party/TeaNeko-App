package org.zexnocs.teanekoagent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.agent.AgentDefinition;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.IAgentPromptSource;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 按实例安装表达合同，实际保留资料后才使用对应语法。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Component
public final class ExpressionFeature implements AgentFeature {
    private final ExpressionRuntime runtime;
    /**
     * 保存编译端口。
     * @param runtime 表达运行时
     */
    public ExpressionFeature(ExpressionRuntime runtime){this.runtime=runtime;}
    /** @return 宿主插件 */
    @Override public String pluginId(){return "teaneko.agent";}
    /** @return 能力 ID */
    @Override public String id(){return "expression";}
    /** @return 默认安装 */
    @Override public boolean defaultEnabled(){return true;}
    /** @return 唯一资料声明 */
    @Override public Set<String> promptNames(){return Set.of(ExpressionRuntime.FRAGMENT);}
    /** @return 不进行模型调用的合同来源 */
    @Override public IAgentPromptSource prompts(){
        return new IAgentPromptSource(){
            /**
             * 注入当前完整合同。
             * @param definition 定义
             * @param request 请求
             * @param snapshot 快照
             * @param control 控制
             * @return 完整合同或空
             */
            @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition,AgentRunRequest request,AgentRuntimeSnapshot snapshot,ExecutionControl control){
                control.checkActive();return CompletableFuture.completedStage(runtime.enabled(request)?List.of(new AgentPromptFragment(ExpressionRuntime.FRAGMENT,runtime.contract(request),null)):List.of());
            }
            /**
             * 配置或目录变更时拒绝旧合同。
             * @param request 请求
             * @param input 原布局
             * @param control 控制
             * @return 校验阶段
             */
            @Override public CompletionStage<Void> verify(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){
                control.checkActive();
                for(var fragment:input.fragments()){
                    if(fragment.name().equals(ExpressionRuntime.FRAGMENT)&&(!runtime.enabled(request)||!fragment.text().equals(runtime.contract(request)))){
                        return CompletableFuture.failedStage(new AgentPromptException(AgentPromptException.Reason.CHANGED));
                    }
                }
                return CompletableFuture.completedStage(null);
            }
            /**
             * 原合同允许只读重放校验。
             * @param request 请求
             * @param input 布局
             * @param control 控制
             * @return 校验通过时为真
             */
            @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){return verify(request,input,control).thenApply(ignored->true);}
        };
    }
}
