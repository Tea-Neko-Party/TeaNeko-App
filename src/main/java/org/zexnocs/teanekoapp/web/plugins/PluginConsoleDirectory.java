package org.zexnocs.teanekoapp.web.plugins;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 初始化时构建插件页面目录，查询仅复核当前插件资格和管理资源白名单。
 * <br>2026/11/07：完整校验后原子发布，禁用插件不再贡献导航。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class PluginConsoleDirectory implements IScanner {
    private final IBeanScanner beans;
    private final PluginRegistry plugins;
    private final IFileConfigService files;
    private volatile @Nullable List<View> pages;

    /**
     * @param beans 仅用于初始化和显式重扫的发现入口
     * @param plugins 插件运行目录
     * @param files 当前本地权限
     */
    public PluginConsoleDirectory(IBeanScanner beans, PluginRegistry plugins, IFileConfigService files) {
        this.beans = beans;
        this.plugins = plugins;
        this.files = files;
    }

    /** 首次成功后不重新发现扩展。 */
    @Override
    public synchronized void init() {
        if (pages == null) {
            reload();
        }
    }

    /**
     * 完整发现和验证后发布目录。
     * @throws IllegalArgumentException 当归属未知、页面重复或模块归属不一致时
     */
    @Override
    public synchronized void reload() {
        var next = new LinkedHashMap<String, View>();
        for (var contribution : beans.getBeansOfType(IPluginConsoleContribution.class).values()) {
            var owner = contribution.pluginId();
            // 2026/11/07：独立插件页面只属于业务插件，不能借宿主身份进入导航。
            plugins.descriptor(owner);
            for (var page : contribution.pages()) {
                String key = owner + ":" + page.id();
                if (!page.moduleKey().startsWith(owner + ":")
                        || next.putIfAbsent(key, new View(key, owner, page)) != null) {
                    throw new IllegalArgumentException("插件页面标识重复或模块归属不一致：" + key);
                }
            }
        }
        pages = next.values().stream().sorted(Comparator.comparingInt((View value) -> value.page().order())
                .thenComparing(View::key)).toList();
    }

    /**
     * @return 当前可用且全部必需管理资源已在本地允许的页面
     * @throws IllegalStateException 当目录尚未初始化成功时
     */
    public List<View> list() {
        var current = installed();
        // 2026/11/07：页面与直接调用使用同一默认开放、可选限制策略。
        var policy = files.requireCurrent(WebSecurityFileConfig.class).snapshot();
        return current.stream().filter(value -> plugins.isEnabled(value.pluginId()))
                .filter(value -> value.page().managementResources().stream()
                        .allMatch(policy::allowsManagementResource)).toList();
    }

    /**
     * 2026/11/07：只向总管理静态贡献目录提供安装事实，不作为页面路由许可。
     * @return 包含停用项的完整页面声明
     * @throws IllegalStateException 当初始化尚未成功
     */
    public List<View> installed() {
        var current = pages;
        if (current == null) {
            throw new IllegalStateException("插件页面目录尚未就绪，请检查初始化日志并显式重扫。");
        }
        return current;
    }

    /**
     * 导出的页面元数据，不包含实现类型或任意资源路径。
     * @param key 页面全局键
     * @param pluginId 所属插件
     * @param page 页面声明
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record View(String key, String pluginId, PluginConsolePage page) {}
}
