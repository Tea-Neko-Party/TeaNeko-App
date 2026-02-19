package org.zexnocs.teanekoclient.onebot.utils;

/**
 * 根据 qq ID 获取头像 URL
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public enum AvatarUtils {
    Instance;

    public String getAvatarUrl(long qqId) {
        return String.format("https://q1.qlogo.cn/g?b=qq&nk=%d&s=640", qqId);
    }
}
