package org.zexnocs.teanekocore.command;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 将实际命令目录投影为说明和补全数据，按同一权限与 scope 服务判断当前来源。
 * 目录不执行命令，不取得平台发送能力；全局说明读取由宿主提供管理员授权。
 * <br>2026/11/04：增加独立中文指令名称用于选择与说明卡片。
 * <br>2026/11/04：分别投影主指令默认规则、方法有效规则及权限组，供控制台展示继承差异。
 * <br>2026/11/05：查询使用扫描阶段缓存的类型说明，不在页面及补全查询中调用底层 Bean 扫描器。
 *
 * <br>2026/11/07：投影并缓存插件归属，保持启停与权限复核。
 * <br>2026/11/07：参数、来源类型和管理声明读取命令初始化缓存。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.9.0-l
 */
@Service
public final class CommandCatalog {
    private final CommandScanner scanner;
    private final ICommandPermissionManager permissions;
    private final ICommandScopeManager scopes;
    private final PluginAccess plugins;

    /**
     * 2026/11/05：查询仅依赖命令缓存，不注入底层 Bean 扫描器。
     * @param scanner 已注册命令
     * @param permissions 原权限规则
     * @param scopes 原客户端与范围规则
     * @param plugins 原插件归属
     */
    public CommandCatalog(CommandScanner scanner, ICommandPermissionManager permissions,
                          ICommandScopeManager scopes, PluginAccess plugins) {
        this.scanner = scanner;
        this.permissions = permissions;
        this.scopes = scopes;
        this.plugins = plugins;
    }

