package org.zexnocs.teanekoagent.component.llm.file_config.form;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明模型配置适配器的实际注册路由，扫描时检查唯一性和协议版本。
 *
 * 修改记录：<br>
 * 2026/11/04：扩展标识改用独立 id，避免 Spring 将业务值作为 Bean 名。
 *
 * <br>2026/11/04：声明同源图标路径，由模型扩展自主管理展示资源。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.0-l
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMConfigurationAdapter {
    /**
     * 2026/11/04：此属性仅用于扩展注册，不参与容器组件命名。
     * @return 对应实际模型适配器的注册 ID
     */
    String id();
    /**
     * 2026/11/04：图标随适配器声明，资源由插件提供，不由中央供应商分支判断。
     * @return 控制台同源图标路径，空值显示名称缩写
     */
    String icon() default "";
    /** @return 当前配置描述协议版本 */
    int version() default 1;
}

