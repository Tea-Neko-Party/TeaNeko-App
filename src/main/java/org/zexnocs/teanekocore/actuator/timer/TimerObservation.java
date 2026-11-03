package org.zexnocs.teanekocore.actuator.timer;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

/**
 * 定时规则的只读描述，由各计时器提供实际计算结果，未知值保持为空。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param kind 规则类型
 * @param rule 中文规则描述
 * @param nextFire 下一次预计触发，等待完成或未知时为空
 * @param waiting 是否等待上个原任务完成
 * @param zone 展示时区
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public record TimerObservation(String kind, String rule, @Nullable Instant nextFire, boolean waiting, String zone) {}
