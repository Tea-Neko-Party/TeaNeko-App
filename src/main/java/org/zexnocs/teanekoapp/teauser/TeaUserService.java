package org.zexnocs.teanekoapp.teauser;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.List;
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
    /// 转化为 TeaUser 的数据命名空间
    public static final String TO_TEA_USER_NAMESPACE = "to_teaneko";

    private final ILogger logger;

    /// 构造一个根据 UUID 和 客户端获取 平台ID 的缓存
    private final ConcurrentMapCacheContainer<Pair<String, UUID>, String> cache;

    public TeaUserService(ILogger logger, ICacheService iCacheService) {
        this.logger = logger;
        cache = ConcurrentMapCacheContainer.of(iCacheService);
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
    public @Nullable UUID get(ITeaNekoClient client, String userId) {
        var target = getToTeaUserTarget(client);
        var uuidStr = target.get(userId);
        if(uuidStr == null) {
            return null;
        }
        try {
            var uuid = UUID.fromString(uuidStr);
            // 添加到缓存中
            var cacheKey = HashPair.of(client.getClientId(), uuid);
            cache.put(cacheKey, userId);
            return uuid;
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
    public TaskFuture<UUID> getOrCreate(ITeaNekoClient client, String userId) {
        var target = getToTeaUserTarget(client);
        var uuid = target.get(userId);
        if (uuid != null) {
            try {
                var existingUuid = UUID.fromString(uuid);
                var resultFuture = new TaskFuture<UUID>(logger,
                        "成功获取现有 UUID",
                        new CompletableFuture<>());
                resultFuture.complete(existingUuid);

                // 添加到缓存中
                var cacheKey = HashPair.of(client.getClientId(), existingUuid);
                cache.put(cacheKey, userId);

                return resultFuture;
            } catch (IllegalArgumentException e) {
                // 如果数据库中的数据不是一个合法的 UUID 字符串，则与后面一样设置一个新的 UUID
            }
        }
        return target.getTaskConfig("创建新的 UUID")
                .set(userId, UUID.randomUUID().toString())
                .pushWithFuture()
                .thenApply(r -> {
                    var currentUUID = UUID.fromString(Objects.requireNonNull(target.get(userId)));
                    // 添加到缓存中
                    var cacheKey = HashPair.of(client.getClientId(), currentUUID);
                    cache.put(cacheKey, userId);
                    return currentUUID;
                });
    }

    /**
     * 根据 client 和 UUID 获取 平台用户 ID。
     *
     * @param client 客户端
     * @param uuid   TeaUser 的 UUID
     * @return 平台用户 ID; 如果没有找到则返回 null
     */
    @Override
    public @Nullable String getPlatformId(ITeaNekoClient client, UUID uuid) {
        // 先尝试从缓存中获取
        var cacheKey = HashPair.of(client.getClientId(), uuid);
        var cachedUserId = cache.get(cacheKey);
        if (cachedUserId != null) {
            return cachedUserId;
        }
        // 否则强行从数据库中获取
        var keyList = getToTeaUserTarget(client).getKeysByValue(uuid.toString());
        if (keyList.isEmpty()) {
            return null;
        } else {
            var userId = keyList.getFirst();
            // 将结果缓存起来
            cache.put(cacheKey, userId);
            return userId;
        }
    }

    /**
     * 获取客户端平台下所有的 UUID
     *
     * @param client 客户端
     */
    @Override
    public @NonNull List<UUID> getAll(ITeaNekoClient client) {
        var target = getToTeaUserTarget(client);
        return target.keySet()
                .stream()
                .map(key -> get(client, key))
                .toList();
    }

    /**
     * 从客户端中获取到 TeaUser 的 target。
     *
     * @param client 客户端
     * @return TeaUser 的 target
     */
    @NonNull
    private static IEasyDataDto getToTeaUserTarget(ITeaNekoClient client) {
        // 获取到 target
        return GeneralEasyData.of(TO_TEA_USER_NAMESPACE).get(client.getClientId());
    }
}
