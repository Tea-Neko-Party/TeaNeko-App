package org.zexnocs.teanekoapp.web.files;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 文件配置的专用与自动投影目录；敏感类型和认证根不进入自动表单。
 * 所有项仍受本地 file-sections 白名单与原插件归属约束。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@Service
public final class FileSettingsDirectory {
    private final IBeanScanner scanner;
    private final IClassScanner classes;
    private final FileTypeScanner parsers;
    private @Nullable Map<String, AnnotatedBeanRegistry.Entry<IFileSettingsAdapter>> entries;

    /**
     * @param scanner 已注册 Spring 适配器
     * @param classes 原文件类型目录
     * @param parsers 原格式解析器
     */
    public FileSettingsDirectory(IBeanScanner scanner, IClassScanner classes, FileTypeScanner parsers) {
        this.scanner = scanner;
        this.classes = classes;
        this.parsers = parsers;
    }

    /** @return 首次实际管理时建立的固定目录 */
    public synchronized Map<String, AnnotatedBeanRegistry.Entry<IFileSettingsAdapter>> entries() {
        if (entries != null) {
            return entries;
        }
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
        for (var item : classes.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class).entrySet()) {
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
        entries = Map.copyOf(found);
        return entries;
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
}
