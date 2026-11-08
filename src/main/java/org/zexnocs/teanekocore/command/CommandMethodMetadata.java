package org.zexnocs.teanekocore.command;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.api.ManagementCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.interfaces.IManagedCommandSource;
import org.zexnocs.teanekocore.framework.description.Description;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令扫描阶段编译的静态方法合同，查询和执行只读取既有声明。
 * <br>2026/11/07：统一参数说明、来源类型、子命令及管理能力的缓存。
 *
 * @param description 方法用途
 * @param documented 是否声明了用途注解
 * @param child 子命令声明，默认方法为空
 * @param management 管理来源声明，普通方法为空
 * @param sourceTypes 明确约束的原始数据类型
 * @param arguments 参数说明，不包含当前输入值
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record CommandMethodMetadata(String description, boolean documented, @Nullable SubCommand child,
        @Nullable ManagementCommand management, List<Class<?>> sourceTypes, List<CommandCatalog.Argument> arguments) {
    /**
     * 仅由命令发现和显式独立宿主装配调用，不允许查询时补编译。
     * @param method 已发现的方法
     * @return 不带调用目标的静态说明
     */
    static CommandMethodMetadata compile(Method method) {
        var arguments = new ArrayList<CommandCatalog.Argument>();
        for (var parameter : method.getParameters()) {
            if (CommandData.class.isAssignableFrom(parameter.getType())) {
                continue;
            }
            var description = parameter.getAnnotation(Description.class);
            var initial = parameter.getAnnotation(DefaultValue.class);
            arguments.add(new CommandCatalog.Argument(parameter.getName(), parameter.getType().getSimpleName(),
                    description == null ? "" : description.value(), initial != null,
                    initial == null ? "" : initial.value()));
        }
        var sourceTypes = new ArrayList<Class<?>>();
        for (var type : method.getGenericParameterTypes()) {
            if (type instanceof ParameterizedType parameter && parameter.getRawType() == CommandData.class
                    && parameter.getActualTypeArguments()[0] instanceof Class<?> expected) {
                sourceTypes.add(expected);
            }
        }
        var description = method.getAnnotation(Description.class);
        return new CommandMethodMetadata(description == null ? "" : description.value(), description != null,
                method.getAnnotation(SubCommand.class), method.getAnnotation(ManagementCommand.class),
                List.copyOf(sourceTypes), List.copyOf(arguments));
    }

    /** @return 管理来源的版本和具体上下文类型是否完整声明 */
    public boolean managementDeclared() {
        return management != null && management.version() == 1
                && sourceTypes.stream().anyMatch(IManagedCommandSource.class::isAssignableFrom);
    }

    /**
     * @param raw 当前可信来源对象
     * @return 所有已声明来源约束是否满足
     */
    public boolean accepts(Object raw) {
        return sourceTypes.stream().allMatch(type -> type.isInstance(raw));
    }
}
