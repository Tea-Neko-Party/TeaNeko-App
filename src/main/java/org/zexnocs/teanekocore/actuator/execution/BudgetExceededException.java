package org.zexnocs.teanekocore.actuator.execution;

/**
 * 资源准入被拒绝或实际结算超额；失败预占不扣减额度，已经发生的消耗仍须保留。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class BudgetExceededException extends IllegalStateException {
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
        super("资源预算超限：维度=" + key.value() + "，上限=" + limit + "，已占用=" + used + "，本次申请=" + requested);
        this.key = key;
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
