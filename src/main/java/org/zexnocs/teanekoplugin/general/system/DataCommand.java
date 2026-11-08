package org.zexnocs.teanekoplugin.general.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 数据库相关系统级指令。
 *
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Description("有关数据库相关系统级的指令。")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "数据维护", value = {"/data"},
        permission = CommandPermission.DEBUG,
        scope = CommandScope.ALL)
@PluginOwned("general.system")
public class DataCommand {
    private final ICacheService iCacheService;

    /**
     * 注入数据与缓存维护端口，管理动作仍经过原命令权限校验。
     *
     * @param easyDataService 由装配方提供的 {@code IEasyDataService} 协作依赖
     * @param iCacheService 缓存注册与生命周期服务
     */
    @Autowired
    public DataCommand(IEasyDataService easyDataService,
                       ICacheService iCacheService) {
        this.iCacheService = iCacheService;
    }

    /**
     * 清除数据库缓存；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("清除数据库缓存。")
    @SubCommand(value = {"clear"}, permission = CommandPermission.DEBUG)
    public CompletionStage<Void> clearCache() {
        iCacheService.manualCleanAll();

        return CompletableFuture.completedStage(null);
    }
}
