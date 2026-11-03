package org.zexnocs.teanekoclient.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;

import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 原网页快照映射为通用消息，图片从已授权本聊天资源内联，不让模型访问私有 HTTP 凭据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class WebMessageFactory {
    private final WebChatStore store;
    private final ObjectProvider<WebTeaNekoClient> clients;

    /**
     * @param store 原持久平台窗口
     * @param clients 延迟借用已注册客户端，避免工具箱装配环
     */
    public WebMessageFactory(WebChatStore store, ObjectProvider<WebTeaNekoClient> clients) {
        this.store = store;
        this.clients = clients;
    }

    /**
     * @param room 原已解析聊天
     * @param message 原持久消息
     * @param read 当前读取授权
     * @param execution 原短暂执行能力，历史查询明确拒绝执行
     * @return 可接入原消息事件和模型图像输入的原生对象
     */
    public CompletionStage<WebMessageData> create(WebChat.Room room, WebChat.Message message, Runnable read, Runnable execution) {
        var builder = DefaultTeaNekoContentListBuilder.builder();
        CompletionStage<Void> work = CompletableFuture.completedStage(null);
        for (var part : message.parts()) {
            work = work.thenCompose(ignored -> {
                read.run();
                switch (part.kind()) {
                    case "TEXT" -> builder.addText(part.value());
                    case "AT" -> builder.addAt(part.value());
                    case "REPLY" -> builder.addReply(part.value());
                    case "IMAGE" -> {
                        return store.image(room.id(), part.value(), read).thenAccept(bytes ->
                                builder.addImage("data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)));
                    }
                    default -> throw new IllegalArgumentException("网页持久片段不受支持。");
                }
                return CompletableFuture.completedStage(null);
            });
        }
        return work.thenApply(ignored -> {
            read.run();
            return new WebMessageData(room, message, clients.getObject(), builder.build(), execution);
        });
    }
}
