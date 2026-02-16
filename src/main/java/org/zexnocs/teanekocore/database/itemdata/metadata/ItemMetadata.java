package org.zexnocs.teanekocore.database.itemdata.metadata;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 物品元数据注解
 * 用于注册元数据，从而使得 value → metadata class 映射生效
 * 因为是数据类，所以注册为 bean 的唯一作用就是被扫描到。
 * 因此需要至少一个支持注入(没有参数或者参数都是 bean)的构造函数来进行初始化，并标记 @Autowired。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ItemMetadata {
    /**
     * 元数据标识
     * @return 标识字符串
     */
    String value();
}
