package org.zexnocs.teanekoagent.memory.profile;

import java.util.concurrent.CompletionStage;

/**
 * 按共享 namespace、generation 和主体 UUID 重建证据画像的独立读取端口。
 * 启用、当前绑定和读权限由可信宿主检查；不创建事实、人格补正或管理操作。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
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
