package org.zexnocs.teanekocore.command.api;

import org.springframework.stereotype.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 向已存在的前缀命令贡献独立子命令，处理实例和插件归属仍属于贡献者。
 * <br>2026/11/07：扩展只在初始化发现，重复子命令拒绝整个目录发布。
 * <br>2026/11/07：命令前缀使用独立属性，避免 Spring 将业务路由解释为 Bean 名。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CommandExtension {
    /** @return 已注册主命令的一个稳定前缀，不参与 Spring Bean 命名。 */
    String prefix();
}
