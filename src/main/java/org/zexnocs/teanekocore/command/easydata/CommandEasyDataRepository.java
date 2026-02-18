package org.zexnocs.teanekocore.command.easydata;

import org.springframework.stereotype.Repository;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;

/**
 * 用于处理命令相关的数据对象的接口。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Repository("commandEasyDataRepository")
public interface CommandEasyDataRepository extends BaseEasyDataRepository<CommandEasyDataObject> {
    @Override
    default CommandEasyDataObject create(String namespace, String target, String key, String value) {
        return new CommandEasyDataObject(namespace, target, key, value);
    }
}
