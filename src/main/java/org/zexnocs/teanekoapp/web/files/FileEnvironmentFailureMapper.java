package org.zexnocs.teanekoapp.web.files;

import org.springframework.http.ResponseEntity;
import org.zexnocs.teanekoapp.web.error.IWebFailureMapper;
import org.zexnocs.teanekoapp.web.error.WebFailureMapping;
import org.zexnocs.teanekocore.file_config.FileConfigEnvironmentException;

import java.util.Map;

/**
 * 将 Core 已脱敏的环境配置错误映射为可修正的参数响应，不开放任意异常消息。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@WebFailureMapping(type = FileConfigEnvironmentException.class)
public final class FileEnvironmentFailureMapper implements IWebFailureMapper {
    /**
     * @param failure 已由注册表匹配的安全环境错误
     * @return 明确未通过配置解析的错误码及处理建议
     */
    @Override
    public ResponseEntity<Map<String, String>> map(Throwable failure) {
        var environment = (FileConfigEnvironmentException) failure;
        return ResponseEntity.badRequest().body(Map.of("code", "FILE_ENVIRONMENT_UNAVAILABLE",
                "message", environment.failureReason()));
    }
}
