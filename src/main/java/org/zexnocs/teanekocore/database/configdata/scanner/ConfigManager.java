package org.zexnocs.teanekocore.database.configdata.scanner;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.api.default_config.BooleanDefaultConfigData;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置管理器注解，用于标记一个类为配置管理器。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigManager {
    /**
     * @return 配置管理器的名字
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();

    /**
     * @return 配置管理器的描述信息。
     */
    String description();

    /**
     * config 所属的 namespace。
     */
    String[] namespaces();

    /**
     * 配置数据类型。
     * 用于自定义配置。
     * 默认值为 Boolean.class，表示不需要配置。
     * @return 规则配置类型
     */
    Class<? extends IConfigData> configType() default BooleanDefaultConfigData.class;

    /**
     * @return 规则总开关
     */
    boolean enabled() default true;
}
