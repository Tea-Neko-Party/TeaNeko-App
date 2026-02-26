package org.zexnocs.teanekocore.database.easydata.core;

import jakarta.persistence.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于扫描和注册所有 {@link BaseEasyDataRepository} 的子类，
 * 并根据它们的泛型参数（即实体类）进行映射，以便在运行时能够根据实体类获取对应的仓库实例。
 *
 * @see BaseEasyDataObject
 * @see BaseEasyDataRepository
 * @author zExNocs
 * @date 2026/02/15
 * @since 4.0.0
 */
@Service("easyDataRepositoryScanner")
public class EasyDataRepositoryScanner extends AbstractScanner {
    /// 仓库的实体类 → 仓库类
    private final Map<Class<? extends BaseEasyDataObject>, BaseEasyDataRepository<?>> repositories =
            new ConcurrentHashMap<>();

    /// bean scanner
    private final IBeanScanner beanScanner;

    /// 日志
    private final ILogger logger;

    @Lazy
    @Autowired
    public EasyDataRepositoryScanner(IBeanScanner beanScanner,
                                     ILogger logger) {
        this.logger = logger;
        this.beanScanner = beanScanner;
    }

    /**
     * 获取指定表名的数据仓库。
     */
    @Override
    protected synchronized void _scan() {
        repositories.clear();

        var beans = beanScanner.getBeansOfType(BaseEasyDataRepository.class);
        for (var repository: beans.values()) {
            var clazz = repository.getClass();
            // 获取 BaseEasyDataRepository 子类的泛型参数 "T"
            ResolvableType type = ResolvableType.forClass(BaseEasyDataRepository.class, clazz);
            Class<?> genericClass = type.getGeneric(0).resolve();
            if(genericClass == null) {
                // 泛型参数为 null
                logger.errorWithReport("EasyDataRepositoryScanner",
                        "类" + clazz.getName() + "没有泛型参数，无法注册到EasyDataRepositoryScanner。");
                continue;
            }

            // 该类必须是 BaseEasyDataObject 的子类
            if (BaseEasyDataObject.class.isAssignableFrom(genericClass)) {
                // 转化成 BaseEasyDataObject 类
                Class<? extends BaseEasyDataObject> entityClass;
                try {
                    entityClass = genericClass.asSubclass(BaseEasyDataObject.class);
                } catch (ClassCastException e) {
                    logger.errorWithReport("EasyDataRepositoryScanner",
                            "类" + genericClass.getName() + "不是BaseEasyDataObject的子类，无法注册到EasyDataRepositoryScanner。");
                    continue;
                }
                // 尝试获取 Table 注解来验证是否是数据库表
                Table table = entityClass.getAnnotation(Table.class);
                if (table != null) {
                    // 如果已经注册过了，则警告
                    if (repositories.containsKey(entityClass)) {
                        logger.warn("EasyDataRepositoryScanner",
                                "表" + table.name() + "已经注册到EasyDataRepositoryScanner。");
                    } else {
                        // 注册
                        repositories.put(entityClass, repository);
                    }
                } else {
                    // 没有 Table 注解
                    logger.errorWithReport("EasyDataRepositoryScanner",
                            "类" + entityClass.getName() + "没有Table注解，无法注册到EasyDataRepositoryScanner。");
                }
            } else {
                // 没有泛型参数
                logger.warn("EasyDataRepositoryScanner",
                        "类" + clazz.getName() + "没有泛型参数，无法注册到EasyDataRepositoryScanner。");
            }
        }
    }

    /**
     * 获取指定表名的数据仓库。
     * @param tableName 表名
     */
    public BaseEasyDataRepository<?> getRepository(Class<? extends BaseEasyDataObject> tableName) {
        // 如果表名为 null，则返回 null
        if (tableName == null) {
            return null;
        }
        return repositories.get(tableName);
    }

    /**
     * 获取所有表名。
     * @return 表名列表
     */
    public Collection<Class<? extends BaseEasyDataObject>> getTableNames() {
        return repositories.keySet();
    }
}
