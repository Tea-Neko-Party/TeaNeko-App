package org.zexnocs.teanekoagent.component.memory.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.component.memory.layered.LongTermMemoryAccess;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 注解式长期记忆查询和本人维护工具，权限与实际事务由访问服务负责。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：长期记忆工具仅对安装记忆组件的轮次开放。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：保留检索入口，画像与维护工具独立声明并复用原访问逻辑。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@TeaNekoAgentToolClass(value = "long-memory", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
public final class LongTermMemoryTools {
    private final LongTermMemoryAccess access;
    /**
     * 注入可信长期业务边界。
     * @param access 原访问服务
     */
    public LongTermMemoryTools(LongTermMemoryAccess access) { this.access = access; }
    /**
     * 判断当前真实请求能否查询，不接受 client 或 namespace 参数。
     * @param context 原宿主上下文
     * @return 当前可读取时为真
     */
    public boolean canRead(TeaNekoAgentToolContext context) { return access.canRead(context.request()); }
    /**
     * 仅原文明确提出遗忘时提供删除工具。
     * @param context 固定上下文
     * @return 本人明确遗忘时为真
     */
    public boolean canForget(TeaNekoAgentToolContext context) { return access.canChange(context.request(), false); }
    /**
     * 仅原文明确提出纠正时提供修改工具。
     * @param context 固定上下文
     * @return 本人明确纠正时为真
     */
    public boolean canCorrect(TeaNekoAgentToolContext context) { return access.canChange(context.request(), true); }
    /**
     * 通过主体和话题索引查询自动整理结果。
     * @param query 字面话题
     * @param subjectId 可选事实主体
     * @param limit 返回条数
     * @param context 原宿主上下文
     * @return 普通长期投影
     */
    @TeaNekoAgentTool(value = "memory_search", condition = "canRead")
    @Description("检索当前共享 namespace 自动整理的长期事实、偏好和经历，不按来源群隔离；优先使用话题关键词。结果可能不完整，CONFLICT 表示存在分歧，不能据空页认定从未发生。")
    public CompletionStage<LongMemories.Result> search(@DefaultValue("") @Description("话题关键词，最多1024字符。") String query,
            @Nullable @Description("可选 TeaNeko 用户 UUID，省略时查询共享空间相关主体。") UUID subjectId,
            @DefaultValue("8") @Description("返回条数，1至20。") int limit, TeaNekoAgentToolContext context) {
        return access.read(context.request(), subjectId, query, limit, context.requireInvocation().control());
    }
    /**
     * 当前用户画像是长期事实投影，不自行推测用户身份。
     * @param subjectId 可选主体，省略时使用真实发起者
     * @param context 固定上下文
     * @return 有来源的事实及偏好画像
     */
    // 2026/11/05：独立画像工具转发至原服务，保持主体默认值和读取预算。
    @Description("读取自动学习的长期用户画像；默认本人，也可明确查询共享空间其他 UUID。只含有证据的事实和偏好，不包含其他群原聊天或人格推测。")
    public CompletionStage<LongMemories.Result> profile(@Nullable @Description("可选真实 TeaNeko UUID，省略时使用当前用户。") UUID subjectId, TeaNekoAgentToolContext context) {
        return access.read(context.request(), subjectId == null ? context.request().actor().userId().orElseThrow() : subjectId, "", 12, context.requireInvocation().control());
    }
    /**
     * 按已查询的原版本遗忘本人条目。
     * @param id 记忆 UUID
     * @param revision 查询结果的版本
     * @param context 固定上下文
     * @return 原提交回执
     */
    // 2026/11/05：独立遗忘工具复用原调用标识和版本仲裁。
    @Description("仅在本轮用户原文以“忘记/请忘记/删除记忆/请删除记忆”明确要求时，按 UUID 和 revision 遗忘本人长期条目。先查询目标，含糊时澄清；不删除原聊天或他人记录，旧证据不能自动恢复该条目。")
    public CompletionStage<LongMemories.Item> forget(@Description("已查询到的本人记忆 UUID。") UUID id,
            @Description("查询结果中的原 revision。") long revision, TeaNekoAgentToolContext context) {
        return access.change(context.request(), context.requireCall().getId(), id, revision, null, context.requireInvocation().control());
    }
    /**
     * 按已查询的原版本纠正本人条目，防止后台自动覆盖人工更正。
     * @param id 记忆 UUID
     * @param revision 原版本
     * @param text 新正文
     * @param context 固定上下文
     * @return 原提交回执
     */
    // 2026/11/05：独立纠正工具复用原调用标识和版本仲裁。
    @Description("仅在本轮用户原文以“纠正/请纠正/更正/请更正”明确提出时，按 UUID 和 revision 修改本人长期记忆。正文最多512字，成功后保护人工更正，不授予修改他人记录的权限。")
    public CompletionStage<LongMemories.Item> correct(@Description("已查询到的本人记忆 UUID。") UUID id,
            @Description("原 revision，版本变化时先重新查询。") long revision,
            @Description("用户要求的更正正文，最多512字符。") String text, TeaNekoAgentToolContext context) {
        return access.change(context.request(), context.requireCall().getId(), id, revision, text, context.requireInvocation().control());
    }
}
