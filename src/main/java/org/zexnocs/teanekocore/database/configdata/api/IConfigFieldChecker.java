package org.zexnocs.teanekocore.database.configdata.api;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * 在设置 config data 前进行检测 field 是否符合条件要求。
 * <br>2026/11/04：增加完整 JSON 值校验入口，保留原文本校验合同。
 *
 * <br>2026/11/04：新增原事务内的结构化字段预处理契约。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 * @version 4.8.0-l
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

    /**
     * 2026/11/04：受版本仲裁保护的字段替换可在行锁内保留明确指示不变的秘密。
     * 默认不变换输入；返回值仍经过类型与业务校验，不能绕过写入权限。
     * @param field 原字段名
     * @param submitted 本次提交值
     * @param previous 当前行原值，字段缺失时为空
     * @return 实际待校验和提交的独立节点
     */
    default JsonNode prepareJson(String field, JsonNode submitted, @Nullable JsonNode previous) {
        return submitted;
    }
}
