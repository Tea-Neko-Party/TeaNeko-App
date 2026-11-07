package org.zexnocs.teanekocore.plugin.management;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * 插件可贡献的具名类型化管理用例，供未来 Web 和命令适配复用。
 * 写用例自行通过领域服务完成预期版本、幂等及审计，不直接访问 Repository。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param <Q> 明确请求 DTO 类型
 * @param <R> 明确返回 View 类型
 * <br>2026/11/07：明确实际完成、提交前复核与原编号查询责任。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.8.7-l
 */
public interface ManagementResource<Q, R> {
    /** @return 不可变资源及权限声明 */
    Descriptor descriptor();
    /** @return 允许反序列化的具体 DTO 类型，不接受客户端指定 Java 类名 */
    Class<Q> requestType();
    /**
     * 执行已授权用例，不导出 Bean、控制、密钥或 Throwable。
     * <br>2026/11/06：返回阶段必须覆盖实际工作收尾；不可用提前截止的包装阶段冒充物理完成。
     * 下游须在副作用前复核 context；不能撤回的写入保留原编号查询责任。
     * @param context 可信管理上下文
     * @param request 明确类型的请求
     * @return 真实业务完成及不可变视图
     */
    CompletionStage<R> execute(ManagementContext context, Q request);

    /**
     * 资源能力及表单字段声明，不包含任何配置实际值。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param pluginId 所属插件
     * @param id 插件内资源 ID
     * @param schemaVersion DTO 协议版本
     * @param permission 宿主需校验的权限能力
     * @param mutation 是否可能写入
     * @param fields 可渲染的请求字段
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    record Descriptor(String pluginId, String id, int schemaVersion, String permission, boolean mutation, List<Field> fields) {
        /**
         * 固定资源协议并校验公开 ID。
         * @param pluginId 所属插件
         * @param id 局部资源 ID
         * @param schemaVersion 协议版本
         * @param permission 权限能力
         * @param mutation 是否写入
         * @param fields 字段描述
         */
        public Descriptor {
            Objects.requireNonNull(pluginId); fields = List.copyOf(fields);
            if (id == null || !id.matches("[a-z][a-z0-9.-]{0,63}") || schemaVersion < 1 || permission == null || permission.isBlank()) {
                throw new IllegalArgumentException("管理资源声明无效。");
            }
            if (fields.stream().map(Field::name).distinct().count() != fields.size()) { throw new IllegalArgumentException("管理字段重复。"); }
        }
        /** @return 全局资源 ID */
        public String key() { return pluginId + ":" + id; }
    }

    /**
     * 最小通用表单字段，不自动读取 Java 对象或展开敏感值。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param name 稳定字段名
     * @param type string、integer、boolean 等显式展示类型
     * @param description 中文用途说明
     * @param required 是否必填
     * @param sensitive 是否只能写入且不得回显
     * @param choices 有限枚举候选
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    record Field(String name, String type, String description, boolean required, boolean sensitive, List<String> choices) {
        /**
         * 固定字段描述。
         * @param name 字段
         * @param type 类型
         * @param description 说明
         * @param required 是否必填
         * @param sensitive 是否敏感
         * @param choices 候选值
         */
        public Field {
            Objects.requireNonNull(name); Objects.requireNonNull(type); Objects.requireNonNull(description); choices = List.copyOf(choices);
        }
    }
}
