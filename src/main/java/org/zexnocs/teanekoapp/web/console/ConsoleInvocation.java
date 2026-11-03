package org.zexnocs.teanekoapp.web.console;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.command.api.ManagementCommand;
import org.zexnocs.teanekocore.command.interfaces.IManagedCommandSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 由指令台宿主铸造的单次管理来源，仅暴露受限文本输出和固定目标发送，不提供原生消息替身。
 * 插件必须等待返回阶段，并在自己的其他副作用前调用 checkActive。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@JsonIgnoreType
public final class ConsoleInvocation implements IManagedCommandSource {
    private final ClientConsole.Lease lease;
    private final Runnable guard;
    private final java.util.function.Function<Method, Runnable> commandGuards;
    private @Nullable Runnable commandGuard;
    private final List<String> output = new ArrayList<>();
    private final List<String> confirmed = new ArrayList<>();
    private @Nullable Method method;
    private boolean closed;
    private int sends;
    private int characters;

    /**
     * @param lease 原平台固定连接与目标
     * @param guard 本次会话、白名单和生命周期
     * @param commandGuards 首次分发时固定原命令插件代次
     */
    ConsoleInvocation(ClientConsole.Lease lease, Runnable guard, java.util.function.Function<Method, Runnable> commandGuards) {
        this.lease = lease;
        this.guard = guard;
        this.commandGuards = commandGuards;
    }

    /**
     * @param method 原目录实际方法
     * @throws SecurityException 当一个来源试图跨方法使用或当前能力失效时
     */
    @Override
    public synchronized void checkCommand(Method method) {
        if (this.method != null && !this.method.equals(method)) {
            throw new SecurityException("管理来源不能转移给其他命令方法。");
        }
        if (this.method == null) {
            this.method = method;
            commandGuard = commandGuards.apply(method);
        }
        checkActive();
    }

    /**
     * 业务自有副作用前也须复核，不以第一次分发成功代替当前授权。
     * @throws SecurityException 当来源关闭、声明缺失或当前能力失效时
     */
    public synchronized void checkActive() {
        if (closed || method == null || commandGuard == null || !method.isAnnotationPresent(ManagementCommand.class)) {
            throw new SecurityException("指令台调用来源已经失效。");
        }
        guard.run();
        commandGuard.run();
        lease.guard().run();
    }

    /** @return 仅用于人可理解的目标说明 */
    public String targetName() {
        checkActive();
        return lease.target().name();
    }

    /**
     * @param text 明确展示文本，不包含凭据或异常链
     * @return 该文本已加入本操作输出，不向真实聊天发送
     */
    public synchronized CompletionStage<Void> print(String text) {
        checkActive();
        if (text == null || text.length() > 2000 || output.size() >= 20 || characters + text.length() > 10000) {
            throw new IllegalArgumentException("指令台输出超过限制。");
        }
        output.add(text);
        characters += text.length();
        return CompletableFuture.completedStage(null);
    }

    /**
     * 发送使用原平台链和真实回执，最多五条，不重试未知结果。
     * @param text 向固定真实聊天发送的文本
     * @return 平台返回实际消息号后才完成
     * @throws SecurityException 当方法未明确声明发送能力时
     */
    public synchronized CompletionStage<Void> send(String text) {
        checkActive();
        var declaration = Objects.requireNonNull(method).getAnnotation(ManagementCommand.class);
        if (declaration == null || !declaration.sendsMessages()) {
            throw new SecurityException("该管理命令没有声明真实消息发送能力。");
        }
        if (text == null || text.isBlank() || text.length() > 2000 || ++sends > 5) {
            throw new IllegalArgumentException("指令台发送条数或文本长度超过限制。");
        }
        return lease.sender().apply(text).thenAccept(id -> {
            synchronized (this) {
                if (id == null || id.isBlank() || id.length() > 128) {
                    throw new IllegalStateException("平台没有返回有效消息标识。");
                }
                confirmed.add(id);
                checkActive();
            }
        });
    }

    /** @return 已观察输出的不可变快照，仅宿主用于保存原回执 */
    synchronized List<String> output() {
        return List.copyOf(output);
    }

    /** @return 已收到的实际平台消息号，不推定其他业务成功 */
    synchronized List<String> confirmed() {
        return List.copyOf(confirmed);
    }

    /** 宿主在物理处理链结束后关闭来源，迟到使用不得继续产生副作用。 */
    synchronized void close() {
        closed = true;
    }
}
