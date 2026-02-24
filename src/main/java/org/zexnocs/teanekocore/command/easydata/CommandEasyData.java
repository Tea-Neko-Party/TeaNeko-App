package org.zexnocs.teanekocore.command.easydata;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekocore.database.easydata.BaseEasyData;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.core.EasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;

/**
 * 用于处理命令相关的数据对象的类。
 * 主要是指令的权限
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@EasyData
public class CommandEasyData extends BaseEasyData {
    /// 用于获取数据库服务。
    private static IEasyDataService easyDataService;

    /**
     * 用于注入数据库服务。
     * @param easyDataService 数据库服务。
     */
    @Autowired
    public CommandEasyData(IEasyDataService easyDataService) {
        super(easyDataService, null);
        CommandEasyData.easyDataService = easyDataService;
    }

    /**
     * 用于创建一个数据对象。
     * @param namespace 数据对象的命名空间。
     */
    private CommandEasyData(String namespace) {
        super(easyDataService, namespace);
    }

    /**
     * 用于创建一个数据对象。
     * @return 数据对象。
     */
    public static CommandEasyData of(String namespace) {
        return new CommandEasyData(namespace);
    }

    @Override
    public Class<? extends BaseEasyDataObject> getEntityClass() {
        return CommandEasyDataObject.class;
    }
}
