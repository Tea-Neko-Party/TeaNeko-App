package org.zexnocs.teanekoapp.plugin;

import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * 通过原文件配置锁和原子替换保存插件意愿，普通目录查询只读取缓存。
 * <br>2026/11/07：仅首次导入旧禁用列表，预写回执在真实替换前取得候选版本。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginStateStore {
    private final IFileConfigService files;
    private final ObjectMapper mapper;
    private final PluginRegistry plugins;
    private final Environment environment;
    private volatile @Nullable Snapshot current;

    /**
     * 借用文件配置服务，不在构造阶段访问尚未初始化的扫描器。
     * @param files 原文件配置服务
     * @param mapper 原类型转换器
     * @param plugins 原插件目录
     * @param environment 旧启动配置来源
     */
    public PluginStateStore(IFileConfigService files, ObjectMapper mapper, PluginRegistry plugins,
                            Environment environment) {
        this.files = files;
        this.mapper = mapper;
        this.plugins = plugins;
        this.environment = environment;
    }

    /**
     * 初始化持久意愿；默认文件不会阻止首次导入旧禁用列表。
     */
    public synchronized void initialize() {
        if (current != null) {
            return;
        }
        current = files.withDocument(PluginSettings.class, document -> {
            var disk = document.read();
            var settings = mapper.treeToValue(document.tree(disk), PluginSettings.class);
            validate(settings);
            if (!settings.isMigrated()) {
                var values = new TreeMap<>(settings.getEnabled());
                var disabled = Binder.get(environment)
                        .bind("tea-neko.plugins.disabled", Bindable.listOf(String.class)).orElse(List.of());
                disabled.forEach(id -> values.putIfAbsent(id, false));
                settings.setEnabled(values);
                settings.setMigrated(true);
                validate(settings);
                var content = document.encode(mapper.valueToTree(settings));
                var parsed = document.parse(content);
                try (var prepared = document.prepare(content)) {
                    prepared.commit(disk.revision(), () -> { });
                    document.publish(prepared.revision(), parsed, () -> { });
                    return new Snapshot(prepared.revision(), values);
                }
            }
            return new Snapshot(disk.revision(), settings.getEnabled());
        });
    }

    /**
     * 返回已加载的完整意愿，不在管理轮询中读取文件。
     * @return 缓存快照
     * @throws IllegalStateException 当启动初始化尚未完成时
     */
    public Snapshot snapshot() {
        var value = current;
        if (value == null) {
            throw new IllegalStateException("插件启用配置尚未初始化。");
        }
        return value;
    }

    /**
     * 仅在显式操作失败恢复时核对磁盘事实，目录轮询不调用此方法。
     * @return 校验后的实际文件状态
     */
    public synchronized Snapshot refresh() {
        current = files.withDocument(PluginSettings.class, document -> {
            var disk = document.read();
            var settings = mapper.treeToValue(document.tree(disk), PluginSettings.class);
            validate(settings);
            return new Snapshot(disk.revision(), settings.getEnabled());
        });
        return current;
    }

    /**
     * 保存一个明确意愿，保留其他插件及暂未安装插件的原配置。
     * @param id 目标插件
     * @param enabled 目标意愿
     * @param expected 原文件版本
     * @param guard 实际提交前的管理授权
     * @param preparedReceipt 原子替换前写入含计划版本的回执
     * @return 已提交的新快照
     */
    public synchronized Snapshot save(String id, boolean enabled, String expected, Runnable guard,
                                       Consumer<String> preparedReceipt) {
        // 2026/11/07：持久化开关只接受业务插件，宿主资源身份不能作为配置目标。
        plugins.descriptor(id);
        return files.withDocument(PluginSettings.class, document -> {
            var original = document.read();
            if (!original.revision().equals(expected)) {
                throw new ConcurrentModificationException("插件配置已改变，请重新读取目录。");
            }
            var settings = mapper.treeToValue(document.tree(original), PluginSettings.class);
            validate(settings);
            var next = new TreeMap<>(settings.getEnabled());
            next.put(id, enabled);
            settings.setEnabled(next);
            settings.setMigrated(true);
            validate(settings);
            byte[] content = document.encode(mapper.valueToTree(settings));
            var parsed = document.parse(content);
            try (var prepared = document.prepare(content)) {
                guard.run();
                preparedReceipt.accept(prepared.revision());
                prepared.commit(original.revision(), guard);
                document.publish(prepared.revision(), parsed, () -> { });
                current = new Snapshot(prepared.revision(), next);
                return current;
            }
        });
    }

    /**
     * 对文件输入执行结构和不可停用模块检查，不因未知旧插件丢弃其意愿。
     * @param settings 已解析配置
     */
    private void validate(PluginSettings settings) {
        if (settings.getVersion() != 1 || settings.getEnabled() == null || settings.getEnabled().size() > 4096) {
            throw new IllegalArgumentException("插件启用配置结构或版本无效。");
        }
        settings.getEnabled().forEach((id, enabled) -> {
            PluginDescriptor.requireId(id);
            if (enabled == null) {
                throw new IllegalArgumentException("插件启用意愿不能为空：" + id);
            }
            // 2026/11/07：兼容旧文件中的启用记录，但拒绝关闭独立宿主能力。
            if (plugins.isHostCapability(id) && !enabled) {
                throw new IllegalArgumentException("宿主能力不能通过插件配置关闭：" + id);
            }
        });
        for (var view : plugins.list()) {
            if (view.descriptor().mandatory() && Boolean.FALSE.equals(settings.getEnabled().get(view.descriptor().id()))) {
                throw new IllegalArgumentException("宿主基础模块不能关闭：" + view.descriptor().id());
            }
        }
    }

    /**
     * 完整文件意愿快照，未显式关闭的新内置插件保持原行为。
     * @param revision 文件身份版本
     * @param enabled 明确保存的启用意愿
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Snapshot(String revision, Map<String, Boolean> enabled) {
        /**
         * 固定配置快照。
         * @param revision 文件版本
         * @param enabled 明确意愿
         */
        public Snapshot {
            enabled = Map.copyOf(enabled);
        }
    }
}
