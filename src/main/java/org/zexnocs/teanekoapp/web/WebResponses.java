package org.zexnocs.teanekoapp.web;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证边界只返回固定安全诊断，不导出异常、凭据或服务对象。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
final class WebResponses {
    /** 禁止实例化响应工具。 */
    private WebResponses() {}

    /**
     * @param response 当前响应
     * @param mapper 原 JSON 转换器
     * @param status 明确状态码
     * @param value 安全 DTO
     * @throws IOException 当传输已经失败时
     */
    static void json(HttpServletResponse response, ObjectMapper mapper, int status, Object value) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(mapper.writeValueAsString(value));
    }

    /**
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param code 稳定错误码
     * @param message 固定中文说明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    record Error(String code, String message) {}
}
