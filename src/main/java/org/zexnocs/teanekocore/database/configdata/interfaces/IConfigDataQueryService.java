package org.zexnocs.teanekocore.database.configdata.interfaces;

import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigManagerNamespaceMismatchException;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

import java.util.List;

/**
 * 配置数据查询服务接口。
 *
 * @param <T>  可以产生 IConfigKey 的类型
 * @author zExNocs
 * @date 2026/02/16
 */
public interface IConfigDataQueryService<T> {
    /**
     * 获取该 query 所属的命名空间。
     */
    String[] getNamespaces();

    /**
     * 根据 T 获取 key。
     */
    IConfigKey getConfigKey(T t);

    /**
     * 查看单个 configManager 的具体配置。
     * @param configManager 配置管理器。
     * @return 返回该规则的详细信息列表。
     * @throws ConfigManagerNamespaceMismatchException 如果配置管理器的命名空间与查询服务不匹配。
     */
    String queryOneConfigManagerDetail(ConfigManager configManager)
            throws ConfigManagerNamespaceMismatchException;

    /**
     * 查看某个域中存在的所有 configManager 的具体配置。
     * @return 返回所有群组管理规则的详细信息列表。
     */
    List<String> queryAllConfigManagerDetails();

    /**
     * 查看指定对象开启的单个规则的具体配置。
     * @param configManager 配置管理器。
     * @param key 对象键。
     * @return 返回该对象开启的指定 configManager 规则的详细信息。
     * @throws IllegalAccessException 如果配置类的字段无法访问。
     */
    String queryOneConfigManagerDetailInObject(ConfigManager configManager, String key)
            throws IllegalAccessException, ConfigManagerNamespaceMismatchException;

    /**
     * 查看指定对象开启的单个规则的具体配置。
     * @param configManager 配置管理器。
     * @param key 对象键。
     * @return 返回该对象开启的指定 configManager 规则的详细信息。
     * @throws IllegalAccessException 如果配置类的字段无法访问。
     * @throws ConfigManagerNamespaceMismatchException 如果配置管理器的命名空间与查询服务不匹配。
     */
    default String queryOneConfigManagerDetailInObject(ConfigManager configManager, IConfigKey key)
            throws IllegalAccessException, ConfigManagerNamespaceMismatchException {
        return queryOneConfigManagerDetailInObject(configManager, key.getKey());
    }

    /**
     * 查看指定对象开启的单个规则的具体配置。
     * @param configManager 配置管理器。
     * @param t 对象。
     * @return 返回该对象开启的指定 configManager 规则的详细信息。
     * @throws IllegalAccessException 如果配置类的字段无法访问。
     * @throws ConfigManagerNamespaceMismatchException 如果配置管理器的命名空间与查询服务不匹配。
     */
    default String queryOneConfigManagerDetailInObject(ConfigManager configManager, T t)
            throws IllegalAccessException, ConfigManagerNamespaceMismatchException {
        return queryOneConfigManagerDetailInObject(configManager, getConfigKey(t));
    }


    /**
     * 查看指定对象开启的当前域域中所有规则的具体配置。
     * @param key 对象键
     * @return 返回该对象开启的所有 configManager 规则的详细信息列表。
     * @throws IllegalAccessException 如果配置类的字段无法访问。
     */
    List<String> queryAllConfigManagerDetailsInObject(String key) throws IllegalAccessException;

    /**
     * 查看指定对象开启的当前域中所有规则的具体配置。
     * @param key 对象键
     * @return 返回该对象开启的所有 configManager 规则的详细信息列表。
     * @throws IllegalAccessException 如果配置类的字段无法访问。
     */
    default List<String> queryAllConfigManagerDetailsInObject(IConfigKey key)
            throws IllegalAccessException {
        return queryAllConfigManagerDetailsInObject(key.getKey());
    }

    /**
     * 查看指定对象开启的当前域中所有规则的具体配置。
     * @param t 对象
     * @return 返回该对象开启的所有 configManager 规则的详细信息列表。
     * @throws IllegalAccessException 如果配置类的字段无法访问。
     */
    default List<String> queryAllConfigManagerDetailsInObject(T t)
            throws IllegalAccessException {
        return queryAllConfigManagerDetailsInObject(getConfigKey(t));
    }
}
