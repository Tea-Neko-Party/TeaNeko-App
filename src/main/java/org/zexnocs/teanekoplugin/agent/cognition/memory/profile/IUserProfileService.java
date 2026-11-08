package org.zexnocs.teanekoplugin.agent.cognition.memory.profile;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.concurrent.CompletionStage;

/**
 * 按共享 namespace、generation 和主体 UUID 重建证据画像的独立读取端口。
 * 启用、当前绑定和读权限由可信宿主检查；不创建事实、人格补正或管理操作。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IUserProfileService {
    /**
     * 每次从已接受的有效事实和偏好读取一份有界视图，不复用其他空间或旧代次缓存。
     * @param request 可信主体及固定引用
     * @return 保留部分覆盖程度的画像，来源失败和旧代次异常结束
     */
    CompletionStage<UserProfileView> read(UserProfileRequest request);
}
