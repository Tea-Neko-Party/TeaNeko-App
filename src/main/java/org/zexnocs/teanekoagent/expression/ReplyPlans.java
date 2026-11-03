package org.zexnocs.teanekoagent.expression;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 协议和交付之间的不可变值，不持有服务、控制或权限。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public final class ReplyPlans {
    /** 禁止实例化值类型集合。 */
    private ReplyPlans() {}
    /**
     * 完整协议的精确键。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param id 协议 ID
     * @param version 版本
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record ProtocolKey(String id, int version) {
        /**
         * 拒绝无界名称和非法版本。
         * @param id 协议 ID
         * @param version 版本
         * @throws IllegalArgumentException 当键无效时
         */
        public ProtocolKey { if (!id.matches("[a-z][a-z0-9_.-]{0,47}:[a-z][a-z0-9_.-]{0,47}") || version < 1 || version > 1000) { throw new IllegalArgumentException("回复协议标识或版本无效。"); } }
    }
    /**
     * 协议内指令键。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param protocol 所属协议
     * @param name 指令名
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record DirectiveKey(ProtocolKey protocol, String name) {}
    /**
     * 允许插件实现的内容合同；实现及所有字段必须不可变。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public interface Content {}
    /**
     * 字面文本。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param value 正文
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Text(String value) implements Content {}
    /**
     * 普通图片引用。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param url 图片引用
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Image(String url) implements Content {}
    /**
     * 已切分的指令。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param name 指令名
     * @param fields 业务字段
     * @param body 字面正文
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Directive(String name, Map<String,String> fields, String body) {
        /**
         * 固定字段。
         * @param name 指令名
         * @param fields 字段
         * @param body 正文
         */
        public Directive { fields = Map.copyOf(fields); }
    }
    /**
     * 等待归一化前的草稿。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param content 不可变内容
     * @param mode 回复方式
     * @param waitMillis 建议等待，未声明时为空
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Draft(Content content, Mode mode, @Nullable Integer waitMillis) {}
    /**
     * 冻结的发送步骤。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param content 内容
     * @param mode 回复方式
     * @param waitMillis 实际等待
     * @param type 内容类型 ID
     * @param version 内容语义版本
     * @param text 历史投影
     * @param digest 实际内容摘要
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public record Step(Content content, Mode mode, int waitMillis, String type, int version, String text, String digest) {}
    /**
     * 只由本包编译器创建的完整计划，语义答案和原文摘要共同冻结。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    public static final class Approved {
        private final UUID requestId;
        private final ProtocolKey protocol;
        private final String sourceDigest;
        private final List<Step> steps;
        private final List<PluginRegistry.Token> tokens;
        private final org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage answer;
        /**
         * 固定编译结果，不允许普通调用者伪造已审核计划。
         * @param requestId 原请求
         * @param protocol 原协议
         * @param sourceDigest 原文摘要
         * @param steps 有序步骤
         * @param tokens 扩展版本
         * @param answer 编译器生成的不可变语义消息
         */
        private Approved(UUID requestId, ProtocolKey protocol, String sourceDigest, List<Step> steps, List<PluginRegistry.Token> tokens,
                org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage answer) {
            this.requestId=requestId;this.protocol=protocol;this.sourceDigest=sourceDigest;this.steps=List.copyOf(steps);this.tokens=List.copyOf(tokens);this.answer=answer;
        }
        /** @return 原请求 ID */
        public UUID requestId(){return requestId;}
        /** @return 原协议 */
        public ProtocolKey protocol(){return protocol;}
        /** @return 原模型答案摘要 */
        public String sourceDigest(){return sourceDigest;}
        /** @return 冻结的有序步骤 */
        public List<Step> steps(){return steps;}
        /** @return 原插件版本 */
        public List<PluginRegistry.Token> tokens(){return tokens;}
        /** @return 已冻结语义答案 */
        public org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage answer(){return answer;}
    }
    /**
     * 编译包内部固定结果，原文校验由历史工厂再次执行。
     * @param requestId 请求
     * @param protocol 协议
     * @param sourceDigest 原文摘要
     * @param steps 步骤
     * @param tokens 插件版本
     * @param answer 语义答案
     * @return 不可伪造构造的结果
     */
    static Approved approved(UUID requestId, ProtocolKey protocol, String sourceDigest, List<Step> steps, List<PluginRegistry.Token> tokens,
            org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage answer) {
        return new Approved(requestId,protocol,sourceDigest,steps,tokens,answer);
    }
}
