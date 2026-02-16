package org.zexnocs.teanekocore.database.itemdata.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.bimap.IBimap;
import org.zexnocs.teanekocore.framework.bimap.LockBiMap;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.bean_scanner.IBeanScanner;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 扫描出带有 ItemMetadata 注解的类
 * 用于将 type → class 的映射关系存储在 itemDataMap 中
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Service
public class ItemMetadataScanner implements IScanner {
    private final ILogger logger;

    /// type → ItemMetadata 类 的映射
    private final IBimap<String, Class<?>> type2Class = new LockBiMap<>();

    /// bean Scanner
    private final IBeanScanner beanScanner;

    /// 初始化
    private final AtomicBoolean isInit = new AtomicBoolean(false);

    @Autowired
    public ItemMetadataScanner(ILogger logger,
                               IBeanScanner beanScanner) {
        this.logger = logger;
        this.beanScanner = beanScanner;
    }

    /**
     * 热重载方法。
     */
    @Override
    public void reload() {
        __scan();
    }

    @Override
    public void init() {
        if (isInit.compareAndSet(false, true)) {
            __scan();
        }
    }

    /**
     * 扫描带有 ItemData 注解的类
     */
    private synchronized void __scan() {
        type2Class.clear();
        var beanPairs = beanScanner.getBeansWithAnnotation(ItemMetadata.class);
        for (var beanPair: beanPairs.values()) {
            var annotation = beanPair.first();
            var clazz = beanScanner.getBeanClass(beanPair.second());
            // 存储映射
            if (type2Class.containsKey(annotation.value())) {
                logger.errorWithReport("ItemDataScanner",
                        "扫描物品数据类型失败，发现重复的物品数据类型：" + annotation.value() +
                                "，类 " + clazz.getName() +
                                " 与类 " + Objects.requireNonNull(type2Class.getValue(annotation.value())).getName() + " 冲突。");
            } else {
                type2Class.put(annotation.value(), clazz);
            }
        }
    }

    /**
     * 获取物品数据类型对应的 class
     * @param type 物品数据类型
     * @return 物品数据类型对应的 class
     */
    public Class<?> getClassFromType(String type) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        return type2Class.getValue(type);
    }

    /**
     * 获取物品数据 class 对应的类型
     * @return 物品数据 class 对应的类型
     */
    public String getTypeFromClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return type2Class.getKey(clazz);
    }
}
