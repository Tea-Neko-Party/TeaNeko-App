package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 用于设置 onebot debugger 的服务类。
 * 临时嵌入在代码中，以后将会放在配置文件中。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.1
 */
@Service
public class OnebotDebuggerService {
    /// 可以在这里自行设置 debugger 账号的 ID
    private static final Set<Long> debuggerIds = Set.of(

    );

    /**
     * 判断是否是 debugger 账号。
     *
     * @param userId 用户 ID
     * @return 是否是 debugger 账号
     */
    public boolean isDebugger(long userId) {
        return debuggerIds.contains(userId);
    }
}
