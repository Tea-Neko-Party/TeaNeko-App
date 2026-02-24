package org.zexnocs.teanekoapp.response.api;

/**
 * 平台用户信息响应数据接口。
 *
 * @author zExNocs
 * @date 2026/02/24
 */
public interface IPlatformUserResponseData {
    /**
     * 获取用户在该平台上的唯一标识符。
     *
     * @return 用户唯一标识符
     */
    String getPlatformUserId();

    /**
     * 获取用户的昵称。
     *
     * @return 用户昵称
     */
    String getNickname();
}
