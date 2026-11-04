package org.zexnocs.teanekoclient.web;

import java.util.concurrent.CompletionStage;

/**
 * 在进入房间或提交消息前完成模块自己的幂等准备，不创建第二条消息执行链。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
public interface IWebRoomPreparation {
    /**
     * @param room 服务端读取的真实房间
     * @param guard 当前会话及插件生命周期复核
     * @return 实际准备完成后的阶段
     */
    CompletionStage<Void> prepare(WebChat.Room room, Runnable guard);
}
