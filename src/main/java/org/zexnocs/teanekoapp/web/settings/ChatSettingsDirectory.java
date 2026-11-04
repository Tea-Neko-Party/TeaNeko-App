package org.zexnocs.teanekoapp.web.settings;

import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 自动投影已启用的配置管理器；专用适配器覆盖同一管理器，重复声明明确失败。
 * 首次管理请求才扫描，避免配置目录装配期间提前实例化业务插件。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@Service
public final class ChatSettingsDirectory {
    private final IBeanScanner scanner;
    private final ObjectMapper mapper;
    private @Nullable Map<String, Entry> entries;

    /**
     * @param scanner 原注解扫描器
     * @param mapper 原配置持久化转换器
     */
    public ChatSettingsDirectory(IBeanScanner scanner, ObjectMapper mapper) {
        this.scanner = scanner;
        this.mapper = mapper;
    }

    /** @return 不可变配置目录，启用与授权仍在每次调用检查 */
    public synchronized Map<String, Entry> entries() {
        if (entries != null) {
            return entries;
        }
        var found = new LinkedHashMap<String, Entry>();
        scanner.getBeansWithAnnotation(ConfigManager.class).values().forEach(pair -> {
            var manager = pair.first();
            if (manager.enabled()) {
                validate(manager.value(), "配置管理器");
                var entry = new Entry(manager.value(), "general", scanner.getBeanClass(pair.second()),
                        new GenericChatSettingsSection(manager, mapper));
                if (found.putIfAbsent(manager.value(), entry) != null) {
                    throw new IllegalArgumentException("配置管理器标识重复：" + manager.value());
                }
            }
        });
        var dedicated = new AnnotatedBeanRegistry<>(scanner, TeaNekoChatSettings.class,
                IChatSettingsSection.class, declaration -> {
                    validate(declaration.value(), "配置区");
                    validate(declaration.group(), "配置分组");
                    if (declaration.version() != 1) {
                        throw new IllegalArgumentException("配置区版本无效。");
                    }
                    return declaration.value();
                });
        var overridden = new java.util.HashSet<String>();
        for (var item : dedicated.entries().entrySet()) {
            var bean = item.getValue().bean();
            String manager = bean.manager().value();
            var original = found.get(manager);
            if (original == null || !original.section().manager().equals(bean.manager()) || !overridden.add(manager)) {
                throw new IllegalArgumentException("专用配置区必须唯一对应一个实际启用的配置管理器。");
            }
            if (!item.getKey().equals(manager) && found.containsKey(item.getKey())) {
                throw new IllegalArgumentException("专用配置区与其他管理器标识冲突。");
            }
            found.remove(manager);
            var annotation = java.util.Objects.requireNonNull(AnnotatedElementUtils.findMergedAnnotation(
                    item.getValue().type(), TeaNekoChatSettings.class));
            found.put(item.getKey(), new Entry(item.getKey(), annotation.group(), item.getValue().type(), bean));
        }
        entries = Map.copyOf(found);
        return entries;
    }

    /**
     * @param id 稳定键
     * @param kind 中文声明类别
     * @throws IllegalArgumentException 当键不满足界面路由合同
     */
    private static void validate(String id, String kind) {
        // 2026/11/04：保留既有管理器的大小写标识，不重命名数据库域或插件 Bean。
        if (!id.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(kind + "标识无效。");
        }
    }

    /**
     * 配置来源与元数据，不作为浏览器响应导出。
     *
     * @param id 配置区稳定键
     * @param group 稳定分组
     * @param owner 所属原 Bean 类型
     * @param section 专用或自动投影
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    @com.fasterxml.jackson.annotation.JsonIgnoreType
    public record Entry(String id, String group, Class<?> owner, IChatSettingsSection section) {}
}
