package org.zexnocs.teanekocore.framework.composition;

import java.util.Objects;

/**
 * 失败通过异常传播；跳过不允许附带组件变更。
 *
 * @param outcome 继续、跳过或停止的步骤决定
 * @param patch 本步骤声明的不可变组件补丁
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record SystemResult(Outcome outcome, ComponentPatch patch) {
    /**
     * 单步骤的继续、跳过或停止决定，由管线统一应用。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Outcome { CONTINUE, SKIP, STOP }

    /**
     * 校验步骤结果与补丁的一致性；跳过结果不允许夹带组件写入。
     *
     * @param outcome 继续、跳过或停止的步骤决定
     * @param patch 本步骤声明的不可变组件补丁
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public SystemResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(patch, "patch");
        if (outcome == Outcome.SKIP && !patch.isEmpty()) {
            throw new IllegalArgumentException("A skipped system cannot modify components");
        }
    }

    /**
     * 声明提交补丁后继续后续步骤，具体提交仍由管线拥有者负责。
     *
     * @param patch 本步骤声明的不可变组件补丁
     * @return 由管线校验并提交的步骤决定与补丁
     */
    public static SystemResult continueWith(ComponentPatch patch) {
        return new SystemResult(Outcome.CONTINUE, patch);
    }

    /**
     * 声明本步骤不写入组件且继续执行，管线仍须检查快照是否过期。
     *
     * @return 由管线校验并提交的步骤决定与补丁
     */
    public static SystemResult skip() {
        return new SystemResult(Outcome.SKIP, ComponentPatch.empty());
    }

    /**
     * 声明提交本步骤补丁后停止后续步骤，不冒充外部副作用回滚。
     *
     * @param patch 本步骤声明的不可变组件补丁
     * @return 由管线校验并提交的步骤决定与补丁
     */
    public static SystemResult stop(ComponentPatch patch) {
        return new SystemResult(Outcome.STOP, patch);
    }
}
