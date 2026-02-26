package org.zexnocs.teanekocore.database.itemdata.metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.bimap.IBimap;
import org.zexnocs.teanekocore.framework.bimap.LockBiMap;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.Objects;

/**
 * 扫描出带有 ItemMetadata 注解的类
 * 用于将 type → class 的映射关系存储在 itemDataMap 中
 *
 * @see ItemMetadata
 * @author zExNocs
 * @date 2026/02/16
 * @since 4.0.0
 */
@Service
public class ItemMetadataScanner extends AbstractScanner {
    private final ILogger logger;

    /// type → ItemMetadata 类 的映射
    private final IBimap<String, Class<?>> type2Class = new LockBiMap<>();

    /// 类扫描器
    private final IClassScanner iClassScanner;

    @Autowired
    public ItemMetadataScanner(ILogger logger, IClassScanner iClassScanner) {
        this.logger = logger;
        this.iClassScanner = iClassScanner;
    }

    /**
     * 扫描带有 ItemMetadata 注解的类
     */
    @Override
    protected synchronized void _scan() {
        type2Class.clear();
        var map = iClassScanner.getClassesWithAnnotation(ItemMetadata.class);
        for(var entry : map.entrySet()) {
            var annotation = entry.getValue();
            var clazz = entry.getKey();
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
     *
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
     *
     * @param clazz 物品数据 class
     * @return 物品数据 class 对应的类型
     */
    public String getTypeFromClass(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return type2Class.getKey(clazz);
    }
}
