package org.zexnocs.teanekocore.actuator.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多线程可共享的显式资源预算。未声明维度会报错，不静默当成无限额度。
 * 读数包含未结算预留，快照逐项采集，不表示多维度事务；结算可退还未使用额度。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class ResourceBudget {
    private final Map<BudgetKey, Counter> counters;

    /**
     * 复制并校验已声明的资源上限，各资源使用独立原子计数器；未声明维度不会自动放行。
     *
     * @param limits 已声明的资源上限映射
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public ResourceBudget(Map<BudgetKey, Long> limits) {
        Objects.requireNonNull(limits, "资源上限不能为空。");
        var values = new HashMap<BudgetKey, Counter>();
        limits.forEach((key, limit) -> {
            Objects.requireNonNull(key, "预算维度不能为空。");
            Objects.requireNonNull(limit, "预算上限不能为空。");
            if (limit < 0) {
                throw new IllegalArgumentException("预算上限不能为负数：" + key.value());
            }
            values.put(key, new Counter(limit));
        });
        counters = Map.copyOf(values);
    }

    /**
     * 预占指定资源；额度不足时明确抛错，失败不扣减已用量。
     *
     * @param key 定位目标数据的键
     * @param units 本次需要预占的正资源数量
     * @throws BudgetExceededException 当共享资源预算不足以预占本次额度时
     */
    public void consume(BudgetKey key, long units) {
        if (!tryConsume(key, units)) {
            var counter = counter(key);
            throw new BudgetExceededException(key, counter.limit, counter.state.get().used(), units);
        }
    }

    /**
     * 使用原子比较更新预占额度；扣减前以剩余额度判断，避免溢出和并发超支。
     *
     * @param key 定位目标数据的键
     * @param units 本次需要预占的正资源数量
     * @return 预占成功为 true，额度不足为 false；拒绝时已用量保持不变
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public boolean tryConsume(BudgetKey key, long units) {
        if (units <= 0) {
            throw new IllegalArgumentException("预占的资源数量必须为正数。");
        }
        var counter = counter(key);
        while (true) {
            var state = counter.state.get();
            if (state.exceeded() || units > counter.limit - state.used()) {
                return false;
            }
            if (counter.state.compareAndSet(state, new State(state.used() + units, false, false))) {
                return true;
            }
        }
    }

    /**
     * 原子预留可结算额度；调用方须结算实际消耗，或在业务尚未发起时关闭以退还全部预留。
     *
     * @param key 已声明的资源维度
     * @param units 本次预留的正资源数量
     * @return 仅能结算一次的预留句柄
     * @throws BudgetExceededException 当可用额度不足时
     */
    public Reservation reserve(BudgetKey key, long units) {
        consume(key, units);
        return new Reservation(counter(key), units);
    }

    /**
     * 查询可选维度是否显式声明，不将缺失维度解释为无限额度。
     *
     * @param key 资源维度
     * @return 存在声明时为 true
     */
    public boolean isDeclared(BudgetKey key) {
        return counters.containsKey(Objects.requireNonNull(key, "预算维度不能为空。"));
    }

    /**
     * 读取已结算消耗与在途预留的合计，结算退还可使此读数降低。
     *
     * @param key 定位目标数据的键
     * @return 指定维度已结算消耗与未结算预留的合计
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public long used(BudgetKey key) {
        return counter(key).state.get().used();
    }

    /**
     * 读取指定预算维度尚可使用的额度，不重置计数器。
     *
     * @param key 定位目标数据的键
     * @return 正常维度的可用额度，曾经超额封锁的维度始终为零
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public long remaining(BudgetKey key) {
        var counter = counter(key);
        var state = counter.state.get();
        return state.exceeded() ? 0 : counter.limit - state.used();
    }

    /**
     * 复制各资源的上限和已用量，向诊断方提供不可变用量视图。
     *
     * @return 不可变的资源上限与已用量快照
     */
    public Map<BudgetKey, Usage> snapshot() {
        var result = new HashMap<BudgetKey, Usage>();
        counters.forEach((key, counter) -> {
            var state = counter.state.get();
            result.put(key, new Usage(counter.limit, state.used(), state.exceeded(), state.overflow()));
        });
        return Map.copyOf(result);
    }

    /**
     * 定位已声明的资源计数器；不存在时报告契约错误而非创建无限额度。
     *
     * @param key 定位目标数据的键
     * @return 满足本方法约束的 {@code Counter} 结果
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private Counter counter(BudgetKey key) {
        var result = counters.get(Objects.requireNonNull(key, "预算维度不能为空。"));
        if (result == null) {
            throw new IllegalArgumentException("预算维度未声明：" + key.value());
        }
        return result;
    }

    /**
     * 单个资源维度的不可变用量快照。
     *
     * @param limit 允许使用的最大额度
     * @param used 已结算消耗与未结算预留的合计，溢出时为饱和值
     * @param exceeded 是否曾因实际结算超额而封锁此维度
     * @param overflow 实际累计量是否发生饱和溢出
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Usage(long limit, long used, boolean exceeded, boolean overflow) {
        /**
         * 保留普通固定消耗的快照构造方式。
         * @param limit 额度上限
         * @param used 当前占用量
         */
        public Usage(long limit, long used) { this(limit, used, false, false); }
    }

    /**
     * 预留的一次性结算句柄；超额结算保留事实并永久封锁该维度，后续退还不能解除封锁。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static final class Reservation implements AutoCloseable {
        private final Counter counter;
        private final long units;
        private final AtomicBoolean settled = new AtomicBoolean();

        /**
         * 接管已经计入预算的预留，不再次扣减额度。
         * @param counter 所属资源计数器
         * @param units 已预留的数量
         */
        private Reservation(Counter counter, long units) { this.counter = counter; this.units = units; }

        /**
         * 以实际消耗替换本次预留，允许实际值超出预留或总上限，并报告超额事实。
         * 资源已经使用后不得通过 close 当作未发生；未知消耗可保守按预留量结算。
         *
         * @param charged 非负实际或保守估算消耗
         * @return 结算后的维度快照，包含超额和溢出标记
         * @throws IllegalArgumentException 当消耗为负时
         * @throws IllegalStateException 当句柄已经结算或关闭时
         */
        public Usage settle(long charged) {
            if (charged < 0) { throw new IllegalArgumentException("结算消耗不能为负数。"); }
            if (!settled.compareAndSet(false, true)) { throw new IllegalStateException("资源预留已经结算。"); }
            return apply(charged);
        }

        /**
         * 原子替换本句柄的预留，溢出时饱和到 Long.MAX_VALUE 并保持封锁。
         * @param charged 本次消耗
         * @return 更新完成后的快照
         */
        private Usage apply(long charged) {
            for (;;) {
                var state = counter.state.get();
                long base = state.used() - units;
                boolean overflow = state.overflow() || charged > Long.MAX_VALUE - base;
                long used = state.overflow() || charged > Long.MAX_VALUE - base ? Long.MAX_VALUE : base + charged;
                boolean exceeded = state.exceeded() || overflow || used > counter.limit;
                if (counter.state.compareAndSet(state, new State(used, exceeded, overflow))) {
                    return new Usage(counter.limit, used, exceeded, overflow);
                }
            }
        }

        /** 仅在业务未发生时退还预留；关闭幂等，已结算句柄不会重复退还。 */
        @Override public void close() {
            if (settled.compareAndSet(false, true)) { apply(0); }
        }
    }

    /**
     * 原子资源状态，读数包含在途预留，超额封锁不会被后续退款取消。
     * @param used 当前占用量
     * @param exceeded 超额封锁标记
     * @param overflow 饱和溢出标记
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record State(long used, boolean exceeded, boolean overflow) {}

    /**
     * 共享预算内部的原子用量计数器，上限在创建时固定。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class Counter {
        private final long limit;
        private final AtomicReference<State> state = new AtomicReference<>(new State(0, false, false));

        /**
         * 固定单个资源维度的上限，已用量由独立原子计数器维护。
         *
         * @param limit 允许使用的最大额度
         */
        private Counter(long limit) {
            this.limit = limit;
        }
    }
}
