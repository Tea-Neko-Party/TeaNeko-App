package org.zexnocs.teanekocore.reload;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.reload.api.IReloadable;
import org.zexnocs.teanekocore.reload.interfaces.IReloadService;
import org.zexnocs.teanekocore.utils.bean_scanner.IBeanScanner;

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

    public ReloadService(IBeanScanner iBeanScanner) {
        this.reloadableSet = ConcurrentHashMap.newKeySet();
        this.iBeanScanner = iBeanScanner;
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
            reloadable.reload();
        }
    }

    /**
     * 在服务初始化时扫描所有 IReloadable 实现类，并将它们添加到管理列表中。
     */
    @PostConstruct
    public void init() {
        scan();
    }

    /**
     * 扫描所有 IReloadable 实现类，并将它们添加到管理列表中。
     */
    public synchronized void scan() {
        // 清理之前的扫描结果
        reloadableSet.clear();

        // 获取所有 IReloadable 实现类的 Bean，并将它们添加到管理列表中
        var beans = iBeanScanner.getBeansOfType(IReloadable.class);
        for(var bean: beans.entrySet()) {
            addReloadable(bean.getValue());
        }
    }
}
