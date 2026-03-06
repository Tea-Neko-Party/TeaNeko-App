package org.zexnocs.teanekoplugin.general.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 数据库相关系统级指令。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Description("有关数据库相关系统级的指令。")
@Command(value = {"/data"},
        permission = CommandPermission.DEBUG,
        scope = CommandScope.ALL)
public class DataCommand {
    private final ICacheService iCacheService;

    @Autowired
    public DataCommand(IEasyDataService easyDataService,
                       ICacheService iCacheService) {
        this.iCacheService = iCacheService;
    }

    @Description("清除数据库缓存。")
    @SubCommand(value = {"clear"}, permission = CommandPermission.DEBUG)
    public void clearCache() {
        iCacheService.manualCleanAll();
    }
}
