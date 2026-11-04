package org.zexnocs.teanekoapp.web.files;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.file_config.FileConfigDocument;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 机器令牌专用管理契约；目录不携带秘密，只有显式查看响应返回指定客户端的值。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
public final class MachineTokens {
    /** 禁止实例化契约容器。 */
    private MachineTokens() {}

    /**
     * 单个机器入口的磁盘与运行策略，未配置和空白放行分别表示。
     *
     * @param client 客户端稳定标识
     * @param name 中文显示名称
     * @param disk 磁盘策略 DENIED、OPEN 或 PROTECTED
     * @param active 当前运行策略
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public record Client(String client, String name, String disk, String active) {}

    /**
     * 令牌目录不返回管理员秘密或原文件树。
     *
     * @param revision 当前磁盘版本
     * @param runtime 当前运行版本
     * @param clients 可管理的真实机器入口
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public record View(String revision, FileConfigDocument.Stamp runtime, List<Client> clients) {}

    /**
     * 显式查看响应只携带指定客户端的当前磁盘值，禁止一般诊断输出。
     *
     * @param revision 对应磁盘版本
     * @param value 经环境展开的实际令牌
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public record Secret(String revision, String value) {
        /** @return 不含秘密的固定说明 */
        @Override
        @NonNull
        public String toString() {
            return "机器令牌查看响应（敏感值已隐藏）";
        }
    }

    /**
     * 固定客户端的局部修改，SET 替换、OPEN 空白放行、DENY 移除声明。
     *
     * @param operationId 原操作编号
     * @param expectedRevision 原磁盘版本
     * @param action 明确修改方式
     * @param value SET 的原始新值，可使用环境模板，其他动作不得提供
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    public record Change(UUID operationId, String expectedRevision, String action,
                         @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Nullable String value) {
        /**
         * 验证请求边界，不将秘密用于异常文本。
         * @param operationId 原编号
         * @param expectedRevision 原版本
         * @param action 修改动作
         * @param value 可选候选秘密
         * @throws IllegalArgumentException 当动作、版本或新值不符合契约时
         */
        public Change {
            if (operationId == null || action == null
                    || expectedRevision == null || expectedRevision.isBlank() || expectedRevision.length() > 128
                    || !Set.of("SET", "OPEN", "DENY").contains(action)
                    || action.equals("SET") && (value == null || value.length() > 256)
                    || !action.equals("SET") && value != null) {
                throw new IllegalArgumentException("机器令牌修改参数无效。");
            }
        }

        /** @return 不包含候选值的固定说明 */
        @Override
        @NonNull
        public String toString() {
            return "机器令牌修改请求（敏感值已隐藏）";
        }
    }
}
