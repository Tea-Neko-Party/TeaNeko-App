package org.zexnocs.teanekoapp.config;

import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于给 {@link ITeaNekoClient} 提供配置数据命名空间的注解。
 * <br>只有命名空间的配置数据才能被客户端查询到。
 * <br>client 提供了可以注册的命名空间列表; scope 提供了注册配置的范围。
 * <br>todo: 当前不在命名空间下的配置数据可能依然会被注册和设置，但暂时问题不大。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigNamespace {
    /**
     * general 命名空间，表示配置数据可以被所有查询到。
     */
    String[] generalNamespace();

    /**
     * 私聊命名空间，表示配置数据只能被私聊查询到。
     *
     * @return 配置数据命名空间
     */
    String[] privateNamespace();

    /**
     * 群聊命名空间，表示配置数据只能被群聊查询到。
     *
     * @return 配置数据命名空间
     */
    String[] groupNamespace();
}
