package org.zexnocs.teanekoclient.onebot.utils;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.config.TeaNekoConfigKey;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;

/**
 * 专门用于处理 onebot 获取 scope ID 的工具类。
 *
 * @author zExNocs
 * @date 2026/03/10
 */
@Service
public class OnebotScopeIdUtils {
    private final TeaNekoCommandConverter teaNekoCommandConverter;
    private final OnebotTeaNekoClient onebotTeaNekoClient;
    private final ITeaUserService iTeaUserService;

    public OnebotScopeIdUtils(TeaNekoCommandConverter teaNekoCommandConverter, OnebotTeaNekoClient onebotTeaNekoClient, ITeaUserService iTeaUserService) {
        this.teaNekoCommandConverter = teaNekoCommandConverter;
        this.onebotTeaNekoClient = onebotTeaNekoClient;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 根据 group id 获取 scope id
     *
     * @param groupId 群号
     * @return scope id
     */
    public String getGroupScopeId(long groupId) {
        return teaNekoCommandConverter.getGroupScopeId(onebotTeaNekoClient, String.valueOf(groupId));
    }

    /**
     * 根据 group id 获取 scope id
     *
     * @param groupId 群号
     * @return scope id
     */
    public String getGroupScopeId(String groupId) {
        return teaNekoCommandConverter.getGroupScopeId(onebotTeaNekoClient, groupId);
    }

    /**
     * 根据 user id 获取 scope id
     *
     * @param userId 用户平台 ID
     * @return scope id
     * @throws IllegalArgumentException 如果无法获取用户 UUID
     */
    public String getUserScopeId(String userId) throws IllegalArgumentException {
        var uuid = iTeaUserService.get(onebotTeaNekoClient, userId);
        if(uuid == null) {
            throw new IllegalArgumentException("无法获取用户 UUID，用户 ID: " + userId);
        }
        return teaNekoCommandConverter.getPrivateScopeId(uuid);
    }

    /**
     * 根据 user id 获取 scope id
     *
     * @param userId 用户平台 ID
     * @return scope id
     * @throws IllegalArgumentException 如果无法获取用户 UUID
     */
    public String getUserScopeId(long userId) throws IllegalArgumentException {
        return getUserScopeId(String.valueOf(userId));
    }

    /**
     * 通过 group ID 获取 tea neko config key
     *
     * @param groupId 群号
     * @return teaneko config key
     */
    public IConfigKey getGroupConfigKey(long groupId) {
        return new TeaNekoConfigKey(getGroupScopeId(groupId));
    }

    /**
     * 通过 group ID 获取 tea neko config key
     *
     * @param groupId 群号
     * @return teaneko config key
     */
    public IConfigKey getGroupConfigKey(String groupId) {
        return new TeaNekoConfigKey(getGroupScopeId(groupId));
    }

    /**
     * 通过 user ID 获取 tea neko config key
     */
    public IConfigKey getUserConfigKey(String userId) {
        return new TeaNekoConfigKey(getUserScopeId(userId));
    }

    /**
     * 通过 user ID 获取 tea neko config key
     */
    public IConfigKey getUserConfigKey(long userId) {
        return getUserConfigKey(String.valueOf(userId));
    }
}
