package org.zexnocs.teanekoagent.agent;

import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolBinding;
import java.util.List;

/**
 * 为一轮选择明确的工具绑定，不默认回退到全局全部工具；执行仍须遵守本轮及业务权限。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface IAgentToolSource {
    /**
     * 在业务执行器中选择绑定；返回后固定定义和执行目标，不在本方法执行副作用。
     * @param definition 当前实例定义
     * @param request 宿主请求及可信身份，持久化模式包含本轮已解析的固定引用；原文凭据与历史分离
     * @param runtime 本轮已固定的运行设置
     * @return 明确选择的工具，空列表表示无工具
     */
    List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime);
}
