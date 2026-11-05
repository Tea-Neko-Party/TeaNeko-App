package org.zexnocs.teanekocore.file_config;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.Map;

/**
 * 缓存文件配置类型声明，普通配置查询不扫描类路径。
 * 显式重扫先构建完整目录再发布，失败时保留上一目录。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@Component
public final class FileConfigScanner implements IScanner {
    private final IClassScanner scanner;
    private volatile Map<Class<? extends IFileConfigData>, FileConfig> declarations = Map.of();
    private boolean initialized;

    /**
     * 创建单例时发现配置类型，不读取配置值或实例化业务服务。
     *
     * @param scanner 底层低频类发现入口
     */
    public FileConfigScanner(IClassScanner scanner) {
        this.scanner = scanner;
        init();
    }

    /** 首次建立目录，重载服务再次初始化时不重复扫描。 */
    @Override
    public synchronized void init() {
        if (!initialized) {
            reload();
        }
    }

    /** 显式重新发现类型，完整不可变结果构建成功后一次替换。 */
    @Override
    public synchronized void reload() {
        var found = Map.copyOf(scanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class));
        declarations = found;
        initialized = true;
    }

    /** @return 当前完整类型目录，不执行扫描或配置文件读取 */
    public Map<Class<? extends IFileConfigData>, FileConfig> declarations() {
        return declarations;
    }

    /** @return 高于配置值重载的类型发现优先级 */
    @Override
    public int getPriority() {
        return 1100;
    }
}
