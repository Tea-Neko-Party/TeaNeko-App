package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

// 2026/11/04：组件数据与系统按职责统一归类，持久标识保持不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.plugin.AgentFeature;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 明确拥有媒体资料和目录工具，预算省略资料后不会暗中保留选图权限。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：统一媒体能力和注解工具的归属标识，修复启动注册失败。
 *
 * 修改记录：<br>
 * 2026/11/04：组件或系统迁入统一的 component 分类，保持原执行语义。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Component
public final class MediaReplyFeature implements AgentFeature {
    /** 2026/11/04：媒体能力与所属注解工具共用的稳定标识。 */
    public static final String ID = "media-replies";

    private final StickerReplyService service;private final StickerReplyTools tools;private final ObjectMapper mapper;private final Executor executor;
    /**
     * @param service 原资源宿主
     * @param tools 原容器工具
     * @param mapper 转换器
     * @param executor 借用业务域
     */
    public MediaReplyFeature(StickerReplyService service,StickerReplyTools tools,ObjectMapper mapper,@Qualifier("virtualExecutor") Executor executor){this.service=service;this.tools=tools;this.mapper=mapper;this.executor=executor;}
    /** @return 宿主插件 */ @Override public String pluginId(){return "teaneko.agent";}
    /**
     * 2026/11/04：与所属工具注解共享标识，防止注册声明与实际能力不一致。
     *
     * @return 稳定的媒体能力标识
     */
    @Override
    public String id() {
        return ID;
    }
    /** @return 默认安装 */ @Override public boolean defaultEnabled(){return true;}
    /** @return 同时安装完整表达合同，显式选择媒体能力也能解析最终指令 */
    @Override public Set<String> dependencies(){return Set.of("teaneko.agent:expression");}
    /** @return 原资源片段声明 */ @Override public Set<String> promptNames(){return Set.of(StickerReplyService.FRAGMENT);}
    /** @return 只在已知原生目标读取资源的来源 */
    @Override public IAgentPromptSource prompts(){return new IAgentPromptSource(){
        /**
         * 2026/11/05：移除包迁移后重复或未使用的类型导入。
         * @param definition 原定义
         * @param request 原请求
         * @param runtime 原设置
         * @param control 原控制
         * @return 当前资源片段
         */
        @Override public CompletionStage<List<AgentPromptFragment>> resolve(AgentDefinition definition,AgentRunRequest request,AgentRuntimeSnapshot runtime,ExecutionControl control){return service.fragments(request,control);}
        /**
         * 2026/11/05：移除包迁移后重复或未使用的类型导入。
         * @param request 原请求
         * @param input 实际保留布局
         * @param control 原控制
         * @return 原资源复核
         */
        @Override public CompletionStage<Void> verify(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){return service.verify(request,input,control);}
        /**
         * 2026/11/05：移除包迁移后重复或未使用的类型导入。
         * @param request 原请求
         * @param input 原布局
         * @param control 原控制
         * @return 原动态来源仍可核验时为真
         */
        @Override public CompletionStage<Boolean> verifyReplay(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){return service.verify(request,input,control).thenApply(ignored->true);}
    };}
    /** @return 必须看到实际布局才安装的工具源 */
    @Override public IAgentToolSource tools(){return new IAgentToolSource(){
        /**
         * 2026/11/05：移除包迁移后重复或未使用的类型导入。
         * @param definition 定义
         * @param request 请求
         * @param runtime 设置
         * @return 缺少布局时没有工具
         */
        @Override public List<LLMToolBinding> select(AgentDefinition definition,AgentRunRequest request,AgentRuntimeSnapshot runtime){return List.of();}
        /**
         * 2026/11/05：移除包迁移后重复或未使用的类型导入。
         * @param definition 定义
         * @param request 请求
         * @param runtime 设置
         * @param input 原布局
         * @return 明确选入的注解工具
         */
        @Override public List<LLMToolBinding> select(AgentDefinition definition,AgentRunRequest request,AgentRuntimeSnapshot runtime,AgentPromptInput input){return TeaNekoAgentToolMethods.select(tools,definition,request,runtime,input,mapper,executor);}
    };}
}
