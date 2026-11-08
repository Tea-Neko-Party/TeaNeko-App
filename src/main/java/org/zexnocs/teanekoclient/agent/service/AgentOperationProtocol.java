package org.zexnocs.teanekoclient.agent.service;

import org.jspecify.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * 类型化内部操作的传输协议，保留操作身份及响应投影，不拥有业务服务。
 * <br>2026/11/07：新增操作通过协议与处理器注册，无需修改客户端中央分支。
 *
 * @param <R> 响应数据类型
 * @param id 持久防重使用的稳定操作标识
 * @param wireName 发送日志使用的稳定协议名称
 * @param responseType 响应运行类型
 * @param statusResponse 失败及重放状态的安全投影
 * @param successful 明确业务成功判定
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record AgentOperationProtocol<R>(String id, String wireName, Class<R> responseType,
        BiFunction<@Nullable UUID, String, R> statusResponse, Predicate<R> successful) {
    /**
     * 校验静态协议身份，拒绝可被误解释为日志结构的名称。
     * @param id 稳定操作标识
     * @param wireName 稳定传输名称
     * @param responseType 明确响应类型
     * @param statusResponse 安全状态投影
     * @param successful 成功判定
     * @throws IllegalArgumentException 操作或传输名称非法时
     */
    public AgentOperationProtocol {
        Objects.requireNonNull(responseType);
        Objects.requireNonNull(statusResponse);
        Objects.requireNonNull(successful);
        if (id == null || !id.matches("[a-z][a-z0-9.-]{0,63}")
                || wireName == null || !wireName.matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("内部操作协议标识无效。");
        }
    }
}
