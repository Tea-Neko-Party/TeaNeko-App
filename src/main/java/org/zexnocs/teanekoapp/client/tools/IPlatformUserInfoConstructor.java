package org.zexnocs.teanekoapp.client.tools;

import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;

import java.util.List;

/**
 * 构造平台用户信息的发送器接口。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
public interface IPlatformUserInfoConstructor {
    /**
     * 根据平台 ID 构造简单的用户信息列表。
     * <br>如果结尾是文本信息，请不要用 {@code "\n"} 作为结尾
     *
     * @param platformId 平台 ID
     * @return 用户信息列表的 future
     */
    List<ITeaNekoContent> getSimpleInfo(String platformId);
}
