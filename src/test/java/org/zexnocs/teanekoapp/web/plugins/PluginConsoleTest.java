package org.zexnocs.teanekoapp.web.plugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.DefaultApplicationArguments;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekocore.file_config.FileConfigScanner;
import org.zexnocs.teanekocore.file_config.FileConfigService;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证插件页面实时撤销和真实文件迁移，不借目录发现授予资源权限。
 * <br>2026/11/07：覆盖停用、失败重扫、精确权限迁移与显式拒绝。
 * <br>2026/11/08：文件原文断言使用当前 JSON 文本读取接口。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginConsoleTest {
    @TempDir
    Path root;

    /** 目录只在显式重扫中发现，停用及白名单撤销会立即隐藏已经发现的页面。 */
    @Test
    void directoryUsesCachedDiscoveryAndCurrentEligibility() {
        var beans = mock(IBeanScanner.class);
        var plugins = plugins();
        var files = mock(IFileConfigService.class);
        var security = new WebSecurityFileConfig();
        // 2026/11/07：默认开放新注册页面，不要求事先补齐旧资源列表。
        security.setManagementResources(List.of());
        when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(security);
        var contribution = new Contribution("sample.plugin", List.of(new PluginConsolePage("page", "示例页面",
                "测试功能", "sample.plugin:page", 1, Set.of("sample.plugin:items"))));
        when(beans.getBeansOfType(IPluginConsoleContribution.class)).thenReturn(Map.of("page", contribution));
        var directory = new PluginConsoleDirectory(beans, plugins, files);
        assertThrows(IllegalStateException.class, directory::list);
        directory.init();
        clearInvocations(beans);
        assertEquals("sample.plugin", directory.list().getFirst().pluginId());
        plugins.setEnabled("sample.plugin", false);
        assertTrue(directory.list().isEmpty());
        plugins.setEnabled("sample.plugin", true);
        security.setLocalAllowlistEnabled(true);
        security.setManagementResources(List.of());
        assertTrue(directory.list().isEmpty());
        verifyNoInteractions(beans);
        security.setManagementResources(List.of("sample.plugin:items"));
        when(beans.getBeansOfType(IPluginConsoleContribution.class)).thenReturn(Map.of(
                "first", contribution, "duplicate", contribution));
        assertThrows(IllegalArgumentException.class, directory::reload);
        assertEquals(1, directory.list().size());
    }

    /**
     * 精确迁移已经授权的资源，未知字段及秘密原文保留，重复启动不重写文件。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void migratesOnlyExistingPermissionsAndPreservesOriginalDocument() throws Exception {
        var files = files("management-resources: ['legacy:items', 'other:keep']\n");
        var migration = new PluginConsoleMigration(files, plugins(), List.of(new Migration("sample.plugin",
                Map.of("legacy:items", "sample.plugin:items", "legacy:missing", "sample.plugin:missing"))));
        migration.run(new DefaultApplicationArguments());
        var actual = files.requireCurrent(WebSecurityFileConfig.class).snapshot().managementResources();
        assertTrue(actual.contains("sample.plugin:items"));
        assertTrue(actual.contains("other:keep"));
        assertFalse(actual.contains("sample.plugin:missing"));
        assertFalse(actual.contains("legacy:items"));
        assertFalse(actual.stream().anyMatch(key -> key.startsWith("teaneko.core:plugins.")));
        var path = root.resolve("config/web/security.yml");
        var first = Files.readAllBytes(path);
        var parser = new YAMLFileTypeParser(new MockEnvironment());
        var tree = parser.readDocument(first);
        // 2026/11/08：沿用当前 JSON 节点接口，保持对未知原文和秘密的精确断言。
        assertEquals("仅测试使用的值", tree.path("unknown-setting").asString());
        assertEquals("仅测试秘密", tree.path("admin-token").asString());
        migration.run(new DefaultApplicationArguments());
        assertArrayEquals(first, Files.readAllBytes(path));
    }

    /**
     * 2026/11/07：启用本地限制后，显式空白名单继续拒绝管理资源。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void explicitEmptyAllowlistRemainsDenied() throws Exception {
        var files = files("local-allowlist-enabled: true\nmanagement-resources: []\n");
        new PluginConsoleMigration(files, plugins(), List.of()).run(new DefaultApplicationArguments());
        assertTrue(files.requireCurrent(WebSecurityFileConfig.class).snapshot().managementResources().isEmpty());
        assertFalse(files.requireCurrent(WebSecurityFileConfig.class).snapshot()
                .allowsManagementResource("teaneko.core:plugins.catalog"));
    }

    /**
     * 2026/11/07：旧配置保留历史列表，缺省策略直接开放新宿主资源，无需修改磁盘列表。
     * @throws Exception 当临时配置创建失败时
     */
    @Test
    void omittedAllowlistPreservesHistoricalListAndDefaultsToOpenAccess() throws Exception {
        var files = files("");
        var migration = new PluginConsoleMigration(files, plugins(), List.of(new Migration("sample.plugin",
                Map.of("teaneko.agent:memory", "sample.plugin:memory"))));
        migration.run(new DefaultApplicationArguments());
        var actual = files.requireCurrent(WebSecurityFileConfig.class).snapshot().managementResources();
        assertEquals(13, actual.size());
        assertTrue(actual.contains("sample.plugin:memory"));
        assertTrue(actual.contains("teaneko.core:runtime"));
        assertFalse(actual.stream().anyMatch(key -> key.startsWith("teaneko.core:plugins.")));
        var policy = files.requireCurrent(WebSecurityFileConfig.class).snapshot();
        assertTrue(policy.allowsManagementResource("teaneko.core:plugins.catalog"));
        assertTrue(policy.allowsManagementResource("teaneko.core:plugins.change"));
        assertTrue(policy.allowsFileSection("new-plugin.settings"));
        assertTrue(policy.allowsConsolePrefix("/new-plugin"));
    }

    /**
     * 旧插件只读资源不意味着已授权新启停用例，也不通过扩展页面目录间接增加权限。
     * @throws Exception 当临时配置创建失败时
     */
    @Test
    void oldPluginInspectionDoesNotGrantManagement() throws Exception {
        var files = files("management-resources: ['teaneko.core:plugins']\n");
        new PluginConsoleMigration(files, plugins(), List.of()).run(new DefaultApplicationArguments());
        assertEquals(List.of("teaneko.core:plugins"),
                files.requireCurrent(WebSecurityFileConfig.class).snapshot().managementResources());
    }

    /**
     * @return 测试拥有的普通插件目录
     */
    private static PluginRegistry plugins() {
        return new PluginRegistry(List.of(() -> new PluginDescriptor("sample.plugin", "1", 1, Set.of())));
    }

    /**
     * @param permissions 原配置白名单
     * @return 使用原解析器、真实类型锁与原子移动的文件服务
     * @throws Exception 当临时文件创建失败时
     */
    private FileConfigService files(String permissions) throws Exception {
        var parsers = mock(FileTypeScanner.class);
        when(parsers.get(FileConfigType.YAML)).thenReturn(new YAMLFileTypeParser(new MockEnvironment()));
        var classes = mock(IClassScanner.class);
        when(classes.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class)).thenReturn(Map.of(
                WebSecurityFileConfig.class, WebSecurityFileConfig.class.getAnnotation(FileConfig.class)));
        var config = root.resolve("config");
        Files.createDirectories(config.resolve("web"));
        Files.writeString(config.resolve("web/security.yml"), "enabled: false\nadmin-token: 仅测试秘密\n"
                + "unknown-setting: 仅测试使用的值\n" + permissions);
        var service = new FileConfigService(parsers, new FileConfigScanner(classes), mock(ILogger.class), config);
        service.reload();
        return service;
    }

    /**
     * 测试页面贡献。
     * @param pluginId 所属插件
     * @param pages 页面声明
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Contribution(String pluginId, List<PluginConsolePage> pages) implements IPluginConsoleContribution {}

    /**
     * 测试权限迁移贡献。
     * @param pluginId 新资源归属
     * @param resourceMappings 精确映射
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Migration(String pluginId, Map<String, String> resourceMappings)
            implements IManagementResourceMigration {}
}
