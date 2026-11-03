package org.zexnocs.teanekocore.command;

import org.zexnocs.teanekocore.command.api.CommandOrigin;
import org.zexnocs.teanekocore.command.api.ManagementCommand;
import org.zexnocs.teanekocore.command.interfaces.IManagedCommandSource;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;

/**
 * 管理来源在原命令链内默认拒绝；目录声明和实际排队启动都使用同一规则。
 * <br>2026/11/03：原生网页来源也可贡献实时守卫，排队后撤权不能继续执行。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class ManagementCommandPolicy {
    /** 禁止实例化命令策略工具。 */
    private ManagementCommandPolicy() {}

    /**
     * @param method 原命令方法
     * @return 是否明确接收受信管理上下文
     */
    public static boolean declared(Method method) {
        var declaration = method.getAnnotation(ManagementCommand.class);
        return declaration != null && declaration.version() == 1 && Arrays.stream(method.getGenericParameterTypes())
                .anyMatch(type -> type instanceof ParameterizedType parameter
                        && parameter.getRawType() == CommandData.class
                        && parameter.getActualTypeArguments()[0] instanceof Class<?> source
                        && IManagedCommandSource.class.isAssignableFrom(source));
    }

    /**
     * @param data 原可信命令数据
     * @param method 原目录选择的方法
     * @throws SecurityException 当管理来源未明确声明或宿主能力失效时
     */
    public static void check(CommandData<?> data, Method method) {
        // 2026/11/03：可撤销平台来源继续走原生角色和权限，额外复核当前宿主能力。
        if (data.getRawData() instanceof org.zexnocs.teanekocore.command.interfaces.ICommandSourceGuard guard) {
            guard.checkCommandSource();
        }
        if (data.getOrigin() == null) {
            throw new SecurityException("命令来源不能为空。");
        }
        if (data.getOrigin() == CommandOrigin.MANAGEMENT) {
            if (!declared(method) || !(data.getRawData() instanceof IManagedCommandSource source)) {
                throw new SecurityException("该命令方法没有声明管理来源能力。");
            }
            source.checkCommand(method);
        }
    }
}
