package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.reload.api.IScanner;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 完整校验各层贡献后原子发布总管理说明，页面轮询只读取不可变缓存。
 * <br>2026/11/07：静态安装说明与业务启停目录分离，失败重扫保留原完整结果。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class PluginContributionDirectory implements IScanner {
    private final List<IPluginContributionSource> sources;
    private final PluginRegistry plugins;
    private volatile @Nullable Map<String, List<PluginContribution>> snapshot;

    /**
     * 由 Spring 固定来源集合，不在查询时扫描 Bean。
     * @param sources 各模块声明来源
     * @param plugins 已注册插件
     */
    public PluginContributionDirectory(List<IPluginContributionSource> sources, PluginRegistry plugins) {
        this.sources = List.copyOf(sources);
        this.plugins = plugins;
    }

    /** 首次成功后不重复建立贡献目录。 */
    @Override
    public synchronized void init() {
        if (snapshot == null) {
            reload();
        }
    }

    /**
     * 读取已初始化来源并校验全局身份，失败不发布半成品。
     * @throws IllegalArgumentException 当插件未知或同类贡献标识重复
     */
    @Override
    public synchronized void reload() {
        var found = new LinkedHashMap<String, PluginContribution>();
        for (var source : sources) {
            for (var item : source.contributions()) {
                // 2026/11/07：总管理只接收业务插件贡献，原生系统能力保留在自身目录。
                plugins.descriptor(item.pluginId());
                String key = item.pluginId() + ":" + item.kind() + ":" + item.id();
                if (found.putIfAbsent(key, item) != null) {
                    throw new IllegalArgumentException("插件贡献说明重复：" + key);
                }
            }
        }
        var next = found.values().stream().sorted(Comparator.comparing(PluginContribution::kind)
                        .thenComparing(PluginContribution::id))
                .collect(Collectors.groupingBy(PluginContribution::pluginId,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
        snapshot = Map.copyOf(next);
    }

    /** @return 晚于配置、命令和 Agent 目录的初始化顺序 */
    @Override
    public int getPriority() {
        return -1000;
    }

    /**
     * 只供插件总管理使用，不能据此开放业务入口。
     * @param pluginId 已注册插件标识
     * @return 全部静态贡献，包含当前停用项
     * @throws IllegalStateException 当初始化尚未成功
     */
    public List<PluginContribution> list(String pluginId) {
        var current = snapshot;
        if (current == null) {
            throw new IllegalStateException("插件贡献目录尚未就绪，请检查初始化日志并显式重扫。");
        }
        return current.getOrDefault(pluginId, List.of());
    }
}
