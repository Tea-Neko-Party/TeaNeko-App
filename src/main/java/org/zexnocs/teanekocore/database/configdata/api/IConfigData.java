package org.zexnocs.teanekocore.database.configdata.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 配置数据，用于表示存储在数据库中的配置项数据。
 * 实现该接口的类可以被序列化和反序列化为JSON格式，以便于存储和读取配置数据。
 * 必须留有一个无参构造函数，以便于反序列化时创建对象实例。
 *
 * @author zExNocs
 * @date 2026/02/16
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public interface IConfigData {
}
