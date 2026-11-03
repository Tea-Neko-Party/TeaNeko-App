package org.zexnocs.teanekocore.command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.interfaces.ICommandClient;

/**
 * 指令的数据类
 * <br>2026/11/03：管理调用显式标记来源，历史原生构造保留默认行为。
 *
 * @param <T> 指令被解析前的数据类型
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.7.0-l
 */
@Getter
@Builder
public class CommandData<T> {
    // 2026/11/03：来源不能从浏览器参数或原生角色字段反推。
    @Builder.Default
    private final org.zexnocs.teanekocore.command.api.CommandOrigin origin =
            org.zexnocs.teanekocore.command.api.CommandOrigin.NATIVE;
    /// 宿主借用的父控制，用于派生执行的取消与防重入；不参与序列化或权限判断。
    @JsonIgnore
    private final @Nullable ExecutionControl parentExecution;
    /// 指令体
    private final String body;

    /// 指令参数
    private final String[] args;

    /// 指令的执行作用域
    private final CommandScope scope;

    /// 指令的执行所属的 client
    private final Class<? extends ICommandClient> clientClass;

    /// 指令的执行作用域的 ID。
    /// 例如如果是群聊，则是 clintId + group@groupId
    /// 如果是私聊，则是 private@userId
    private final String scopeId;

    /// 指令发送者的实际权限
    private final CommandPermission permission;

    /// 指令发送者的识别 ID。
    private final String senderId;

    /// 指令被解析前的数据，应该永远不为 null
    @NonNull
    private final T rawData;

    /**
     * 获取指令被解析前的数据的类型
     *
     * @return 原始数据对象的实际类型
     */
    @SuppressWarnings("unchecked")
    public Class<T> getRawDataType() {
        return (Class<T>) rawData.getClass();
    }
}
