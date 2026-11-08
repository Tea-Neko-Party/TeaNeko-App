package org.zexnocs.teanekocore.actuator.timer;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.zexnocs.teanekocore.framework.function.IMethodCallable;
import org.zexnocs.teanekocore.utils.MethodCallableUtils;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerTaskConfig;

import java.lang.reflect.Method;

/**
 * 不持有业务对象的定时展示快照；也供无法推断来源的手动注册显式声明。
 * <br>2026/11/07：增加插件归属与有效状态约束。
 *
 * @param category 稳定分类键
 * @param categoryName 中文分类名
 * @param name 中文名称，空值保留原任务名
 * @param description 用途与执行边界
 * @param visible 是否展示
 * @param controllable 是否允许原 Core 门禁
 * @param pluginId 所属插件标识，宿主任务或未筛选请求为空
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.9.0-l
 */
public record TimerPresentation(String category, String categoryName, String name, String description,
                                boolean visible, boolean controllable, @Nullable String pluginId) {
    public static final TimerPresentation UNCLASSIFIED =
            new TimerPresentation("unclassified", "未分类", "", "来源尚未声明分类。", true, true);

    /**
     * 2026/11/07：兼容宿主任务原展示声明；插件归属仍由注册入口解析。
     * @param category 稳定分类键
     * @param categoryName 中文分类名
     * @param name 显示名称
     * @param description 用途说明
     * @param visible 是否展示
     * @param controllable 是否允许管理操作
     */
    public TimerPresentation(String category, String categoryName, String name, String description,
                             boolean visible, boolean controllable) {
        this(category, categoryName, name, description, visible, controllable, null);
    }

    /**
     * @param category 稳定分类键
     * @param categoryName 中文分类名
     * @param name 显示名
     * @param description 说明
     * @param visible 是否展示
     * @param controllable 是否可控
     * @param pluginId 明确归属，宿主任务为空
     * @throws IllegalArgumentException 当声明不满足目录边界时
     */
    public TimerPresentation {
        if (pluginId != null) {
            PluginDescriptor.requireId(pluginId);
        }
        if (!category.matches("[a-z][a-z0-9_.-]{0,63}") || categoryName.isBlank() || categoryName.length() > 64
                || name.length() > 128 || description.length() > 1024) {
            throw new IllegalArgumentException("定时任务的分类或展示声明无效。");
        }
    }

    /**
     * @param callable 原任务方法引用或可序列化 lambda
     * @return 方法或类型声明，无法确认时明确归入未分类
     */
    public static TimerPresentation from(IMethodCallable<?> callable) {
        return from(MethodCallableUtils.INSTANCE.extractMethod(callable));
    }

    /**
     * @param method 由实际任务取得的方法，未知时为空
     * @return 方法优先、类型其次的稳定展示声明
     */
    public static TimerPresentation from(@Nullable Method method) {
        // 2026/11/07：归属与展示同时在低频发现阶段冻结。
        if (method == null) {
            return UNCLASSIFIED;
        }
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, TimerDisplay.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), TimerDisplay.class);
        }
        var owned = AnnotatedElementUtils.findMergedAnnotation(method, PluginOwned.class);
        if (owned == null) {
            owned = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), PluginOwned.class);
        }
        String owner = owned == null ? null : owned.value();
        return annotation == null ? new TimerPresentation("unclassified", "未分类", "", "来源尚未声明分类。", true, true, owner)
                : new TimerPresentation(annotation.category(), annotation.categoryName(), annotation.name(),
                annotation.description(), annotation.visible(), annotation.controllable(), owner);
    }

    /**
     * 2026/11/07：仅在注册阶段合并显式展示与实际回调归属，手工定时器同样受约束。
     * @param config 原定时任务配置
     * @return 冻结后的展示及归属
     * @throws IllegalArgumentException 当显式归属与回调声明冲突时
     */
    public static TimerPresentation resolve(ITimerTaskConfig<?> config) {
        var declared = config.getPresentation();
        var detected = from(config.getTaskConfig().getCallable());
        String owner = detected.pluginId();
        if (owner != null && declared.pluginId() != null && !owner.equals(declared.pluginId())) {
            throw new IllegalArgumentException("定时器显式插件归属与回调声明不一致。");
        }
        return new TimerPresentation(declared.category(), declared.categoryName(), declared.name(), declared.description(),
                declared.visible(), declared.controllable(), owner == null ? declared.pluginId() : owner);
    }
}
