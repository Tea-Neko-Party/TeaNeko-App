package org.zexnocs.teanekoapp.client.tools;

import org.jspecify.annotations.Nullable;

/**
 * 根据用户平台 ID 获取头像 url
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
public interface IAvatarGetter {

    /**
     * 根据 user Id 获取 url
     *
     * @param userId 用户平台 ID
     * @return {@link String }
     */
    @Nullable
    String getUrl(String userId);
}
