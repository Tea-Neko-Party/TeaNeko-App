package org.zexnocs.teanekoapp.web.error;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将业务异常类型声明为唯一错误映射键，实际实现由 Spring 和原扫描器管理。
 * 异常类型不作为 Bean 名称，也不接受请求传入的反射类名。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface WebFailureMapping {
    /** @return 该实现明确处理的异常类型，子类优先使用距离最近的声明 */
    Class<? extends Throwable> type();
}
