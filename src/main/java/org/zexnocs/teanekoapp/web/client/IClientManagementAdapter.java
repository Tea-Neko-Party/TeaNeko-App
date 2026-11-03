package org.zexnocs.teanekoapp.web.client;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 平台向人可理解的管理视图投影；首批只读状态，不推测账号或指令能力。
 * <br>2026/11/03：按平台贡献账号与聊天定位，未知实现保持不开放设置。
 * <br>2026/11/03：指令台由平台明确贡献就绪状态和固定连接能力。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IClientManagementAdapter {
    /**
     * 2026/11/03：发现管理适配器不代表可以执行命令。
     * @param target 原服务端解析目标
     * @return 当前真实状态，默认不支持
     */
    default CompletionStage<org.zexnocs.teanekoapp.web.console.ClientConsole.State> consoleState(ChatTargets.Resolved target) {
        return CompletableFuture.completedStage(new org.zexnocs.teanekoapp.web.console.ClientConsole.State(
                "UNSUPPORTED", "", "该平台尚未提供指令台连接能力。"));
    }

    /**
     * @param target 原服务端解析目标
     * @param guard 当前会话和平台插件授权
     * @return 固定原连接的短暂能力；默认明确拒绝
     */
    default CompletionStage<org.zexnocs.teanekoapp.web.console.ClientConsole.Lease> consoleLease(ChatTargets.Resolved target, Runnable guard) {
        return CompletableFuture.failedStage(new UnsupportedOperationException("该平台尚未提供指令台连接能力。"));
    }
    /** @return 本次实际观测，不缓存在线许可 */
    View describe();

    /** @return 平台已观察账号，不支持时为空 */
    default CompletionStage<List<ChatTargets.Account>> accounts() {
        return CompletableFuture.completedStage(List.of());
    }

    /**
     * @param account 原账号引用
     * @param after 原页尾聊天引用
     * @param kind 类型过滤
     * @return 有界聊天页
     */
    default CompletionStage<ChatTargets.Page> targets(String account, String after, String kind) {
        return CompletableFuture.failedStage(new UnsupportedOperationException("该平台尚未支持聊天设置目录。"));
    }

    /**
     * @param account 原账号
     * @param target 原聊天
     * @return 重新解析的内部目标
     */
    default CompletionStage<ChatTargets.Resolved> resolve(String account, String target) {
        return CompletableFuture.failedStage(new UnsupportedOperationException("该平台尚未支持聊天设置定位。"));
    }

    /**
     * @param guard 管理授权复核
     * @return 同步实际联系人后的账号目录
     */
    default CompletionStage<List<ChatTargets.Account>> discover(Runnable guard) {
        return CompletableFuture.failedStage(new UnsupportedOperationException("该平台尚未支持联系人发现。"));
    }

    /**
     * 不含会话句柄、凭据、内部 scope 或消息正文的平台卡片。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param clientId 稳定平台标识
     * @param name 人可理解的平台名称
     * @param state 稳定连接状态
     * @param connections 实际开放连接数
     * @param explanation 当前状态的中文说明
     * @param observedAt 实际观测时间
     * @param settingsAvailable 是否已有可用聊天设置适配
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    record View(String clientId, String name, String state, int connections,
                String explanation, Instant observedAt, boolean settingsAvailable) {
        /**
         * 2026/11/03：保留旧只读平台构造，不推测设置能力。
         * @param clientId 原平台
         * @param name 中文名称
         * @param state 原状态
         * @param connections 真实连接数
         * @param explanation 说明
         * @param observedAt 观测时间
         */
        public View(String clientId, String name, String state, int connections,
                    String explanation, Instant observedAt) {
            this(clientId, name, state, connections, explanation, observedAt, false);
        }
    }
}
