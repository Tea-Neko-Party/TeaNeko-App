package org.zexnocs.teanekocore.actuator.runtime;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

/**
 * 运行观测的本地容量和保留策略，不提供任意日志文件读取。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Getter
@Setter
@FileConfig(value = "observability", path = "runtime", type = FileConfigType.YAML)
public final class RuntimeSettings implements IFileConfigData {
    private int maxRuns = 1000;
    private int maxLogs = 2000;
    private int retentionMinutes = 60;
    private boolean captureMessages;

    /**
     * @return 有界策略快照，日志正文默认关闭，须由本机显式启用
     * @throws IllegalArgumentException 当容量或保留分钟数超过本机边界时
     */
    public Policy snapshot() {
        if (maxRuns < 100 || maxRuns > 10000 || maxLogs < 100 || maxLogs > 20000
                || retentionMinutes < 5 || retentionMinutes > 1440) {
            throw new IllegalArgumentException("运行观测的容量或保留分钟数无效。");
        }
        return new Policy(maxRuns, maxLogs, retentionMinutes, captureMessages);
    }

    /**
     * 一次观测使用的不可变限制。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param maxRuns 最多任务数
     * @param maxLogs 最多日志条数
     * @param retentionMinutes 最长保留分钟数
     * @param captureMessages 是否记录脱敏正文
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Policy(int maxRuns, int maxLogs, int retentionMinutes, boolean captureMessages) {}
}
