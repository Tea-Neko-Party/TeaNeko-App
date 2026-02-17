package org.zexnocs.teanekocore.api_response.exception;

/**
 * 当 API 的 requestData 中没有找到对应的注解时抛出此异常。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public class APIRequestAnnotationNotFoundException extends RuntimeException {
    public APIRequestAnnotationNotFoundException(String message) {
        super(message);
    }
}
