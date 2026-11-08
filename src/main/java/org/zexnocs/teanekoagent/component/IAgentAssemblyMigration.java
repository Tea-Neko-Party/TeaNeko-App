package org.zexnocs.teanekoagent.component;

import tools.jackson.databind.node.ObjectNode;

/**
 * 插件拥有的装配兼容转换；只修改独立配置树，不执行数据访问或扩大授权。
 * <br>2026/11/07：组件迁移由所属插件声明，骨架不维护业务标识分支。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentAssemblyMigration {
    /** @return 全局唯一且稳定的迁移标识。 */
    String id();

    /**
     * 幂等转换一份独立装配；冲突必须报告，不能选择性覆盖原参数。
     * @param document 完整装配文档
     */
    void migrate(ObjectNode document);

    /**
     * 2026/11/07：仅在旧平铺配置首次显式装配时恢复插件自己的历史选择，不新增旧配置未授予的能力。
     * @param configuration 独立的完整旧配置树，只读使用
     * @param document 已生成原生参数的独立装配，随后继续执行普通兼容转换
     * @param templates 当前完整安装目录的独立公开默认参数，不包含可执行对象
     */
    default void migrateLegacy(ObjectNode configuration, ObjectNode document,
                               java.util.Map<String, ObjectNode> templates) {}

    /** @return 旧能力到等价新能力的精确映射，不扩大原组件或客户端权限。 */
    default java.util.Map<String, java.util.Set<String>> featureAliases() {
        return java.util.Map.of();
    }
}
