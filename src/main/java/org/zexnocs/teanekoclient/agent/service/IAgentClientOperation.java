package org.zexnocs.teanekoclient.agent.service;

import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import java.util.concurrent.CompletionStage;

/**
 * 进程内客户端的可选操作贡献，具体业务及参数校验由实现拥有。
 * <br>2026/11/07：宿主和插件使用相同注册合同，发现不等于取得执行资格。
 *
 * @param <R> 明确响应类型
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentClientOperation<R> {
    /** @return 静态且稳定的操作协议 */
    AgentOperationProtocol<R> protocol();

    /**
     * 2026/11/08：在持久认领及每次副作用前复核业务角色，不把授权逻辑写入客户端分支。
     * @param source 宿主固定的原生来源
     * @throws RuntimeException 当业务角色或适配能力已经失效时
     */
    default void verifySource(org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData source) {
    }

    /**
     * 固定动作摘要，具体操作有额外业务字段时须显式计入。
     * @param input 真实原文和附件
     * @param field 操作字段
     * @return 用于检测同一来源不同负载的摘要
     */
    default String digest(NativeMessageInput input, String field) {
        return input.digest();
    }

    /**
     * 在客户端已取得持久执行权后处理本次操作，阶段必须覆盖真实业务终态。
     * @param context 当前固定身份、字段、引用及借用执行控制
     * @return 实际响应，不执行传输回执或普通平台回复
     */
    CompletionStage<R> execute(AgentClientOperationContext context);
}
