package org.zexnocs.teanekoclient.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekocore.command.CommandCatalog;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 从真实房间与所选成员构造只读命令目录，不接受网页自报的角色或 scope。
 * 预览消息不会持久化、分发或提供新的执行能力。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
@Service
public final class WebCommandCatalog {
    private final WebChatStore store;
    private final WebMessageFactory messages;
    private final TeaNekoCommandConverter converter;
    private final CommandCatalog commands;
    private final Executor executor;

    /**
     * @param store 原网页持久数据
     * @param messages 原平台数据映射
     * @param converter 原权限来源转换器
     * @param commands 原命令目录
     * @param executor 原业务执行域
     */
    public WebCommandCatalog(WebChatStore store, WebMessageFactory messages, TeaNekoCommandConverter converter,
                             CommandCatalog commands, @Qualifier("virtualExecutor") Executor executor) {
        this.store = store;
        this.messages = messages;
        this.converter = converter;
        this.commands = commands;
        this.executor = executor;
    }

    /**
     * @param room 固定房间引用
     * @param profile 所选成员档案，只允许本房间已存在的成员
     * @param guard 当前会话与生命周期
     * @return 原权限和客户端规则允许的目录，不代表后续执行时仍然有权
     */
    public CompletionStage<List<CommandCatalog.Entry>> list(String room, String profile, Runnable guard) {
        return store.room(room, guard).thenCompose(known -> {
            var member = known.members().stream().filter(value -> value.profile().equals(profile)).findFirst()
                    .orElseThrow(() -> new SecurityException("所选档案不属于当前房间。"));
            return store.profile(profile, guard).thenCompose(user -> {
                var preview = new WebChat.Message("command-catalog", 0, user, member.card(), member.role(), false,
                        "", List.of(new WebChat.Part("TEXT", "/")), Instant.now());
                return messages.create(known, preview, guard, () -> {
                    throw new SecurityException("命令目录预览不能用于执行或发送。");
                }).thenApplyAsync(data -> {
                    guard.run();
                    return commands.list(converter.__parse(data), guard);
                }, executor);
            });
        });
    }
}
