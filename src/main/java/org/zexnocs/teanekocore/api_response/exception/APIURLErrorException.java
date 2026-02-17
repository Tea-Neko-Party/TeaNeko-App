package org.zexnocs.teanekocore.api_response.exception;

/**
 * API 请求的 URL 格式不正确时抛出此异常
 *
 * @author zExNocs
 * @date 2026/02/17
 */
public class APIURLErrorException extends RuntimeException {
    public APIURLErrorException(String message) {
        super(message);
    }
}
