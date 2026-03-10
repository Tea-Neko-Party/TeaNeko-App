package org.zexnocs.teanekoapp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;

import java.util.UUID;

/**
 * 配置项 key。
 * <br>为当前配置的 scope ID。
 *
 * @see org.zexnocs.teanekocore.database.configdata.api.IConfigData
 * @see org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService
 * @author zExNocs
 * @date 2026/03/11
 * @since 4.1.3
 */
@Service
public class TeaNekoConfigKey implements IConfigKey {
    /// 当前配置的 scope ID。
    private String scopeId = null;
    private static TeaNekoCommandConverter teaNekoCommandConverter = null;

    /**
     * 用于注入 scope 解析服务。
     */
    @Autowired
    public TeaNekoConfigKey(TeaNekoCommandConverter teaNekoCommandConverter) {
        TeaNekoConfigKey.teaNekoCommandConverter = teaNekoCommandConverter;
    }

    /**
     * 使用群 ID 构造配置键。
     *
     * @param groupId 群 ID
     */
    public TeaNekoConfigKey(ITeaNekoClient client, String groupId) {
        if(teaNekoCommandConverter == null) {
            throw new IllegalStateException("TeaNekoCommandConverter is not initialized");
        }
        this.scopeId = teaNekoCommandConverter.getGroupScopeId(client, groupId);
    }

    /**
     * 直接使用 scope ID 构造配置键。
     *
     * @param scopeId scope ID
     */
    public TeaNekoConfigKey(String scopeId) {
        this.scopeId = scopeId;
    }

    /**
     * 使用用户 ID 构造配置键。
     *
     * @param userId 用户 ID
     */
    public TeaNekoConfigKey(UUID userId) {
        if(teaNekoCommandConverter == null) {
            throw new IllegalStateException("TeaNekoCommandConverter is not initialized");
        }
        this.scopeId = teaNekoCommandConverter.getPrivateScopeId(userId);
    }

    /**
     * 获取配置键。
     *
     * @return 配置键
     */
    @Override
    public String getKey() {
        return scopeId;
    }
}
