package org.zexnocs.teanekoclient.onebot.core;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoclient.onebot.config.OnebotMainFileConfig;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

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
@RequiredArgsConstructor
public class OnebotDebuggerService {
    /// 可以在这里自行添加 debugger 账号的 ID
    private final Set<Long> debuggerIds = Set.of();

    /// 用于注入 debugger id
    private final IFileConfigService iFileConfigService;

    /**
     * 判断是否是 debugger 账号。
     *
     * @param userId 用户 ID
     * @return 是否是 debugger 账号
     */
    public boolean isDebugger(long userId) {
        var config = iFileConfigService.get(OnebotMainFileConfig.class);
        if(userId == config.getDebugger().getDebuggerId()) {
            return true;
        }
        return debuggerIds.contains(userId);
    }
}
