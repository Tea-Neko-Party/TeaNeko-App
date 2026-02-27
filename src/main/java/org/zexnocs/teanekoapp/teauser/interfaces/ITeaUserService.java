package org.zexnocs.teanekoapp.teauser.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;

import java.util.UUID;

/**
 * 用于根据客户端和平台 ID 获取 TeaUser 的服务接口。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.11
 */
public interface ITeaUserService {

    /**
     * 根据客户端和平台用户 ID 获取 TeaUser。
     * 如果不存在，则返回 {@code null}。
     *
     * @param client     客户端
     * @param userId 平台用户 ID
     * @return TeaUser 对象的 UUID; 如果没有找到则返回 null
     */
    @Nullable
    UUID getUserId(ITeaNekoClient client, String userId);

    /**
     * 根据客户端和平台用户 ID  获取或者创建 TeaUser。
     *
     * @see TaskFuture
     * @param client     客户端
     * @param userId 平台用户 ID
     * @return TeaUser 对象的 UUID 的 future; 如果没有则会异步创建一个新的 TeaUser，并返回其 UUID
     */
    TaskFuture<UUID> getOrCreateUserId(ITeaNekoClient client, String userId);
}
