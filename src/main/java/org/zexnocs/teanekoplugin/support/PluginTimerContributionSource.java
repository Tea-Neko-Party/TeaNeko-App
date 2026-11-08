package org.zexnocs.teanekoplugin.support;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.zexnocs.teanekoapp.plugin.PluginContribution;
import org.zexnocs.teanekoapp.plugin.IPluginContributionSource;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将插件任务的静态展示声明提供给总管理，不依赖插件启动后才存在的计时器实例。
 * <br>2026/11/07：仅在贡献目录初始化和显式重扫时发现声明，页面查询读取上层完整快照。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginTimerContributionSource implements IPluginContributionSource {
    private final IClassScanner classes;

    /**
     * 保存低频声明发现入口，构造阶段不启动业务任务。
     * @param classes 已装配插件类的发现入口
     */
    public PluginTimerContributionSource(IClassScanner classes) {
        this.classes = classes;
    }

    /**
     * 投影类型与显式方法声明；类型声明不重复展开到每个方法。
     * @return 包含当前停用插件的静态任务介绍
     */
    @Override
    public List<PluginContribution> contributions() {
        var found = new ArrayList<PluginContribution>();
        classes.getClassesWithAnnotation(PluginOwned.class).forEach((type, owner) -> {
            var display = AnnotatedElementUtils.findMergedAnnotation(type, TimerDisplay.class);
            if (display != null && display.visible()) {
                found.add(contribution(owner.value(), type.getName(), type.getSimpleName(), display));
            }
            for (var method : ReflectionUtils.getUniqueDeclaredMethods(type)) {
                if (method.isSynthetic() || method.isBridge()) {
                    continue;
                }
                var methodDisplay = AnnotatedElementUtils.findMergedAnnotation(method, TimerDisplay.class);
                if (methodDisplay == null || !methodDisplay.visible()) {
                    continue;
                }
                var methodOwner = AnnotatedElementUtils.findMergedAnnotation(method, PluginOwned.class);
                found.add(contribution(methodOwner == null ? owner.value() : methodOwner.value(),
                        methodId(type, method), method.getName(), methodDisplay));
            }
        });
        return found.stream().sorted(Comparator.comparing(PluginContribution::pluginId)
                .thenComparing(PluginContribution::id)).toList();
    }

    /**
     * 保留方法重载身份，不在运行时依赖方法对象或业务实例。
     * @param type 已装配的真实声明类型
     * @param method 显式展示方法
     * @return 稳定类型及签名标识
     */
    private static String methodId(Class<?> type, Method method) {
        String parameters = Arrays.stream(method.getParameterTypes()).map(Class::getName)
                .collect(Collectors.joining(","));
        return type.getName() + "#" + method.getName() + "(" + parameters + ")";
    }

    /**
     * 只复制允许公开的名称与用途，不读取配置和运行值。
     * @param pluginId 声明所属插件
     * @param id 稳定任务身份
     * @param fallbackName 未显式命名时使用的声明名
     * @param display 原任务介绍
     * @return 静态贡献记录
     */
    private static PluginContribution contribution(String pluginId, String id, String fallbackName,
                                                   TimerDisplay display) {
        return new PluginContribution(pluginId, "task", id,
                display.name().isBlank() ? fallbackName : display.name(), display.description());
    }
}
