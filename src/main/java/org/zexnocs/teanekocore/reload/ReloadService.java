package org.zexnocs.teanekocore.reload;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IReloadable;
import org.zexnocs.teanekocore.reload.interfaces.IReloadService;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将所有的 IReloadable 实现类进行管理，并提供一个统一的 reload 方法来调用它们的 reload 方法。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Service
public class ReloadService implements IReloadService {
    /// 管理所有 IReloadable 实现类的集合。
    private final Set<IReloadable> reloadableSet;
    private final IBeanScanner iBeanScanner;
    private final ILogger logger;

    public ReloadService(IBeanScanner iBeanScanner, ILogger logger) {
        this.reloadableSet = ConcurrentHashMap.newKeySet();
        this.iBeanScanner = iBeanScanner;
        this.logger = logger;
    }

    /**
     * 添加一个 IReloadable 实现类到管理列表中。
     * 并调用一次该实现类的 init 方法，以确保它在添加时已经被正确加载。
     * @param reloadable 需要添加的 IReloadable 实现类。
     */
    @Override
    public void addReloadable(IReloadable reloadable) {
        reloadable.init();
        this.reloadableSet.add(reloadable);
    }

    /**
     * 调用所有 IReloadable 实现类的 reload 方法。
     */
    @Override
    public void reloadAll() {
        for (IReloadable reloadable : this.reloadableSet) {
            try {
                reloadable.reload();
            } catch (Exception e) {
                // 如果 reload 失败，记录错误日志
                logger.errorWithReport(this.getClass().getSimpleName(), """
                        reloadable %s reload 出现异常""".formatted(reloadable.getClass().getName()), e);
            }
        }
    }

    /**
     * 在服务初始化时扫描所有 IReloadable 实现类，并将它们添加到管理列表中。
     */
    @PostConstruct
    public void init() {
        __scan();
    }

    /**
     * 扫描所有 IReloadable 实现类，并将它们添加到管理列表中。
     */
    private synchronized void __scan() {
        // 清理之前的扫描结果
        reloadableSet.clear();

        // 获取所有 IReloadable 实现类的 Bean，并将它们添加到管理列表中
        var beanPairs = iBeanScanner.getBeansOfType(IReloadable.class);
        for(var beanPair: beanPairs.entrySet()) {
            var bean = beanPair.getValue();
            try {
                addReloadable(bean);
            } catch (Exception e) {
                // 如果添加失败，记录错误日志
                logger.errorWithReport(this.getClass().getSimpleName(), """
                        bean %s 扫描出现异常""".formatted(iBeanScanner.getBeanClass(bean).getName()), e);
            }
        }
    }
}
