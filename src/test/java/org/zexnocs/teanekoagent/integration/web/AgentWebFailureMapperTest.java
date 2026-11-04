package org.zexnocs.teanekoagent.integration.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsErrors;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

/**
 * 验证真实注解发现和异步异常解包仍保留 Agent 错误语义，未知异常不泄露原文。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
class AgentWebFailureMapperTest {
    /** 使用独立容器扫描业务映射，不连接数据库、平台或模型。 */
    @Test
    void preservesRegisteredFailureSemanticsAndRedactsUnknownFailures() {
        try (var context = new AnnotationConfigApplicationContext(AgentWebFailureMapper.class)) {
            var errors = new ChatSettingsErrors(new BeanScanner(context, mock(ILogger.class)));
            var expected = Map.of(
                    AgentPromptException.Reason.DISABLED, Map.entry(409, "AGENT_FEATURE_DISABLED"),
                    AgentPromptException.Reason.CHANGED, Map.entry(409, "AGENT_REFERENCE_CHANGED"),
                    AgentPromptException.Reason.UNAVAILABLE, Map.entry(503, "AGENT_REFERENCE_UNAVAILABLE"),
                    AgentPromptException.Reason.INPUT_TOO_LARGE, Map.entry(400, "AGENT_INPUT_TOO_LARGE"));
            expected.forEach((reason, response) -> {
                var failure = new CompletionException(new ExecutionException(new AgentPromptException(reason)));
                var result = errors.handle(failure);
                assertEquals(response.getKey(), result.getStatusCode().value());
                assertEquals(response.getValue(), Objects.requireNonNull(result.getBody()).get("code"));
            });
            var unknown = errors.handle(new RuntimeException("不应公开的凭据"));
            assertEquals(500, unknown.getStatusCode().value());
            assertFalse(Objects.requireNonNull(unknown.getBody()).toString().contains("不应公开的凭据"));
        }
    }
}
