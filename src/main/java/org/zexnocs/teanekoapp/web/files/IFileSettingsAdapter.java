package org.zexnocs.teanekoapp.web.files;

import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import java.util.List;

/**
 * 模块明确声明文件表单、语义校验和无外部副作用的应用确认，不自动暴露所有 FileConfig。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public interface IFileSettingsAdapter {
    /** @return 中文职责名 */
    String name();
    /** @return 原固定 FileConfig 类型 */
    Class<? extends IFileConfigData> type();
    /** @return 明确的字段规则 */
    List<FileSettings.Rule> fields();
    /** @return 可追加/删除的集合，默认没有 */
    default List<FileSettings.CollectionRule> collections() {
        return List.of();
    }
    /** @return NEXT_USE 或 RESTART；只有实现确认的模块可以声明动态应用 */
    String application();
    /**
     * @param candidate 已校验的实际候选
     * @return 本候选是否还涉及需要重建的固定资源
     */
    default String applicationFor(IFileConfigData candidate) {
        return application();
    }
    /**
     * @param document 原始未展开候选树，用于兼容别名等结构校验
     */
    default void validateDocument(tools.jackson.databind.JsonNode document) {}
    /**
     * @param candidate 经原解析器绑定且未发布的候选
     */
    void validate(IFileConfigData candidate);
    /**
     * 确认不得进行网络操作、调度或其他不可幂等外部动作，也不得等待其他线程取得本类型锁。
     * @param published 已发布的同一候选
     * @return 具体模块已经确认的生效边界
     */
    String verifyApplied(IFileConfigData published);
}
