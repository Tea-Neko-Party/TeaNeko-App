package org.zexnocs.teanekocore.command;

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * 将实际命令目录投影为说明和补全数据，按同一权限与 scope 服务判断当前来源。
 * 目录不执行命令，不取得平台发送能力；全局说明读取由宿主提供管理员授权。
 * <br>2026/11/04：增加独立中文指令名称用于选择与说明卡片。
 * <br>2026/11/04：分别投影主指令默认规则、方法有效规则及权限组，供控制台展示继承差异。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.7-l
 */
@Service
public final class CommandCatalog {
    private final CommandScanner scanner;
    private final IBeanScanner beans;
    private final ICommandPermissionManager permissions;
    private final ICommandScopeManager scopes;
    private final PluginAccess plugins;

    /**
     * @param scanner 已注册命令
     * @param beans 原 Bean 类型发现
     * @param permissions 原权限规则
     * @param scopes 原客户端与范围规则
     * @param plugins 原插件归属
     */
    public CommandCatalog(CommandScanner scanner, IBeanScanner beans, ICommandPermissionManager permissions,
                          ICommandScopeManager scopes, PluginAccess plugins) {
        this.scanner = scanner;
        this.beans = beans;
        this.permissions = permissions;
        this.scopes = scopes;
        this.plugins = plugins;
    }

    /**
     * @param context 已由宿主构造的真实来源，管理员查看全局说明时为空
     * @param guard 本次读取授权
     * @return 实际已启用的命令说明，来源非空时只返回有权调用的方法
     */
    public List<Entry> list(@Nullable CommandData<?> context, Runnable guard) {
        guard.run();
        var maps = new LinkedHashSet<>(scanner.getPrefixCommandMap().values());
        scanner.getRegexpCommandKeys().forEach(key -> maps.add(scanner.getRegexpCommand(key)));
        var entries = new ArrayList<Entry>();
        for (var map : maps) {
            // 2026/11/04：声明关闭的命令与实际分发器保持一致，不进入说明或补全目录。
            if (!map.getCommandAnnotation().enable()) {
                continue;
            }
            var parent = AnnotatedElementUtils.findMergedAnnotation(beans.getBeanClass(map.getCommand()), Description.class);
            var method = map.getDefaultCommandMethod();
            if (method != null) {
                add(entries, map, method, null, context, parent);
            }
            var seen = new HashSet<Method>();
            for (var child : map.getSubCommandMap().values()) {
                if (seen.add(child.getSecond())) {
                    add(entries, map, child.getSecond(), child.getFirst(), context, parent);
                }
            }
        }
        guard.run();
        return entries.stream().sorted(Comparator.comparing(Entry::command).thenComparing(Entry::subcommand)).toList();
    }

