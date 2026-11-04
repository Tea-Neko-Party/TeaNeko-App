package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
import java.time.Instant;
/**
 * 可解释的准入与淘汰评分；宿主另行约束视觉类型、配额与固定保护。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
public interface IStickerAdmissionPolicy {
    /**
     * @param analysis 模型判定
     * @param settings 阈值
     * @return 策略是否建议收藏
     */
    boolean accepts(Stickers.Analysis analysis,StickerSettings settings);
    /**
     * @param entry 候选条目
     * @param categoryCount 同类数量
     * @param now 当前时刻
     * @param settings 本地权重
     * @return 归一化竞争分数
     */
    double score(Stickers.Entry entry,long categoryCount,Instant now,StickerSettings settings);
}
