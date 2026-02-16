package org.zexnocs.teanekocore.database.itemdata.metadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 物品元数据注解
 * 用于注册元数据，从而使得 value → metadata class 映射生效
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ItemMetadata {
    /**
     * 元数据标识
     * @return 标识字符串
     */
    String value();
}
