package org.zexnocs.teanekoagent.llm.framework.route;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMProviderAccess;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

import java.util.function.Supplier;

/**
 * 模型配置来源边界；服务、凭据和事务不进入模型 Options 或实体参数。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
public interface ILLMRouteSource {
    /**
     * @param route 完整路由
     * @return 实际注册的协议 ID
     */
    LLMModelId adapter(String route);
    /**
     * @param route 完整路由
     * @param base 协议初值
     * @return 不含秘密且绑定代次的参数
     */
    LLMModelOptions defaults(String route, ILLMModelOptions base);
    /**
     * @param route 完整路由
     * @return 仅交给实际 HTTP 构造的连接及凭据
     */
    LLMProviderAccess credentials(String route);
    /**
     * @param route 完整路由
     * @param action 同步请求构造
     * @param <T> 结果类型
     * @return 不等待网络的构造结果
     */
    <T> T fixed(String route, Supplier<T> action);
    /**
     * @param options 原解析参数
     * @throws LLMConfigurationException 当配置已经变化时
     */
    void verify(ILLMModelOptions options);
    /**
     * @param options 当前有效参数
     * @return 标记同一配置代次后的参数
     */
    LLMModelOptions stamp(LLMModelOptions options);
}