    /**
     * @param entries 当前有限目录
     * @param map 已扫描命令
     * @param method 真实处理方法
     * @param child 子指令，默认方法为空
     * @param context 可选真实来源
     * @param parent 类型说明
     */
    private void add(List<Entry> entries, CommandMapData map, Method method, @Nullable SubCommand child,
                     @Nullable CommandData<?> context, @Nullable Description parent) {
        if (!plugins.enabled(map.getCommand(), method)) {
            return;
        }
        var command = map.getCommandAnnotation();
        var defaults = map.getDefaultCommandAnnotation();
        if (context != null) {
            if (method.isAnnotationPresent(ManagementCommand.class) || !accepts(method, context.getRawData())) {
                return;
            }
            boolean allowed = child == null
                    ? scopes.inScope(command, defaults, context) && permissions.hasPermission(command, defaults, context)
                    : scopes.inScope(command, child, context) && permissions.hasPermission(command, child, context);
            if (!allowed) {
                return;
            }
        }
        var parameters = new ArrayList<Argument>();
        for (var parameter : method.getParameters()) {
            if (CommandData.class.isAssignableFrom(parameter.getType())) {
                continue;
            }
            var description = parameter.getAnnotation(Description.class);
            var initial = parameter.getAnnotation(DefaultValue.class);
            parameters.add(new Argument(parameter.getName(), parameter.getType().getSimpleName(),
                    description == null ? "" : description.value(), initial != null, initial == null ? "" : initial.value()));
        }
        String sub = child == null ? "" : child.value()[0];
        String insertion = command.value()[0] + (sub.isEmpty() ? "" : " " + sub);
        String usage = insertion + parameters.stream().map(value -> " " + (value.optional() ? "[" : "<")
                + value.name() + (value.optional() ? "]" : ">")).collect(java.util.stream.Collectors.joining());
        var description = method.getAnnotation(Description.class);
        var permission = child == null ? defaults.permission() : child.permission();
        var scope = child == null ? defaults.scope() : child.scope();
        // 2026/11/04：权限组沿用执行器的非空覆盖语义；主规则独立返回，不借用首个方法的有效值。
        var packages = child == null ? defaults.permissionPackage() : child.permissionPackage();
        // 2026/11/04：输出独立中文显示名，命令匹配仍使用原前缀。
        entries.add(new Entry(command.value()[0], command.displayName(), List.of(command.value()), sub,
                child == null ? List.of() : List.of(child.value()), insertion, usage,
                parent == null ? "" : parent.value(), description == null ? "" : description.value(), parameters,
                (permission == CommandPermission.DEFAULT ? command.permission() : permission).name(),
                (scope == CommandScope.DEFAULT ? command.scope() : scope).name(),
                Arrays.stream(command.supportedClients()).map(Class::getSimpleName).toList(),
                command.mode() == Command.CommandMode.REGEX, method.isAnnotationPresent(ManagementCommand.class),
                command.permission().name(), command.scope().name(), List.of(command.permissionPackage()),
                List.of(packages.length == 0 ? command.permissionPackage() : packages)));
    }

    /**
     * @param method 原处理方法
     * @param raw 当前真实平台数据
     * @return 方法声明的原始数据类型是否兼容当前平台
     */
    private static boolean accepts(Method method, Object raw) {
        for (var type : method.getGenericParameterTypes()) {
            if (type instanceof ParameterizedType parameter && parameter.getRawType() == CommandData.class
                    && parameter.getActualTypeArguments()[0] instanceof Class<?> expected && !expected.isInstance(raw)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 参数的声明信息，不包含真实消息或执行对象。
     * @param name 参数名
     * @param type 类型提示
     * @param description 原说明
     * @param optional 是否有默认值
     * @param defaultValue 声明默认值
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    public record Argument(String name, String type, String description, boolean optional, String defaultValue) {}

/**
     * 一个真实命令方法的目录项，查看不代表可以执行。
     * 修改记录：<br>
     * 2026/11/04：增加中文显示名，调用前缀和权限合同保持独立。
     * <br>2026/11/04：携带主规则与有效权限组，说明默认继承与方法覆盖。
     *
     * @param command 主前缀
     * @param displayName 中文指令名称
     * @param aliases 主前缀别名
     * @param subcommand 子指令
     * @param subAliases 子指令别名
     * @param insertion 不带参数占位符的补全文本
     * @param usage 参数用法
     * @param introduction 类型说明
     * @param description 方法说明
     * @param parameters 参数声明
     * @param permission 原权限要求
     * @param scope 原范围要求
     * @param clients 原客户端声明
     * @param regex 是否为正则入口
     * @param management 是否只支持管理来源
     * @param defaultPermission 主指令成员要求
     * @param defaultScope 主指令范围要求
     * @param defaultPackages 主指令权限组
     * @param permissionPackages 当前方法有效权限组
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.7-l
     */
    public record Entry(String command, String displayName, List<String> aliases, String subcommand, List<String> subAliases,
                        String insertion, String usage, String introduction, String description,
                        List<Argument> parameters, String permission, String scope, List<String> clients,
                        boolean regex, boolean management, String defaultPermission, String defaultScope,
                        List<String> defaultPackages, List<String> permissionPackages) {}
}
