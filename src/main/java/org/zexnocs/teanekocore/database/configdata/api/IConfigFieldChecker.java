package org.zexnocs.teanekocore.database.configdata.api;

/**
 * 在设置 config data 前进行检测 field 是否符合条件要求。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
public interface IConfigFieldChecker {
    /**
     * 检测当前域的设置值是否合理。
     * <br>如果合理则返回
     * {@code null}
     * <br>如果不合理则返回报错信息。
     *
     * @param field 域名
     * @param value 值
     * @return {@link String }
     */
    String isValid(String field, String value);
}
