package org.zexnocs.teanekoagent.component.sticker;

import java.util.Objects;
import java.util.UUID;

/**
 * 自动采集的分析抽样；作业标识固定抽样值，事务重试不重复掷骰。
 * <br>2026/11/07：提供静态概率及随有效图库数量下降的平方指数曲线。
 *
 * @param mode 使用的模式
 * @param minimum 动态概率下限
 * @param maximum 动态概率上限
 * @param decayRate 动态变化率
 * @param fixed 静态概率
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
public record StickerAnalysisSampling(Mode mode, double minimum, double maximum, double decayRate, double fixed) {
    /**
     * 两种模式的参数均校验，使切换模式不留下潜在无效配置。
     * @param mode 当前模式
     * @param minimum 动态下限
     * @param maximum 动态上限
     * @param decayRate 有限正变化率
     * @param fixed 静态概率
     * @throws IllegalArgumentException 当概率、上下限或变化率无效时
     */
    public StickerAnalysisSampling {
        Objects.requireNonNull(mode);
        if (!StickerSettings.unit(minimum) || !StickerSettings.unit(maximum) || minimum > maximum
                || !StickerSettings.unit(fixed) || !Double.isFinite(decayRate) || decayRate <= 0) {
            throw new IllegalArgumentException("自动解析概率须在 0 至 1 之间，下限不大于上限，变化率须为有限正数。");
        }
    }

    /**
     * @param activeCount 当前有效素材数
     * @return 本次使用的概率；动态公式为 min + (max-min) × exp(-(rate×n)²)
     */
    public double probability(long activeCount) {
        double scaled = decayRate * Math.max(0, activeCount);
        return mode == Mode.STATIC ? fixed : minimum + (maximum - minimum) * Math.exp(-scaled * scaled);
    }

    /**
     * 采用随机 UUID 的 53 个均匀位，显式请求由调用方直接绕过本方法。
     * @param jobId 系统分配且不会因事务重试变化的作业标识
     * @param activeCount 当前有效数量
     * @return 是否执行本次自动分析
     */
    public boolean accepts(UUID jobId, long activeCount) {
        double draw = ((jobId.getLeastSignificantBits() & 0x3fffffffffffffffL) >>> 9) * 0x1.0p-53;
        return draw < probability(activeCount);
    }

    /**
     * 仅选择使用哪组参数，其余参数仍保存。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    public enum Mode { DYNAMIC, STATIC }
}
