package org.zexnocs.teanekoapp.web.settings;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.zexnocs.teanekocore.database.configdata.ConfigEditConflictException;

import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 设置异步失败使用稳定 HTTP 语义，不把 SQL、原配置或异常链传给浏览器。
 * <br>2026/11/03：类型化管理路由复用安全错误映射，绑定错误返回参数无效。
 * <br>2026/11/03：文件版本冲突使用相同安全映射，不返回文件或秘密原值。
 * <br>2026/11/03：指令台继续使用固定脱敏错误，不向浏览器返回平台协议或异常链。
 * <br>2026/11/03：网页聊天、媒体与成员接口同样使用原脱敏错误边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：机器令牌专用接口沿用安全错误映射，不回显原文或秘密。
 * <br>2026/11/04：已关闭资料、过期绑定及暂不可用状态使用明确的安全响应。
 * <br>2026/11/04：指令说明及模型样板接口复用同一脱敏错误边界。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.5-l
 */
// 2026/11/03：聊天、领域管理及文件入口使用相同的脱敏失败合同。
@RestControllerAdvice(assignableTypes = {ChatSettingsController.class,
        org.zexnocs.teanekoapp.web.WebManagementController.class,
        // 2026/11/04：只读扩展目录同样不向网页返回原异常或秘密。
        org.zexnocs.teanekoapp.web.WebCommandCatalogController.class,
        org.zexnocs.teanekoagent.adapter.web.ModelConfigurationController.class,
        org.zexnocs.teanekoapp.web.files.WebFileSettingsController.class,
        org.zexnocs.teanekoapp.web.files.MachineTokenController.class,
        org.zexnocs.teanekoapp.web.console.WebConsoleCommandController.class,
        org.zexnocs.teanekoapp.web.chat.WebChatController.class})
public final class ChatSettingsErrors {
    /**
     * @param failure 原异步或参数错误
     * @return 安全固定说明与明确冲突状态
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception failure) {
        Throwable root = failure;
        for (int i = 0; i < 8 && (root instanceof CompletionException || root instanceof ExecutionException)
                && root.getCause() != null; i++) {
            root = root.getCause();
        }
        // 2026/11/04：功能关闭是可操作的业务状态，不再映射为不明服务器错误。
        if (root instanceof org.zexnocs.teanekoagent.agent.prompt.AgentPromptException prompt) {
            return switch (prompt.reason()) {
                case DISABLED -> error(409, "AGENT_FEATURE_DISABLED",
                        "此聊天尚未登记或启用 Agent 相关功能，请先在聊天配置中登记并启用。");
                case CHANGED -> error(409, "AGENT_REFERENCE_CHANGED", "资料绑定或代次已变化，请重新读取。");
                case UNAVAILABLE -> error(503, "AGENT_REFERENCE_UNAVAILABLE", "资料暂不可用，请检查人格或记忆配置。");
                case INPUT_TOO_LARGE -> error(400, "AGENT_INPUT_TOO_LARGE", "本次输入超过资料容量限制，请缩短内容。");
            };
        }
        // 2026/11/03：文件版本冲突同样禁止自动覆盖。
        if (root instanceof ConfigEditConflictException || root instanceof ConcurrentModificationException) {
            return error(409, "CONFIG_CONFLICT", "配置已被其他操作修改。请刷新后重新确认，未自动覆盖。");
        }
        if (root instanceof SecurityException) {
            return error(403, "SETTINGS_FORBIDDEN", "当前会话或配置区授权已经失效。");
        }
        if (root instanceof NoSuchElementException) {
            return error(404, "TARGET_NOT_FOUND", "聊天目标或操作回执不存在，请刷新目录。");
        }
        if (root instanceof IllegalArgumentException
                // 2026/11/03：显式类型绑定失败属于请求错误，不返回解析正文。
                || root instanceof tools.jackson.core.JacksonException
                || root instanceof org.springframework.http.converter.HttpMessageNotReadableException
                || root instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException) {
            return error(400, "SETTINGS_INVALID", "字段值或操作参数无效，请检查字段说明。");
        }
        if (root instanceof IllegalStateException || root instanceof UnsupportedOperationException) {
            return error(409, "SETTINGS_UNAVAILABLE", "管理依赖或操作状态不可用，请刷新并检查当前状态后再试。");
        }
        return error(500, "SETTINGS_FAILED", "操作未完成。若已提交保存请求，可使用原操作标识查询真实结果。");
    }

    /**
     * @param status 明确状态码
     * @param code 稳定错误码
     * @param message 固定中文说明
     * @return 不含原异常的响应
     */
    private static ResponseEntity<Map<String, String>> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
