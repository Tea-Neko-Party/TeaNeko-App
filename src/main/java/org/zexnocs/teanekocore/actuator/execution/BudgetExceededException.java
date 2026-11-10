package org.zexnocs.teanekocore.actuator.execution;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;
import java.util.Objects;

/**
 * 资源准入被拒绝或实际结算超额；失败预占不扣减额度，已经发生的消耗仍须保留。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/10：公开预算维度和数值对照，异步包装后仍能提供安全的拒绝原因。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.1-l
 */
public final class BudgetExceededException extends IllegalStateException implements IFailureReason {
    // 2026/11/10：异常仅持有原维度和自行生成的安全数值摘要。
    @Serial
    private static final long serialVersionUID = 1L;

    private final BudgetKey key;

    /**
     * 记录拒绝预占的资源维度与额度差异，供上层区分预算终止原因。
     *
     * @param key 定位目标数据的键
     * @param limit 允许使用的最大额度
     * @param used 此前已消耗额度
     * @param requested 本次申请额度
     */
    public BudgetExceededException(BudgetKey key, long limit, long used, long requested) {
        // 2026/11/10：只公开有界资源标识，不将任意键文本带入日志与聊天错误报告。
        super("资源预算不足：维度="
                + (key.value().matches("[A-Za-z0-9._-]{1,96}") ? key.value() : "非标准标识（已隐藏）")
                + "，上限=" + limit + "，已占用=" + used + "，剩余=" + Math.max(0, limit - used)
                + "，本次需要=" + requested + "。已占用包含在途预留。");
        this.key = key;
    }

    /**
     * 2026/11/10：只返回本类型构造的摘要，不读取第三方异常正文。
     * @return 预算不足的维度与数值对照
     */
    @Override
    public String failureReason() {
        return Objects.requireNonNull(super.getMessage());
    }

    /**
     * 提供额度不足的资源维度，供调用方区分预算原因而不解析异常文本。
     *
     * @return 满足本方法约束的 {@code BudgetKey} 结果
     */
    public BudgetKey key() {
        return key;
    }
}
