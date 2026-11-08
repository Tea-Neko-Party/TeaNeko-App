package org.zexnocs.teanekoagent.runtime.context;

import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import java.util.concurrent.CompletionStage;

/**
 * 可替换的作用域上下文引用生命周期；宿主不识别提供者的业务组件标识。
 * <br>2026/11/07：以中立生命周期合同接入可选业务能力，保留宿主权限和异步完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentContextReferences {
    /**
     * @param assembly 本轮实际组件装配
     * @return 本端口是否参与此装配
     */
    boolean supports(AgentAssembly assembly);
    /**
     * @param scope 当前可信作用域
     * @return 当前配置和扩展资格是否允许解析引用，不创建存储
     */
    boolean available(AgentScope scope);
    /**
     * @param scope 已通过宿主准入的作用域
     * @return 本轮固定引用，异常表示无法建立业务边界
     */
    CompletionStage<? extends IAgentContextReference> resolve(AgentScope scope);
    /**
     * @param expected 原固定引用，禁止替换成新的引用
     * @return 原业务边界仍成立时完成
     */
    CompletionStage<Void> verify(IAgentContextReference expected);
}
