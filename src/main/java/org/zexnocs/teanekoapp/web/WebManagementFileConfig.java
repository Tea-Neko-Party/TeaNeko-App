package org.zexnocs.teanekoapp.web;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 管理入口的等待期限和真实在途限制，缩减额度不撤销已开始的物理工作。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
@Getter
@Setter
@FileConfig(value = "management", path = "web", type = FileConfigType.YAML)
public final class WebManagementFileConfig implements IFileConfigData {
    @Description("管理等待毫秒数\n100～300000；超时不证明写入回滚，保留原操作编号查询。")
    private int timeoutMillis = 30000;
    @Description("管理在途总数\n2～256；实际阶段结束后才归还。")
    private int maxInFlight = 32;
    @Description("单资源在途数\n1～总数减一，避免单个资源独占全部容量。")
    private int maxPerResource = 4;

    /**
     * @return 当前有效限制
     * @throws IllegalArgumentException 当期限或额度超出支持范围时
     */
    public Policy snapshot() {
        if (timeoutMillis < 100 || timeoutMillis > 300000 || maxInFlight < 2 || maxInFlight > 256
            || maxPerResource < 1 || maxPerResource >= maxInFlight) {
            throw new IllegalArgumentException("管理调用期限或并发额度无效。");
        }
        return new Policy(timeoutMillis, maxInFlight, maxPerResource);
    }

    /**
     * 单次准入固定的限制。
     *
     * @param timeoutMillis  等待毫秒数
     * @param maxInFlight    物理在途总数
     * @param maxPerResource 每资源物理在途数
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    public record Policy(int timeoutMillis, int maxInFlight, int maxPerResource) {
    }
}
