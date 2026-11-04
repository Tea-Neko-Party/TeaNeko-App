package org.zexnocs.teanekoapp.web.chat;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoclient.web.WebChat;
import org.zexnocs.teanekoclient.web.WebChatService;
import org.zexnocs.teanekoclient.web.WebChatStore;
import org.zexnocs.teanekoclient.web.WebImages;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 网页聊天仅通过原认证会话和 CSRF；图片读取、档案与消息查询同样授权。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：房间打开操作完成模块默认配置准备，沿用原会话与防伪校验。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.5-l
 */
@RestController
@RequestMapping("/api/v1/chat")
public final class WebChatController {
    /**
     * @param room 已保存的房间引用
     * @param request 当前请求
     * @param authentication 当前真实认证
     * @return 完成模块准备的房间
     */
    @PostMapping("/rooms/{room}/open")
    public CompletionStage<WebChat.RoomView> open(@PathVariable String room, HttpServletRequest request,
                                                Authentication authentication) {
        return service.open(room, sessions.capture(request, authentication));
    }

    private final WebChatService service;
    private final WebChatStore store;
    private final WebImages images;
    private final WebSessionGuard sessions;

    /**
     * @param service 原入站与身份编排
     * @param store 原持久查询
     * @param images 原有界图片处理
     * @param sessions 原真实会话复核
     */
    public WebChatController(WebChatService service, WebChatStore store, WebImages images, WebSessionGuard sessions) {
        this.service = service;
        this.store = store;
        this.images = images;
        this.sessions = sessions;
    }

    /**
     * @param request 原请求
     * @param authentication 原认证
     * @return 服务端档案、真实聊天与观察时刻
     */
    @GetMapping("/rooms")
    public CompletionStage<WebChat.Home> home(HttpServletRequest request, Authentication authentication) {
        return service.home(sessions.capture(request, authentication));
    }

    /**
     * @param body 显式创建聊天
     * @param request 原请求
     * @param authentication 原认证
     * @return 已提交安全视图
     */
    @PostMapping("/rooms")
    public CompletionStage<WebChat.RoomView> create(@RequestBody WebChat.CreateRoom body,
            HttpServletRequest request, Authentication authentication) {
        return service.create(body, sessions.capture(request, authentication));
    }

    /**
     * @param body 显式创建档案，不接收 UUID
     * @param request 原请求
     * @param authentication 原认证
     * @return 已映射到 TeaUser 的网页档案
     */
    @PostMapping("/profiles")
    public CompletionStage<WebChat.Profile> profile(@RequestBody WebChat.CreateProfile body,
            HttpServletRequest request, Authentication authentication) {
        return service.profile(body, sessions.capture(request, authentication));
    }

    /**
     * @param room 已知聊天
     * @param body 专用成员操作
     * @param request 原请求
     * @param authentication 原认证
     * @return 原子成员更新结果
     */
    @PostMapping("/rooms/{room}/members")
    public CompletionStage<WebChat.RoomView> member(@PathVariable String room, @RequestBody WebChat.ChangeMember body,
            HttpServletRequest request, Authentication authentication) {
        return service.member(room, body, sessions.capture(request, authentication));
    }

    /**
     * @param room 已知聊天
     * @param after 已读持久游标
     * @param request 原请求
     * @param authentication 原认证
     * @return 原窗口有界页，不触发 Agent
     */
    @GetMapping("/rooms/{room}/messages")
    public CompletionStage<WebChat.Page> page(@PathVariable String room, @RequestParam(defaultValue = "0") long after,
            HttpServletRequest request, Authentication authentication) {
        return store.page(room, after, sessions.capture(request, authentication));
    }

    /**
     * @param room 已知聊天
     * @param body 原消息与编号
     * @param request 原请求
     * @param authentication 原认证
     * @return 实际持久受理事实
     */
    @PostMapping("/rooms/{room}/messages")
    public CompletionStage<WebChat.Receipt> send(@PathVariable String room, @RequestBody WebChat.Send body,
            HttpServletRequest request, Authentication authentication) {
        return service.submit(room, body, sessions.capture(request, authentication));
    }

    /**
     * @param room 已知聊天
     * @param operation 原编号
     * @param request 原请求
     * @param authentication 原认证
     * @return 只读原受理状态
     */
    @GetMapping("/rooms/{room}/operations/{operation}")
    public CompletionStage<WebChat.Receipt> operation(@PathVariable String room, @PathVariable UUID operation,
            HttpServletRequest request, Authentication authentication) {
        return service.operation(room, operation, sessions.capture(request, authentication));
    }

    /**
     * @param room 已知聊天
     * @param request 原请求
     * @param authentication 原认证
     * @return 当前聊天图片号
     */
    @GetMapping("/rooms/{room}/media")
    public CompletionStage<List<String>> media(@PathVariable String room,
            HttpServletRequest request, Authentication authentication) {
        return store.images(room, sessions.capture(request, authentication));
    }

    /**
     * @param room 已知聊天
     * @param operation 原上传编号
     * @param request 原请求，先限长读取再解码
     * @param authentication 原认证
     * @return 已保存图片号；不保留上传名称和原始元数据
     * @throws IOException 当请求体读取失败时
     */
    @PostMapping(value = "/rooms/{room}/media", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public CompletionStage<Map<String, String>> upload(@PathVariable String room, @RequestParam UUID operation,
            HttpServletRequest request, Authentication authentication) throws IOException {
        var guard = sessions.capture(request, authentication);
        if (request.getContentLengthLong() > WebImages.MAX_BYTES) {
            throw new IllegalArgumentException("上传图片超过一 MiB 上限。");
        }
        byte[] body = request.getInputStream().readNBytes(WebImages.MAX_BYTES + 1);
        return images.normalize(body, guard).thenCompose(bytes -> store.upload(room, operation, bytes, guard))
                .thenApply(id -> Map.of("id", id));
    }

    /**
     * @param room 已知聊天
     * @param media 本聊天资源号
     * @param request 原请求
     * @param authentication 原认证
     * @return 不缓存的标准 PNG，读取前后均授权
     */
    @GetMapping("/rooms/{room}/media/{media}")
    public CompletionStage<ResponseEntity<byte[]>> image(@PathVariable String room, @PathVariable String media,
            HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        return store.image(room, media, guard).thenApply(bytes -> {
            guard.run();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image.png\"").body(bytes);
        });
    }

    /**
     * @param room 已知聊天
     * @param media 本聊天未引用资源
     * @param request 原请求
     * @param authentication 原认证
     * @return 已删除事实，不删除当前消息仍引用的图片
     */
    @DeleteMapping("/rooms/{room}/media/{media}")
    public CompletionStage<Map<String, String>> delete(@PathVariable String room, @PathVariable String media,
            HttpServletRequest request, Authentication authentication) {
        return store.deleteImage(room, media, sessions.capture(request, authentication)).thenApply(ignored -> Map.of("status", "DELETED"));
    }
}
