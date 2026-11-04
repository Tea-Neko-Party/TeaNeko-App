package org.zexnocs.teanekoapp.web.error;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明使用管理员控制台固定错误响应的控制器，不改变原认证、授权或防伪要求。
 * 上层控制器主动标记，App 不维护具体客户端或业务控制器清单。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WebManagedEndpoint {
}
