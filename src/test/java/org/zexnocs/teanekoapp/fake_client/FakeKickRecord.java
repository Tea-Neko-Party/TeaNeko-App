package org.zexnocs.teanekoapp.fake_client;

/**
 * 记录一次模拟踢出动作的目标群、用户和执行信息。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param groupId 目标群组标识
 * @param userId 原业务边界中的目标用户标识
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public record FakeKickRecord(String groupId, String userId) {
}
