package org.zexnocs.teanekoclient.onebot.utils;

import org.zexnocs.teanekoapp.client.tools.IAvatarGetter;

/**
 * 根据 qq ID 获取头像 URL
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public enum AvatarUtils implements IAvatarGetter {
    Instance;

    /**
     * 根据 onebot id 获取到头像 URL
     *
     * @param userId onebot id
     * @return {@link String }
     */
    public String getUrl(long userId) {
        return String.format("https://q1.qlogo.cn/g?b=qq&nk=%d&s=640", userId);
    }

    /**
     * 根据 onebot id 获取到头像 URL
     *
     * @param userId 用户平台 ID
     * @return {@link String }
     */
    @Override
    public String getUrl(String userId) {
        return String.format("https://q1.qlogo.cn/g?b=qq&nk=%s&s=640", userId);
    }
}