    /**
     * 2026/11/07：为总管理初始化提供完整安装说明，停用声明不因此获得命令调用资格。
     * @return 不含 Bean、方法句柄或当前权限的静态元数据
     */
    public List<InstalledCommand> installedDescription() {
        var result = new ArrayList<InstalledCommand>();
        for (var command : scanner.getRegisteredCommands()) {
            for (var method : command.getRegisteredMethods()) {
                var metadata = CommandMethodContract.metadata(method);
                var child = metadata.child();
                String id = command.getCommandAnnotation().value()[0]
                        + (child == null ? "" : " " + child.value()[0]);
                result.add(new InstalledCommand(plugins.owner(command.target(method), method), id, id,
                        metadata.description()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 安装命令的说明，不作为执行或可见性授权。
     * <br>2026/11/07：供上层建立一次性的插件贡献快照。
     *
     * @param pluginId 所属插件，宿主未声明归属时为空
     * @param id 默认命令或子命令的稳定标识
     * @param name 展示名称
     * @param description 功能说明
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record InstalledCommand(@Nullable String pluginId, String id, String name, String description) {}

    /**
     * @param context 已由宿主构造的真实来源，管理员查看全局说明时为空
     * @param guard 本次读取授权
     * @return 实际已启用的命令说明，来源非空时只返回有权调用的方法
     */
    public List<Entry> list(@Nullable CommandData<?> context, Runnable guard) {
        guard.run();
        // 2026/11/05：目录投影固定同一完整注册快照，显式重扫期间不混合两套命令。
        var maps = scanner.getRegisteredCommands();
        var entries = new ArrayList<Entry>();
        for (var map : maps) {
            // 2026/11/04：声明关闭的命令与实际分发器保持一致，不进入说明或补全目录。
            if (!map.getCommandAnnotation().enable()) {
                continue;
            }
            // 2026/11/05：复用原命令扫描的完整元数据，权限和插件状态仍实时判断。
            var parent = map.getDescription();
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
        if (!plugins.enabled(map.target(method), method)) {
            return;
        }
        var command = map.getCommandAnnotation();
        var defaults = map.getDefaultCommandAnnotation();
        var metadata = CommandMethodContract.metadata(method);
        if (context != null) {
            if (metadata.management() != null || !metadata.accepts(context.getRawData())) {
                return;
            }
            boolean allowed = child == null
                    ? scopes.inScope(command, defaults, context) && permissions.hasPermission(command, defaults, context)
                    : scopes.inScope(command, child, context) && permissions.hasPermission(command, child, context);
            if (!allowed) {
                return;
            }
        }
        // 2026/11/07：参数元数据在扫描阶段编译，当前调用只投影既有声明。
        var parameters = metadata.arguments();
        String sub = child == null ? "" : child.value()[0];
        String insertion = command.value()[0] + (sub.isEmpty() ? "" : " " + sub);
        String usage = insertion + parameters.stream().map(value -> " " + (value.optional() ? "[" : "<")
                + value.name() + (value.optional() ? "]" : ">")).collect(java.util.stream.Collectors.joining());
        var permission = child == null ? Objects.requireNonNull(defaults).permission() : child.permission();
        var scope = child == null ? defaults.scope() : child.scope();
        // 2026/11/04：权限组沿用执行器的非空覆盖语义；主规则独立返回，不借用首个方法的有效值。
        var packages = child == null ? defaults.permissionPackage() : child.permissionPackage();
        // 2026/11/04：输出独立中文显示名，命令匹配仍使用原前缀。
        entries.add(new Entry(command.value()[0], command.displayName(), List.of(command.value()), sub,
                child == null ? List.of() : List.of(child.value()), insertion, usage,
                parent == null ? "" : parent.value(), metadata.description(), parameters,
                (permission == CommandPermission.DEFAULT ? command.permission() : permission).name(),
                (scope == CommandScope.DEFAULT ? command.scope() : scope).name(),
                Arrays.stream(command.supportedClients()).map(Class::getSimpleName).toList(),
                command.mode() == Command.CommandMode.REGEX, metadata.management() != null,
                command.permission().name(), command.scope().name(), List.of(command.permissionPackage()),
                List.of(packages.length == 0 ? command.permissionPackage() : packages),
                Arrays.stream(command.supportedClients()).map(Class::getSimpleName).toList(),
                plugins.owner(map.target(method), method)));
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
     * <br>2026/11/07：投影并缓存插件归属，保持启停与权限复核。
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
     * @param defaultClients 主指令客户端声明，用于独立展示主规则及方法差异
     * @param pluginId 所属插件 ID，宿主未声明归属时为空
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.9.0-l
     */
    public record Entry(String command, String displayName, List<String> aliases, String subcommand, List<String> subAliases,
                        String insertion, String usage, String introduction, String description,
                        List<Argument> parameters, String permission, String scope, List<String> clients,
                        boolean regex, boolean management, String defaultPermission, String defaultScope,
                        List<String> defaultPackages, List<String> permissionPackages, List<String> defaultClients,
                        @Nullable String pluginId) {
        /**
         * 2026/11/07：保留宿主命令的既有构造，显式插件归属由目录投影填入。
         * @param command 主指令
         * @param displayName 中文名称
         * @param aliases 主指令别名
         * @param subcommand 子指令
         * @param subAliases 子指令别名
         * @param insertion 补全内容
         * @param usage 用法
         * @param introduction 总体说明
         * @param description 方法说明
         * @param parameters 参数目录
         * @param permission 成员权限
         * @param scope 聊天范围
         * @param clients 客户端声明
         * @param regex 是否正则
         * @param management 是否管理来源
         * @param defaultPermission 默认权限
         * @param defaultScope 默认范围
         * @param defaultPackages 默认权限组
         * @param permissionPackages 有效权限组
         * @param defaultClients 默认客户端
         */
        public Entry(String command, String displayName, List<String> aliases, String subcommand,
                List<String> subAliases, String insertion, String usage, String introduction, String description,
                List<Argument> parameters, String permission, String scope, List<String> clients, boolean regex,
                boolean management, String defaultPermission, String defaultScope, List<String> defaultPackages,
                List<String> permissionPackages, List<String> defaultClients) {
            this(command, displayName, aliases, subcommand, subAliases, insertion, usage, introduction, description,
                    parameters, permission, scope, clients, regex, management, defaultPermission, defaultScope,
                    defaultPackages, permissionPackages, defaultClients, null);
        }
    }
}
