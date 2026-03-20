package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.tools.IPlatformUserInfoConstructor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotContentListBuilder;

import java.util.List;

/**
 * 符合 onebot 协议的用户信息构造器，用于根据平台 ID 构造出用户的信息列表。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Service
public class OnebotUserInfoConstructor implements IPlatformUserInfoConstructor {
    /**
     * 根据平台 ID 构造简单的用户信息列表。
     * 只有头像 和 平台 ID 两项信息。
     *
     * @param platformId 平台 ID
     * @return 用户信息列表的 future
     */
    @Override
    public List<ITeaNekoContent> getSimpleInfo(String platformId) {
        return OnebotContentListBuilder.builder()
                .addImage(AvatarUtils.Instance.getUrl(Long.parseLong(platformId)))
                .addText("QQ号: " + platformId)
                .build();
    }
}
