package org.zexnocs.teanekoagent.agent.access;

import java.util.Objects;
import java.util.Optional;

/**
 * 不含原始异常或配置值的就绪检查结果。
 * @param status 当前就绪状态
 * @param snapshot 就绪时才存在的不可变运行数据
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentReadiness(Status status, Optional<AgentRuntimeSnapshot> snapshot) {
    /**
     * 保证只有 READY 才能携带运行快照，阻止失败路径继续使用半成品参数。
     * @param status 就绪状态
     * @param snapshot 可选运行快照
     * @throws IllegalArgumentException 当状态与快照是否存在不一致时
     */
    public AgentReadiness {
        Objects.requireNonNull(status, "就绪状态不能为空。");
        Objects.requireNonNull(snapshot, "就绪快照容器不能为空。");
        if ((status == Status.READY) != snapshot.isPresent()) { throw new IllegalArgumentException("就绪状态与运行快照不一致。"); }
    }

    /**
     * 可向管理入口解释的固定原因，不回显环境变量、配置值或异常链。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status {
        READY("Agent 配置已就绪。"),
        LOCAL_DISABLED("本地配置已关闭 Agent。"),
        LOCAL_CONFIG_UNAVAILABLE("Agent 本地运行设置不可用，请检查主配置及 LLM 交互配置。"),
        MODEL_UNAVAILABLE("Agent 模型路由、模型参数或能力配置不可用。"),
        CREDENTIALS_UNAVAILABLE("Agent 模型凭据或连接设置不可用，请检查 Spring application 配置。");

        private final String description;

        /**
         * 固定不含配置值的中文诊断。
         * @param description 可公开的说明
         */
        Status(String description) { this.description = description; }

        /**
         * 取得固定中文说明，不读取运行配置或异常文本。
         * @return 管理和日志可用的说明
         */
        public String description() { return description; }
    }
}
