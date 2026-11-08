package org.zexnocs.teanekoagent.runtime.operation;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 聊天准入、采集及工具副作用共用的持久化防重端口，先提交占用再执行；未知在途记录不得自动重新执行。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：允许无记忆的主动活动保留独立持久身份，其他动作继续要求引用。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public interface IAgentOperationStore {
    /**
     * 原子登记执行权或读取原回执，不自动接管旧在途记录。
     * @param attempt 宿主固定的完整动作身份
     * @return 是否取得唯一执行权及已有状态
     */
    CompletionStage<Claim> claim(Attempt attempt);
    /**
     * 在原占用行记录真实完成状态；失败保留在途，不宣称没有副作用。
     * @param attempt 原动作
     * @param receipt 观察到的实际终态
     * @return 持久化后的实际回执
     */
    CompletionStage<AgentOperationReceipt> finish(Attempt attempt, AgentOperationReceipt receipt);
    /**
     * 不保存命令正文、密钥或原生客户端，摘要只用于检测同 ID 不同内容。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param requestId 原宿主请求
     * @param callId 原工具调用 ID
     * @param actor 原真实发起者
     * @param reference 原完整空间引用；仅独立主动活动允许为空
     * @param commandDigest 原命令文本摘要
     * @param kind 原生聊天准入或具体工具动作，二者使用不同幂等空间
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Attempt(UUID requestId, String callId, AgentActorContext actor, @Nullable IAgentContextReference reference, String commandDigest, Kind kind) {
        /**
         * 创建具体工具动作，保留原工具调用标识而不拼接伪造前缀。
         * @param requestId 原请求
         * @param callId 原调用 ID
         * @param actor 原来源
         * @param reference 原空间
         * @param commandDigest 原正文摘要
         */
        public Attempt(UUID requestId, String callId, AgentActorContext actor, IAgentContextReference reference, String commandDigest) {
            this(requestId, callId, actor, reference, commandDigest, Kind.TOOL);
        }
        /**
         * 普通动作限定人类来源，独立自主发生限定 SYSTEM；类别由宿主固定，不接受模型声明。
         * @param requestId 原请求
         * @param callId 原调用 ID
         * @param actor 原来源
         * @param reference 固定空间
         * @param commandDigest 原正文摘要
         * @param kind 原动作类别
         * @throws IllegalArgumentException 当来源、范围或摘要无效时
         * @throws NullPointerException 当必需身份、引用或类别为空时
         */
        public Attempt {
            Objects.requireNonNull(requestId); Objects.requireNonNull(actor); Objects.requireNonNull(kind);
            if (callId == null || callId.isBlank() || callId.length() > 256
                    || actor.origin() != (kind == Kind.PROACTIVE_ENTRY ? AgentActorContext.Origin.SYSTEM : AgentActorContext.Origin.USER)
                    // 2026/11/07：聊天及内部客户端的防重独立于可选业务引用，具体写入由插件校验引用。
                    || reference == null && kind != Kind.PROACTIVE_ENTRY && kind != Kind.CHAT_ENTRY && kind != Kind.CLIENT_ENTRY
                    || reference != null && !actor.scope().equals(reference.scope())
                    || commandDigest == null || !commandDigest.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("命令动作来源、范围或摘要无效。");
            }
        }
    }
    /**
     * 当前动作的唯一执行权，未取得时只返回原状态或冲突。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param acquired 是否允许本次真正执行
     * @param receipt 已有回执或明确在途状态
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    record Claim(boolean acquired, AgentOperationReceipt receipt) {}
    /**
     * 分离原生消息的单次模型准入、采集和工具调用，避免重启或换代后重放旧来源。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    enum Kind { CHAT_ENTRY, TOOL, COLLECT_ENTRY, PROACTIVE_ENTRY, CLIENT_ENTRY }
}
