package org.zexnocs.teanekoapp.teauser;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 用于根据客户端和平台 ID 获取 TeaUser 的服务类。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.11
 */
@Service
public class TeaUserService implements ITeaUserService {
    /// 数据库中命名空间
    public static String EASY_DATA_NAMESPACE = "tea-user-mapping";
    private final ILogger logger;

    public TeaUserService(ILogger logger) {
        this.logger = logger;
    }


    /**
     * 根据客户端和平台用户 ID 获取 TeaUser。
     * 如果不存在，则返回 {@code null}。
     *
     * @param client 客户端
     * @param userId 平台用户 ID
     * @return TeaUser 对象的 UUID; 如果没有找到则返回 null
     */
    @Override
    public @Nullable UUID getId(ITeaNekoClient client, String userId) {
        var target = getTarget(client);
        var uuid = target.get(userId);
        if(uuid == null) {
            return null;
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            // 如果数据库中的数据不是一个合法的 UUID 字符串，则删除并返回 null
            target.getTaskConfig("删除非法 UUID 数据")
                    .remove(userId)
                    .push();
            return null;
        }
    }

    /**
     * 根据客户端和平台用户 ID  获取或者创建 TeaUser。
     *
     * @param client 客户端
     * @param userId 平台用户 ID
     * @return TeaUser 对象的 UUID 的 future; 如果没有则会异步创建一个新的 TeaUser，并返回其 UUID
     * @see TaskFuture
     */
    @Override
    @NonNull
    public TaskFuture<UUID> getOrCreateId(ITeaNekoClient client, String userId) {
        var target = getTarget(client);
        var uuid = target.get(userId);
        if (uuid != null) {
            try {
                var existingUuid = UUID.fromString(uuid);
                var resultFuture = new TaskFuture<UUID>(logger,
                        "成功获取现有 UUID",
                        new CompletableFuture<>());
                resultFuture.complete(existingUuid);
                return resultFuture;
            } catch (IllegalArgumentException e) {
                // 如果数据库中的数据不是一个合法的 UUID 字符串，则与后面一样设置一个新的 UUID
            }
        }
        return target.getTaskConfig("创建新的 UUID")
                .set(userId, UUID.randomUUID().toString())
                .pushWithFuture()
                .thenApply(r -> UUID.fromString(Objects.requireNonNull(target.get(userId))));
    }

    /**
     * 从客户端中获取到 TeaUser 的 target。
     *
     * @param client 客户端
     * @return TeaUser 的 target
     */
    @NonNull
    private static IEasyDataDto getTarget(ITeaNekoClient client) {
        // 获取到 target
        return GeneralEasyData.of(EASY_DATA_NAMESPACE).get(client.getClientId());
    }
}
