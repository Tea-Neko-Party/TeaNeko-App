package org.zexnocs.teanekoapp.web.console;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekocore.command.interfaces.ICommandClient;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 客户端指令台的安全视图与受信能力；不传递原生用户、权限或任意平台 JSON。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/07：指令目录公开真实贡献插件，支持实时资格撤销。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
public final class ClientConsole {
    /** 禁止实例化协议命名空间。 */
    private ClientConsole() {}

    /**
     * 固定原请求，过期原文不能在回执淘汰后重新执行。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原操作编号
     * @param text 命令原文，只用于内存执行及摘要
     * @param connection 页面读取的连接代次
     * @param observedAt 服务端目录观察时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Request(UUID operationId, String text, String connection, Instant observedAt) {
        /**
         * @param operationId 原编号
         * @param text 原命令
         * @param connection 原连接代次
         * @param observedAt 服务端观察时间
         * @throws IllegalArgumentException 当正文、连接或必需参数无效时
         */
        public Request {
            Objects.requireNonNull(operationId);
            Objects.requireNonNull(observedAt);
            if (text == null || text.isBlank() || text.length() > 4096 || connection == null || connection.length() > 128) {
                throw new IllegalArgumentException("指令台请求参数无效。");
            }
        }
    }

    /**
     * 当前真实平台状态，不缓存成执行许可。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param state UNSUPPORTED、DISCONNECTED、IDENTITY_PENDING、CONFLICT 或 READY
     * @param connection 当前连接代次，不可用于认证
     * @param explanation 中文就绪说明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record State(String state, String connection, String explanation) {}

    /**
     * 原目录中明确允许管理来源的方法。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/07：管理命令公开真实贡献者的插件归属。
     *
     * @param usage 已注册命令或子命令
     * @param description 原中文说明
     * @param sendsMessages 是否声明向真实目标发送
     * @param pluginId 真实命令贡献者的插件归属，宿主方法可为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    public record Command(String usage, String description, boolean sendsMessages,
            @org.jspecify.annotations.Nullable String pluginId) {
        /**
         * 2026/11/07：保留宿主命令原构造入口。
         * @param usage 已注册命令用法
         * @param description 中文说明
         * @param sendsMessages 是否可能发送消息
         */
        public Command(String usage, String description, boolean sendsMessages) {
            this(usage, description, sendsMessages, null);
        }
    }

    /**
     * 指令目录与当前目标状态。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param targetName 实际目标名
     * @param state 实际平台状态
     * @param commands 明确已注册的管理命令
     * @param observedAt 服务端观察时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Catalog(String targetName, State state, List<Command> commands, Instant observedAt) {}

    /**
     * 原操作事实，不输出反射返回值、命令正文或异常链。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param operationId 原编号
     * @param status ACCEPTED、RUNNING、COMPLETED、REJECTED 或 INDETERMINATE
     * @param commandStatus 原分发状态或固定诊断
     * @param output 有界明确输出
     * @param confirmedMessages 实际平台返回的消息标识
     * @param note 事实与限制
     * @param at 原提交时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Receipt(UUID operationId, String status, String commandStatus, List<String> output,
                          List<String> confirmedMessages, String note, Instant at) {
        /**
         * @param operationId 原编号
         * @param status 实际阶段
         * @param commandStatus 原分发状态
         * @param output 有界文本
         * @param confirmedMessages 已确认消息号
         * @param note 固定说明
         * @param at 原时刻
         */
        public Receipt {
            output = List.copyOf(output);
            confirmedMessages = List.copyOf(confirmedMessages);
            if (output.size() > 20 || output.stream().anyMatch(line -> line.length() > 2000)
                    || output.stream().mapToInt(String::length).sum() > 10000
                    || confirmedMessages.size() > 5 || confirmedMessages.stream().anyMatch(id -> id.isBlank() || id.length() > 128)
                    || note.length() > 256 || commandStatus.length() > 64) {
                throw new IllegalArgumentException("指令台回执超过显示容量上限。");
            }
        }
    }

    /**
     * 宿主捕获的连接能力，不通过 JSON 输出，不允许浏览器构造。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param clientClass 原实际客户端类型
     * @param target 已解析目标
     * @param connection 固定连接代次
     * @param guard 副作用前复核
     * @param sender 固定目标的原发送链，返回实际消息号
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Lease(Class<? extends ICommandClient> clientClass, ChatTargets.Resolved target, String connection,
                        Runnable guard, Function<String, CompletionStage<String>> sender) {}
}
