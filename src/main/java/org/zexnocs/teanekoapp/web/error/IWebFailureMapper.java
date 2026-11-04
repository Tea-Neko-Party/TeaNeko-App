package org.zexnocs.teanekoapp.web.error;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 上层业务主动提供的公开错误映射，App 只按异常类型分派而不依赖业务异常。
 * 实现只返回固定状态、错误码和安全说明，不输出异常原文、凭据或堆栈。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public interface IWebFailureMapper {
    /**
     * 映射已经解包且符合本实现注解声明的异常，不执行恢复、重试或其他副作用。
     * @param failure 当前声明类型或其未单独注册的子类异常
     * @return 固定的错误 HTTP 响应，正文包含 code 与 message
     */
    ResponseEntity<Map<String, String>> map(Throwable failure);
}
