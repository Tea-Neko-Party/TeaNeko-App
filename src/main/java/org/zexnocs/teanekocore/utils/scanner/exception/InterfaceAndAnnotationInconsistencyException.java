package org.zexnocs.teanekocore.utils.scanner.exception;

import org.springframework.beans.factory.BeanDefinitionStoreException;

/**
 * 当接口和注解标注的 Bean 不一致时抛出该异常。
 * 说明开发者在使用某个注解标注 Bean 时没有实现该注解指定的接口。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public class InterfaceAndAnnotationInconsistencyException extends BeanDefinitionStoreException {
    public InterfaceAndAnnotationInconsistencyException(String message) {
        super(message);
    }
}
