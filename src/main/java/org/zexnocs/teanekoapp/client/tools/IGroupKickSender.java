package org.zexnocs.teanekoapp.client.tools;

import java.util.concurrent.CompletionStage;

/**
 * 群组踢出发送器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.4
 * @version 4.5.0-l
 */
public interface IGroupKickSender {
    /**
     * 将用户踢出群组。
     *
     * @param groupId 群组平台 ID
     * @param userId  用户平台 ID
     */
    void kick(String groupId, String userId);

    /**
     * 等待平台结果；实现方必须提供真实完成信号。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    CompletionStage<Void> kickAsync(String groupId, String userId);
}
