package org.zexnocs.teanekocore.command.easydata;

import org.springframework.stereotype.Repository;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;

/**
 * 用于处理命令相关的数据对象的接口。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Repository("commandEasyDataRepository")
public interface CommandEasyDataRepository extends BaseEasyDataRepository<CommandEasyDataObject> {
    /**
     * 实现工厂接口。
     *
     * @param namespace 数据对象的命名空间
     * @param target    数据对象
     * @param key       数据对象映射的键
     * @param value     数据对象映射的值
     * @return {@link CommandEasyDataObject }
     */
    @Override
    default CommandEasyDataObject create(String namespace, String target, String key, String value) {
        return new CommandEasyDataObject(namespace, target, key, value);
    }
}
