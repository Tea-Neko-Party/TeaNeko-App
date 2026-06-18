package org.zexnocs.teanekoagent_old.file_config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

/**
 * Agent token 监控器文件配置。
 * <br>该配置写入 {@code config/agent/token-monitor.yml}，用于控制 token 记录、上下文快照保留、
 * 清理任务和告警上报策略。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@FileConfig(
        value = "token-monitor",
        path = "agent",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
public class AgentTokenMonitorFileConfig implements IFileConfigData {
    /**
     * 是否启用 token 监控器。
     */
    private boolean enabled = true;

    /**
     * 是否记录上下文快照。
     */
    private boolean recordContext = true;

    /**
     * 单条上下文快照最多保留的字符数。
     */
    private int maxContextSnapshotCharacters = 200_000;

    /**
     * 小型 token 使用量阈值。
     * <br>小于该值的上下文快照使用短保留期。
     */
    private int shortUsageTokenThreshold = 1_024;

    /**
     * 异常 token 使用量阈值。
     * <br>大于或等于该值的记录会被标记为异常。
     */
    private int abnormalUsageTokenThreshold = 16_384;

    /**
     * 小型 token 使用的上下文快照保留天数。
     */
    private int shortContextRetentionDays = 7;

    /**
     * 普通或较长 token 使用的上下文快照保留天数。
     */
    private int longContextRetentionDays = 30;

    /**
     * 异常 token 使用的上下文快照保留天数。
     * <br>小于 {@code 0} 表示不自动清理；等于 {@code 0} 表示不保存异常上下文快照。
     */
    private int abnormalContextRetentionDays = 0;

    /**
     * 模型上下文窗口大小。
     * <br>大于 {@code 0} 时用于判断上下文 token 是否接近耗尽。
     */
    private int contextWindowTokens = 0;

    /**
     * 剩余 token 告警阈值。
     */
    private int lowRemainingTokenThreshold = 512;

    /**
     * token 使用比例告警阈值。
     */
    private double warningUsageRatio = 0.85;

    /**
     * token 使用比例异常阈值。
     */
    private double abnormalUsageRatio = 0.95;

    /**
     * 是否在 warning 级别告警时向 debugger 报告。
     */
    private boolean reportWarningToDebugger = true;

    /**
     * 是否在 abnormal 级别告警或异常时向 debugger 报告。
     */
    private boolean reportAbnormalToDebugger = true;

    /**
     * 指定日志报告接收者。
     * <br>为空时使用 logger 的默认监护人或 debugger。
     */
    private String reportRecipients = "";

    /**
     * 是否启用上下文快照清理定时任务。
     */
    private boolean cleanupEnabled = true;

    /**
     * 上下文快照清理任务 cron 表达式。
     */
    private String cleanupCron = "0 0 4 * * *";
}
