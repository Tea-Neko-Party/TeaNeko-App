package org.zexnocs.teanekocore.reload;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IReloadable;
import org.zexnocs.teanekocore.reload.interfaces.IReloadService;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 将所有的 IReloadable 实现类进行管理，并提供一个统一的 reload 方法来调用它们的 reload 方法。
 *
 * 修改记录：<br>
 * 2026/11/05：初始化失败的扫描器仍保留在重载目录，修复配置后可由显式重扫恢复。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.2
 * @version 4.8.2-l
 */
@Service
public class ReloadService implements IReloadService {
    /// 管理所有 IReloadable 实现类的集合。
    private final List<IReloadable> reloadableSet;
    private final IBeanScanner iBeanScanner;
    private final ILogger logger;

    public ReloadService(IBeanScanner iBeanScanner, ILogger logger) {
        this.reloadableSet = new CopyOnWriteArrayList<>();
        this.iBeanScanner = iBeanScanner;
        this.logger = logger;
    }

    /**
     * 添加一个 IReloadable 实现类到管理列表中。
     * 2026/11/05：先登记再初始化，初始化失败仍允许后续显式重载，不由业务查询重试发现。
     * @param reloadable 需要添加的 IReloadable 实现类。
     * @throws RuntimeException 当初始化失败时，已登记对象仍保留供显式重载
     */
    @Override
    public void addReloadable(IReloadable reloadable) {
        this.reloadableSet.add(reloadable);
        reloadable.init();
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
        // 根据 reloadable 的优先级进行排序，优先级高的先添加
        beanPairs.entrySet().stream().sorted((e1, e2) -> {
            var p1 = e1.getValue().getPriority();
            var p2 = e2.getValue().getPriority();
            return Integer.compare(p2, p1);
        }).forEachOrdered(beanPair -> {
            var bean = beanPair.getValue();
            try {
                addReloadable(bean);
            } catch (Exception e) {
                // 如果添加失败，记录错误日志
                logger.errorWithReport(this.getClass().getSimpleName(), """
                        bean %s 扫描出现异常""".formatted(iBeanScanner.getBeanClass(bean).getName()), e);
            }
        });
    }
}
