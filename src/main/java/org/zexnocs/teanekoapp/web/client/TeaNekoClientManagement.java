package org.zexnocs.teanekoapp.web.client;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为已有 Spring Bean 声明可视化客户端标识，不代替真实连接和权限检查。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeaNekoClientManagement {
    /** @return 对应实际 clientId 的唯一键 */
    String value();

    /** @return 当前支持的管理适配契约版本 */
    int version() default 1;
}
