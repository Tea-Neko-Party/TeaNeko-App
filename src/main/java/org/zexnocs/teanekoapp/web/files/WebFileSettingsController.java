package org.zexnocs.teanekoapp.web.files;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zexnocs.teanekoapp.web.WebSessionGuard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 仅按受控配置区开放表单、预检、保存、应用和原回执查询，不接受任意文件路径。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@RestController
@RequestMapping("/api/v1/file-configs")
public final class WebFileSettingsController {
    private final FileSettingsService service;
    private final WebSessionGuard sessions;

    /**
     * @param service 原文档管理用例
     * @param sessions 真实会话能力
     */
    public WebFileSettingsController(FileSettingsService service, WebSessionGuard sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    /**
     * @param request 原请求
     * @param authentication 真实认证
     * @return 本地允许的文件区
     */
    @GetMapping
    public List<FileSettings.Section> list(HttpServletRequest request, Authentication authentication) {
        return service.list(sessions.capture(request, authentication));
    }

    /**
     * @param id 原区
     * @param request 原请求
     * @param authentication 真实认证
     * @return 脱敏原文档字段
     */
    @GetMapping("/{id}")
    public CompletionStage<FileSettings.View> read(@PathVariable String id, HttpServletRequest request,
            Authentication authentication) {
        return service.read(id, sessions.capture(request, authentication));
    }

    /**
     * @param id 原区
     * @param change 原版本及局部修改
     * @param request 原请求
     * @param authentication 真实认证
     * @return 仅预检结果
     */
    @PostMapping("/{id}/actions/validate")
    public CompletionStage<Map<String, String>> validate(@PathVariable String id, @RequestBody FileSettings.Change change,
            HttpServletRequest request, Authentication authentication) {
        return service.validate(id, change, sessions.capture(request, authentication));
    }

    /**
     * @param id 原区
     * @param change 固定原操作
     * @param request 原请求
     * @param authentication 真实认证
     * @return 真实磁盘结果
     */
    @PostMapping("/{id}/actions/save")
    public CompletionStage<FileSettings.Receipt> save(@PathVariable String id, @RequestBody FileSettings.Change change,
            HttpServletRequest request, Authentication authentication) {
        return service.save(id, change, sessions.capture(request, authentication));
    }

    /**
     * @param id 原区
     * @param application 固定磁盘版本的应用请求
     * @param request 原请求
     * @param authentication 真实认证
     * @return 明确应用或重启状态
     */
    @PostMapping("/{id}/actions/apply")
    public CompletionStage<FileSettings.Receipt> apply(@PathVariable String id, @RequestBody FileSettings.Application application,
            HttpServletRequest request, Authentication authentication) {
        return service.apply(id, application, sessions.capture(request, authentication));
    }

    /**
     * @param id 原区
     * @param operation 原编号
     * @param request 原请求
     * @param authentication 真实认证
     * @return 原回执，不推定失败即未保存
     */
    @GetMapping("/{id}/operations/{operation}")
    public CompletionStage<FileSettings.Receipt> operation(@PathVariable String id, @PathVariable UUID operation,
            HttpServletRequest request, Authentication authentication) {
        return service.operation(id, operation, sessions.capture(request, authentication));
    }
}
