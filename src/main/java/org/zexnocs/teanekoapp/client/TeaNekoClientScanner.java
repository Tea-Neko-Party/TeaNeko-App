package org.zexnocs.teanekoapp.client;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekocore.framework.pair.IPair;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布带客户端声明的单例目录，动态连接和启用资格仍由调用方复核。
 * <br>2026/11/06：拒绝重复标识；完整重扫成功后原子替换，失败保留原快照。
 * <br>2026/11/07：拒绝重复客户端声明，重扫失败保留原目录。
 * <br>2026/11/07：安装目录与有效目录分离，缓存插件归属并实时检查客户端资格。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 * @version 4.9.0-l
 */
@Service
public class TeaNekoClientScanner implements IScanner {
    private volatile @Nullable Installed snapshot;
    private final IBeanScanner beans;
    private @Nullable PluginRegistry plugins;

    /**
     * 安装运行门禁，独立扫描器测试仍可只验证静态目录。
     * @param plugins 宿主插件目录
     */
    @Autowired
    public void installPlugins(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /** @param beans 只在初始化和显式重扫中调用的 Bean 发现设施 */
    public TeaNekoClientScanner(IBeanScanner beans) {
        this.beans = beans;
    }

    /**
     * @return 不可变客户端快照
     * @throws IllegalStateException 当目录尚未成功初始化时
     */
    public Map<String, IPair<TeaNekoClient, ITeaNekoClient>> getHandlerMap() {
        var current = installed();
        var installed = current.handlers();
        var registry = plugins;
        if (registry == null) {
            return installed;
        }
        var available = new LinkedHashMap<String, IPair<TeaNekoClient, ITeaNekoClient>>();
        installed.forEach((id, value) -> {
            String owner = current.pluginOwners().get(id);
            if (owner == null || registry.isEnabled(owner)) {
                available.put(id, value);
            }
        });
        return Map.copyOf(available);
    }

    /**
     * 启动路由和插件依赖解析读取安装事实，不以插件临时停用删除客户端声明。
     * @return 已完整发布的安装目录
     * @throws IllegalStateException 当初始化尚未成功时
     */
    public Map<String, IPair<TeaNekoClient, ITeaNekoClient>> getInstalledHandlerMap() {
        return installed().handlers();
    }

    /**
     * 同一快照同时包含声明与归属，显式重扫不会公开半成品。
     * @return 完整安装事实
     * @throws IllegalStateException 当目录尚未就绪时
     */
    public Installed installed() {
        var current = snapshot;
        if (current == null) {
            throw new IllegalStateException("客户端目录尚未就绪，请完成初始化或显式重扫。");
        }
        return current;
    }

    /**
     * 查询初始化时解析的客户端归属，不扫描 Bean 或连接。
     * @param id 客户端标识
     * @return 插件标识；宿主官方客户端未声明时为空
     */
    public @Nullable String pluginOwner(String id) {
        return installed().pluginOwners().get(id);
    }

    /**
     * @param key 客户端标识
     * @return 当前快照中的声明和单例，不存在时为空
     */
    public @Nullable IPair<TeaNekoClient, ITeaNekoClient> getPair(String key) {
        var current = installed();
        String owner = current.pluginOwners().get(key);
        return owner == null || plugins == null || plugins.isEnabled(owner) ? current.handlers().get(key) : null;
    }

    /** 首次成功后不重复发现，失败仅由初始化或显式重扫恢复。 */
    @Override
    public synchronized void init() {
        if (snapshot == null) {
            reload();
        }
    }

    /**
     * 完整验证后一次发布；不清空读者正在使用的旧快照。
     * @throws IllegalArgumentException 当标识为空或有重复声明时
     */
    @Override
    public synchronized void reload() {
        var next = new LinkedHashMap<String, IPair<TeaNekoClient, ITeaNekoClient>>();
        var owners = new LinkedHashMap<String, String>();
        var nextPluginOwners = new LinkedHashMap<String, String>();
        for (var entry : beans.getBeansWithAnnotationAndInterface(TeaNekoClient.class, ITeaNekoClient.class).entrySet()) {
            var pair = entry.getValue();
            if (!pair.first().enabled()) {
                continue;
            }
            String id = pair.first().value();
            if (id.isBlank()) {
                throw new IllegalArgumentException("客户端标识为空，来源=" + entry.getKey());
            }
            var previous = owners.putIfAbsent(id, entry.getKey());
            if (previous != null) {
                throw new IllegalArgumentException("客户端标识冲突：" + id + "，来源=" + previous + "、" + entry.getKey());
            }
            next.put(id, pair);
            // 2026/11/07：归属仅在低频发现阶段解析，实际查询读取已发布映射。
            var ownership = AnnotatedElementUtils.findMergedAnnotation(org.springframework.aop.support.AopUtils.getTargetClass(pair.second()),
                    PluginOwned.class);
            if (ownership != null) {
                if (plugins != null) {
                    plugins.requireRegistered(ownership.value());
                }
                nextPluginOwners.put(id, ownership.value());
            }
        }
        snapshot = new Installed(next, nextPluginOwners);
    }

    /**
     * 客户端声明与归属的同代不可变快照。
     * @param handlers 已安装客户端
     * @param pluginOwners 显式插件归属，官方未声明客户端不在此表
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Installed(Map<String, IPair<TeaNekoClient, ITeaNekoClient>> handlers,
                            Map<String, String> pluginOwners) {
        /**
         * 固定完整发现结果。
         * @param handlers 客户端声明
         * @param pluginOwners 插件归属
         */
        public Installed {
            handlers = Map.copyOf(handlers);
            pluginOwners = Map.copyOf(pluginOwners);
        }
    }
}
