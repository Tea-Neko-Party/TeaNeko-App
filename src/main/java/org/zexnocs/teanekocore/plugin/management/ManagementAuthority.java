package org.zexnocs.teanekocore.plugin.management;

import java.util.concurrent.CompletionStage;

/**
 * 宿主提供的管理授权边界；Web 登录与业务 OWNER/DEBUG 映射由适配器显式负责。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
@FunctionalInterface
public interface ManagementAuthority {
    /**
     * 在实际操作及返回前检查当前可信权限，拒绝时异常完成。
     * @param context 可信主体及目标
     * @param resource 请求的管理资源
     * @return 获准信号
     */
    CompletionStage<Void> authorize(ManagementContext context, ManagementResource.Descriptor resource);
}
