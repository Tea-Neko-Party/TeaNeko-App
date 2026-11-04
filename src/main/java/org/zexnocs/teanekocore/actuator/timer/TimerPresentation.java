package org.zexnocs.teanekocore.actuator.timer;

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekocore.framework.function.MethodCallable;
import org.zexnocs.teanekocore.utils.MethodCallableUtils;

import java.lang.reflect.Method;

/**
 * 不持有业务对象的定时展示快照；也供无法推断来源的手动注册显式声明。
 *
 * @param category 稳定分类键
 * @param categoryName 中文分类名
 * @param name 中文名称，空值保留原任务名
 * @param description 用途与执行边界
 * @param visible 是否展示
 * @param controllable 是否允许原 Core 门禁
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
public record TimerPresentation(String category, String categoryName, String name, String description,
                                boolean visible, boolean controllable) {
    public static final TimerPresentation UNCLASSIFIED =
            new TimerPresentation("unclassified", "未分类", "", "来源尚未声明分类。", true, true);

    /**
     * @param category 稳定分类键
     * @param categoryName 中文分类名
     * @param name 显示名
     * @param description 说明
     * @param visible 是否展示
     * @param controllable 是否可控
     * @throws IllegalArgumentException 当声明不满足目录边界时
     */
    public TimerPresentation {
        if (!category.matches("[a-z][a-z0-9_.-]{0,63}") || categoryName.isBlank() || categoryName.length() > 64
                || name.length() > 128 || description.length() > 1024) {
            throw new IllegalArgumentException("定时任务的分类或展示声明无效。");
        }
    }

    /**
     * @param callable 原任务方法引用或可序列化 lambda
     * @return 方法或类型声明，无法确认时明确归入未分类
     */
    public static TimerPresentation from(MethodCallable<?> callable) {
        return from(MethodCallableUtils.INSTANCE.extractMethod(callable));
    }

    /**
     * @param method 由实际任务取得的方法，未知时为空
     * @return 方法优先、类型其次的稳定展示声明
     */
    public static TimerPresentation from(@Nullable Method method) {
        if (method == null) {
            return UNCLASSIFIED;
        }
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, TimerDisplay.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), TimerDisplay.class);
        }
        return annotation == null ? UNCLASSIFIED : new TimerPresentation(annotation.category(), annotation.categoryName(),
                annotation.name(), annotation.description(), annotation.visible(), annotation.controllable());
    }
}
