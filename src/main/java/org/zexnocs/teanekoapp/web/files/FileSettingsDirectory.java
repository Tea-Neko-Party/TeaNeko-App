package org.zexnocs.teanekoapp.web.files;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.plugins.PluginOwners;
import org.zexnocs.teanekocore.file_config.FileConfigScanner;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 文件配置的专用与自动投影目录；敏感类型和认证根不进入自动表单。
 * 所有项仍受本地 file-sections 白名单与原插件归属约束。
 * <br>2026/11/05：文件类型复用专用扫描器缓存，管理目录不另行扫描类路径。
 * <br>2026/11/05：查询不触发初始化或失败重试，发现仅由扫描生命周期执行。
 *
 * <br>2026/11/07：投影并缓存插件归属，保持启停与权限复核。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.9.0-l
 */
@Service
public final class FileSettingsDirectory implements IScanner {
    private final IBeanScanner scanner;
    private final FileConfigScanner classes;
    private final FileTypeScanner parsers;
    private volatile @Nullable Catalog catalog;

    /**
     * 2026/11/05：类型发现由可重扫的 Core 目录提供。
     * @param scanner 已注册 Spring 适配器
     * @param classes 原文件类型目录
     * @param parsers 原格式解析器
     */
    public FileSettingsDirectory(IBeanScanner scanner, FileConfigScanner classes, FileTypeScanner parsers) {
        this.scanner = scanner;
        this.classes = classes;
        this.parsers = parsers;
    }

    /**
     * 2026/11/05：只读取初始化时发布的目录，不由页面请求触发发现或失败重试。
     * @return 原重载服务初始化或显式重扫发布的完整目录
     * @throws IllegalStateException 当目录尚未成功初始化时
     */
    public Map<String, AnnotatedBeanRegistry.Entry<IFileSettingsAdapter>> entries() {
        var current = catalog;
        if (current == null) {
            throw new IllegalStateException("文件配置目录尚未成功初始化，请检查初始化日志并显式重扫。");
        }
        return current.entries();
    }

    /** 2026/11/05：首次初始化目录，已初始化的页面查询只读取缓存。 */
    @Override
    public synchronized void init() {
        if (catalog == null) {
            reload();
        }
    }

    /** 2026/11/05：显式重建全部管理声明，校验完成后发布，失败保留旧目录。 */
    @Override
    public synchronized void reload() {
        var found = discover();
        var owners = new LinkedHashMap<String, String>();
        found.forEach((id, entry) -> {
            var owner = PluginOwners.resolve(entry.type(), entry.bean().type());
            if (owner != null) {
                owners.put(id, owner);
            }
        });
        // 2026/11/07：实现与插件归属作为同一不可变快照发布。
        catalog = new Catalog(found, Map.copyOf(owners));
    }

    /**
     * 2026/11/05：目录重新发现晚于配置类型与解析器加载。
     * @return 低于原配置加载的初始化优先级
     */
    @Override
    public int getPriority() {
        return -100;
    }

    /**
     * 2026/11/05：只在初始化或显式重扫时构建专用与自动投影，不修改读者可见的目录。
     * @return 经全部声明校验的完整管理目录
     */
    private Map<String, AnnotatedBeanRegistry.Entry<IFileSettingsAdapter>> discover() {
        var found = new LinkedHashMap<>(new AnnotatedBeanRegistry<>(scanner, TeaNekoFileSettings.class,
                IFileSettingsAdapter.class, declaration -> {
                    if (!declaration.value().matches("[a-z][a-z0-9_.-]{0,63}") || declaration.version() != 1) {
                        throw new IllegalArgumentException("文件管理区标识或版本无效。");
                    }
                    return declaration.value();
                }).entries());
        var types = new HashSet<Class<?>>();
        for (var entry : found.values()) {
            if (entry.bean().type() == WebSecurityFileConfig.class || !types.add(entry.bean().type())) {
                throw new IllegalArgumentException("认证根不可普通编辑，同一文件类型只能有一个专用适配器。");
            }
            FileSettingsPolicy.validateSchema(entry.bean());
        }
        for (var item : classes.declarations().entrySet()) {
            var declaration = item.getValue();
            if (types.contains(item.getKey()) || declaration.sensitive()) {
                continue;
            }
            var type = item.getKey().asSubclass(IFileConfigData.class);
            if (!(parsers.get(declaration.type()) instanceof IFileDocumentParser parser)) {
                continue;
            }
            var schema = parser.schema(type);
            if (schema == null || schema.properties().isEmpty() || schema.properties().stream().anyMatch(ConfigSchema.Property::secret)) {
                continue;
            }
            String id = ("file." + (declaration.path().isBlank() ? "" : declaration.path().replace('/', '.').replace('\\', '.') + ".")
                    + declaration.value()).toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z][a-z0-9_.-]{0,63}")) {
                throw new IllegalArgumentException("文件声明不能映射为有效的管理标识。");
            }
            var description = type.getAnnotation(Description.class);
            var rules = GenericFileSettingsAdapter.rules(schema);
            if (rules.isEmpty()) {
                continue;
            }
            var adapter = new GenericFileSettingsAdapter(type, description == null ? id : description.value(), rules);
            FileSettingsPolicy.validateSchema(adapter);
            if (found.putIfAbsent(id, new AnnotatedBeanRegistry.Entry<>(id, type, adapter)) != null) {
                throw new IllegalArgumentException("文件管理标识冲突：" + id);
            }
        }
        return Map.copyOf(found);
    }

    /**
     * @param id 已授权的精确标识
     * @return 固定的专用或自动投影
     */
    public AnnotatedBeanRegistry.Entry<IFileSettingsAdapter> require(String id) {
        var value = entries().get(id);
        if (value == null) {
            throw new IllegalArgumentException("文件管理区不存在。");
        }
        return value;
    }

    /**
     * @param id 文件配置区标识
     * @return 初始化时确定的插件归属，宿主配置为空
     */
    public @Nullable String pluginId(String id) {
        entries();
        return java.util.Objects.requireNonNull(catalog).owners().get(id);
    }

    /**
     * 文件贡献与插件归属的原子快照。
     * @param entries 原贡献目录
     * @param owners 已明确声明的插件归属
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Catalog(Map<String, AnnotatedBeanRegistry.Entry<IFileSettingsAdapter>> entries,
                           Map<String, String> owners) {}
}
