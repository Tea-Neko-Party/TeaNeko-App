package org.zexnocs.teanekocore.database.configdata.api;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * 在设置 config data 前进行检测 field 是否符合条件要求。
 * <br>2026/11/04：增加完整 JSON 值校验入口，保留原文本校验合同。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 * @version 4.7.1-l
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
    @Nullable String isValid(String field, String value);

    /**
     * 2026/11/04：字符串按原文本校验，其他 JSON 值保留结构；模块可细化空值和集合规则。
     * @param field 原 Java 字段名
     * @param value 已解析的完整 JSON 值
     * @return 不合法时返回中文说明，合法时为空
     */
    default @Nullable String isValidJson(String field, JsonNode value) {
        return isValid(field, value.isString() ? value.stringValue() : value.toString());
    }
}
