package org.zexnocs.teanekoplugin.onebot.requests.invite;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 用于被邀请入群时候通知 debugger。如果同意则自动入群。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/28
 * @since 4.4.0
 * @version 4.9.0-l
 */
@PluginOwned("onebot.requests")
public class OnebotGroupInviteService {
}
