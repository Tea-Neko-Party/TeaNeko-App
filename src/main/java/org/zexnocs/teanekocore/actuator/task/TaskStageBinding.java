package org.zexnocs.teanekocore.actuator.task;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekocore.actuator.task.api.ITaskStage;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 扫描或显式配置创建时冻结阶段归属，执行线程只调用原阶段和缓存标识。
 * <br>2026/11/07：手动阶段与扫描阶段共用同一声明快照，禁止执行时发现方法。
 *
 * @param stage 原阶段实现
 * @param pluginId 所属插件，宿主阶段为空
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record TaskStageBinding(ITaskStage stage, @Nullable String pluginId) implements ITaskStage {
    /**
     * 仅供扫描或显式配置创建调用；已经编译的阶段直接复用。
     * @param stage 原阶段或已有快照
     * @return 不可变归属快照
     */
    public static TaskStageBinding of(ITaskStage stage) {
        if (stage instanceof TaskStageBinding binding) {
            return binding;
        }
        var type = AopUtils.getTargetClass(stage);
        try {
            var method = AopUtils.getMostSpecificMethod(ITaskStage.class.getMethod("process", TaskStageChain.class), type);
            var owned = AnnotatedElementUtils.findMergedAnnotation(method, PluginOwned.class);
            if (owned == null) {
                owned = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class);
            }
            return new TaskStageBinding(stage, owned == null ? null : owned.value());
        } catch (NoSuchMethodException failure) {
            throw new IllegalStateException("任务阶段接口缺少约定入口。", failure);
        }
    }

    /**
     * 调用原阶段，插件有效性由原任务执行器在进入及结果交付时复核。
     * @param chain 当前同步阶段链
     * @return 原阶段结果
     */
    @Override
    public ITaskResult<?> process(TaskStageChain chain) {
        return stage.process(chain);
    }
}
