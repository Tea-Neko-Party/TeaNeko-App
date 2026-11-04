package org.zexnocs.teanekocore.file_config.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.framework.config.ConfigSchema;
import tools.jackson.databind.JsonNode;

/**
 * 原文件解析器可选提供的文档能力；原始树不展开环境变量，绑定仍走原加载规则。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：可选提供与文件命名策略一致的通用表单元数据。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
public interface IFileDocumentParser extends IFileTypeParser {
    /**
     * @param type 已注册的配置类型
     * @return 与实际解析器一致的字段元数据，不支持时为空
     */
    default @Nullable ConfigSchema schema(Class<? extends IFileConfigData> type) {
        return null;
    }

    /**
     * @param bytes 原始文件字节
     * @return 未展开的文档树，不作通用导出
     */
    JsonNode readDocument(byte[] bytes);

    /**
     * @param document 未展开的完整树
     * @return 原格式文件，不保证注释和排版保持
     */
    byte[] writeDocument(JsonNode document);

    /**
     * @param type 已声明配置类型
     * @return 默认对象的原始文档，仅用于缺少文件及模板时的预览
     */
    JsonNode defaultDocument(Class<? extends IFileConfigData> type);
}
