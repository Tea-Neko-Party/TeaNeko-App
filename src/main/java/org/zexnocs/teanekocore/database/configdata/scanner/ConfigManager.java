package org.zexnocs.teanekocore.database.configdata.scanner;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;
import org.zexnocs.teanekocore.database.configdata.api.default_config.BooleanDefaultConfigData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 配置管理器注解，用于标记一个类为配置管理器。
 *
 * 修改记录：<br>
 * 2026/11/04：独立声明简短显示名称，稳定配置标识和完整说明保持原契约。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.7.4-l
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
     * 2026/11/04：供管理界面直接展示，不参与配置键、Bean 名称或权限判定。
     *
     * @return 简短中文名称，旧声明留空时使用说明首行
     */
    String displayName() default "";

    /**
     * @return 配置管理器的描述信息。
     */
    String description();

    /** @return 配置所属的数据实体，默认保留既有通用配置表 */
    Class<? extends BaseEasyDataObject> storage() default GeneralEasyDataObject.class;

    /**
     * config 所属的 namespace。
     * @return 允许配置使用的作用域命名空间
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
     * 域检测器。
     * <br>要求该检测器是 bean 且能搜索出来。
     * <br>如果不是 bean 则应当能由无参构造器创建实例。
     * <br>如果默认 {@link IConfigFieldChecker} 接口则没有任何检测器。
     * @return 字段检测器类型
     */
    Class<? extends IConfigFieldChecker> fieldChecker() default IConfigFieldChecker.class;

    /**
     * @return 规则总开关
     */
    boolean enabled() default true;
}
