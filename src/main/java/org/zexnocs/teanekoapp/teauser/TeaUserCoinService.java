package org.zexnocs.teanekoapp.teauser;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserCoinService;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataService;
import org.zexnocs.teanekocore.database.itemdata.metadata.IItemMetadata;

import java.util.UUID;

/**
 * 用于获取用户猫猫币的服务类。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.11
 */
@Service
public class TeaUserCoinService implements ITeaUserCoinService {

    /// item data 数据库中命名空间
    public final static String DATABASE_NAMESPACE = "tea-user";

    /// item data 中的 item type
    public final static String ITEM_TYPE = "coin";

    /// item data 服务，用于获取用户的猫猫币数据
    private final IItemDataService iItemDataService;
    private final ITeaUserService iTeaUserService;

    public TeaUserCoinService(IItemDataService iItemDataService, ITeaUserService iTeaUserService) {
        this.iItemDataService = iItemDataService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 获取用户的猫猫币 dto future。
     *
     * @param userId 用户的 UUID
     * @return 用户的猫猫币 dto future
     * @see TaskFuture
     */
    @Override
    public TaskFuture<IItemDataDTO<IItemMetadata>> getCoin(UUID userId) {
        return iItemDataService.getOrCreate(userId, DATABASE_NAMESPACE, ITEM_TYPE, 0, null);
    }

    /**
     * 根据 client 和 平台用户 ID 获取用户的猫猫币 dto future。
     * <p>如果不存在用户，则创建一个新的用户并返回其猫猫币 dto future。
     *
     * @param client 客户端
     * @param userId 平台用户 ID
     * @return 用户的猫猫币 dto future
     * @see TaskFuture
     */
    @Override
    public TaskFuture<IItemDataDTO<IItemMetadata>> getCoin(ITeaNekoClient client, String userId) {
        return iTeaUserService.getOrCreateId(client, userId)
                .thenComposeTask(this::getCoin);
    }
}
