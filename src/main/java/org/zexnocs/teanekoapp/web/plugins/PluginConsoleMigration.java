package org.zexnocs.teanekoapp.web.plugins;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 在插件激活前迁移已授权管理资源，保留原配置树、秘密与显式空白名单。
 * <br>2026/11/07：精确映射来自插件贡献，宿主不依赖业务插件类型或固定标识。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
@Order(500)
public final class PluginConsoleMigration implements ApplicationRunner {
    // 2026/11/07：历史缺省权限固定为升级前范围，不使用新版默认值扩大现存实例的授权。
    private static final List<String> LEGACY_DEFAULT_RESOURCES = List.of("teaneko.agent:activity",
            "teaneko.agent:memory", "teaneko.agent:profiles", "teaneko.agent:personality", "teaneko.agent:layers",
            "teaneko.agent:schedules", "teaneko.agent:library", "teaneko.agent:memory-space",
            "teaneko.agent:personality-space", "teaneko.agent:profile-space", "teaneko.agent:layers-space",
            "teaneko.core:runtime", "teaneko.core:logs");
    private final IFileConfigService files;
    private final Map<String, String> mappings;

    /**
     * @param files 原配置文件入口
     * @param plugins 已装配插件目录
     * @param contributions 各插件自己声明的迁移
     * @throws IllegalArgumentException 当迁移归属或旧键冲突时
     */
    public PluginConsoleMigration(IFileConfigService files, PluginRegistry plugins,
            List<IManagementResourceMigration> contributions) {
        this.files = files;
        var found = new LinkedHashMap<String, String>();
        for (var contribution : contributions) {
            plugins.requireRegistered(contribution.pluginId());
            contribution.resourceMappings().forEach((oldKey, newKey) -> {
                if (!newKey.startsWith(contribution.pluginId() + ":") || found.putIfAbsent(oldKey, newKey) != null) {
                    throw new IllegalArgumentException("管理资源迁移归属无效或旧资源键重复：" + oldKey);
                }
            });
        }
        mappings = Map.copyOf(found);
    }

    /**
     * 只替换已有权限；旧显式白名单或历史缺省都不自动获得插件启停能力。
     * @param arguments 宿主启动参数
     */
    @Override
    public void run(@NonNull ApplicationArguments arguments) {
        files.withDocument(WebSecurityFileConfig.class, document -> {
            var snapshot = document.read();
            var tree = document.tree(snapshot);
            if (!(tree instanceof ObjectNode root)) {
                throw new IllegalArgumentException("Web 安全配置根节点必须是对象。");
            }
            var current = files.requireCurrent(WebSecurityFileConfig.class).snapshot();
            var original = snapshot.exists() && !root.has("management-resources")
                    ? LEGACY_DEFAULT_RESOURCES : current.managementResources();
            var migrated = new LinkedHashSet<String>();
            original.forEach(key -> migrated.add(mappings.getOrDefault(key, key)));
            boolean initial = root.path("plugin-management-version").asInt(0) < 1;
            if (!initial && migrated.equals(new LinkedHashSet<>(original))) {
                return null;
            }
            ArrayNode resources = root.putArray("management-resources");
            migrated.forEach(resources::add);
            root.put("plugin-management-version", 1);
            byte[] content = document.encode(root);
            var candidate = (WebSecurityFileConfig) document.parse(content);
            candidate.snapshot();
            try (var prepared = document.prepare(content)) {
                prepared.commit(snapshot.revision(), () -> {});
                document.publish(prepared.revision(), candidate, () -> {});
            }
            return null;
        });
    }
}
