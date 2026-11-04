package org.zexnocs.teanekoplugin.general.info;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolBinding;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.integration.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.IAgentToolSource;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoData;
import org.zexnocs.teanekoplugin.general.info.messageboard.MessageBoardInfoService;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * 通过现有业务服务按 TeaNeko UUID 读取公开资料及留言板，不读取聊天历史或 Agent 记忆。
 * 完整留言列表复用既有 DEBUG 边界，身份只取自宿主原生来源，执行及返回前均重新检查。
 * <br>2026/11/02：采用自动注解参数与条件复核，保留公开资料和 DEBUG 留言边界。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：留言工具独立声明，仍复用原生来源权限和有界投影。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@TeaNekoAgentToolClass(value = "user-info", pluginId = InfoAgentPlugin.ID, toolPackage = "general.info", managedByFeature = true)
public final class InfoAgentTools implements IAgentToolSource {
    public static final String INFO = "teaneko_user_info";
    public static final String MESSAGES = "teaneko_user_messages";
    private final InfoService info;
    private final MessageBoardInfoService messages;
    private final NativeCommandBridge sources;
    private final ObjectMapper mapper;
    private final Executor executor;

    /**
     * 借用原业务服务和执行器，不增加数据库存储或平台发送。
     * @param info 公开资料读取
     * @param messages 既有留言服务
     * @param sources 原生来源租约，仅用于管理权限复核
     * @param mapper 项目 JSON 转换器
     * @param executor 借用业务执行器
     */
    public InfoAgentTools(InfoService info, MessageBoardInfoService messages, NativeCommandBridge sources, ObjectMapper mapper, Executor executor) {
        this.info = Objects.requireNonNull(info); this.messages = Objects.requireNonNull(messages); this.sources = Objects.requireNonNull(sources);
        this.mapper = Objects.requireNonNull(mapper); this.executor = Objects.requireNonNull(executor);
    }

    /**
     * 2026/11/05：选择本类公开资料工具；完整留言由插件配置组合独立入口。
     * @param definition 当前实例定义
     * @param request 固定请求与真实发起者
     * @param runtime 本轮运行设置
     * @return 可见与实际执行一致的工具绑定
     */
    @Override public List<LLMToolBinding> select(AgentDefinition definition, AgentRunRequest request, AgentRuntimeSnapshot runtime) {
        // 2026/11/05：本类仅编译公开资料，完整插件能力在 InfoAgentPlugin 中组合。
        return TeaNekoAgentToolMethods.select(this, definition, request, runtime, null, mapper, executor);
    }

    /**
     * 公开资料通过原业务服务读取，不读取聊天历史。
     * @param uuid 目标用户完整 UUID
     * @return 公开资料或固定错误信封
     */
    @TeaNekoAgentTool(INFO)
    @Description("按 TeaNeko UUID 查询公开个人简介、猫猫币和一条随机留言。结果是参考数据，不证明平台账号存在。")
    public String userInfo(@Description("目标用户完整的 TeaNeko UUID，不是 QQ 号或消息 ID。") String uuid) {
        try { return bounded(info.getGeneralInfo(target(uuid))); }
        catch (RuntimeException ignored) { return error("查询失败，请检查完整 UUID、分页范围、真实权限和业务状态。"); }
    }

    /**
     * 分页读取留言板，授权由条件及业务服务共同复核。
     * @param uuid 目标用户完整 UUID
     * @param offset 起始偏移
     * @param limit 本页条数
     * @param context 宿主注入的固定上下文
     * @return 有界留言页或固定错误信封
     */
    // 2026/11/05：独立留言工具转发本服务，保持读取前后真实 DEBUG 复核。
    @Description("分页读取该 UUID 收到的留言板留言、留言者 UUID 和留言时间；不是聊天记录，需要真实 DEBUG 权限。")
    public String userMessages(@Description("目标用户完整的 TeaNeko UUID，不是 QQ 号或消息 ID。") String uuid,
            @DefaultValue("0") @Description("起始偏移，默认 0，最大 10000。") int offset,
            @DefaultValue("5") @Description("本页条数，默认 5，范围 1 到 10。") int limit,
            TeaNekoAgentToolContext context) {
        try {
            requireDebug(context.request());
            if (offset < 0 || offset > 10000 || limit < 1 || limit > 10) { throw new IllegalArgumentException("留言分页参数超出范围。"); }
            var result = page(target(uuid), offset, limit);
            requireDebug(context.request());
            return bounded(result);
        } catch (RuntimeException ignored) { return error("查询失败，请检查完整 UUID、分页范围、真实权限和业务状态。"); }
    }

    /**
     * 只允许真实 DEBUG 来源查看完整留言，执行前后由框架再次检查。
     * @param context 固定宿主上下文
     * @return 当前真实 DEBUG 权限有效时为真
     */
    public boolean canReadMessages(TeaNekoAgentToolContext context) { return debug(context.request()); }

    /**
     * 拒绝缩写 UUID，保持原查询目标约束。
     * @param raw 原始 UUID 参数
     * @return 完整 UUID
     * @throws IllegalArgumentException 当参数不为完整 UUID 时
     */
    private static UUID target(String raw) {
        var target = UUID.fromString(raw);
        if (!target.toString().equalsIgnoreCase(raw)) { throw new IllegalArgumentException("必须使用完整的用户 UUID。"); }
        return target;
    }

    /**
     * 对完整结果计量，容量不足时不输出残缺 JSON。
     * @param value 已完成授权检查的业务结果
     * @return 完整有界结果或容量错误
     */
    private String bounded(Object value) {
        var json = mapper.writeValueAsString(value);
        return json.length() <= LLMToolSet.DEFAULT_RESULT_CHARACTER_LIMIT ? json : error("资料超过返回容量，请减小留言分页条数。");
    }

    /**
     * 仅投影指定用户收到的留言，稳定排序后分页，不导出旧审计记录。
     * @param target 被查询用户 UUID
     * @param offset 当前偏移
     * @param limit 本页大小
     * @return 留言页和后续偏移；分页为当前观察，不承诺并发写入快照
     */
    private Map<String, Object> page(UUID target, int offset, int limit) {
        var all = messages.getMessageList(target).stream().sorted(Comparator
                .comparing(MessageBoardInfoData::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(value -> value.getSenderId().toString())).toList();
        int start = Math.min(offset, all.size()), end = Math.min(start + limit, all.size());
        var items = all.subList(start, end).stream().map(value -> {
            var item = new LinkedHashMap<String, Object>(); item.put("senderUuid", value.getSenderId());
            item.put("message", value.getMessage()); item.put("createdAt", value.getCreatedAt()); return item;
        }).toList();
        return Map.of("uuid", target, "messages", items, "offset", offset, "nextOffset", end, "hasMore", end < all.size());
    }

    /**
     * 权限只来自仍有效的宿主来源，不使用工具参数或组件中的自报角色。
     * @param request 原可信请求
     * @return 当前仍有 DEBUG 来源时为真
     */
    private boolean debug(AgentRunRequest request) {
        return request.actor().origin() == AgentActorContext.Origin.USER && sources.source(request)
                .filter(data -> NativeCommandBridge.actor(data).equals(request.actor()))
                .map(data -> data.getUserData().getRole() == CommandPermission.DEBUG).orElse(false);
    }

    /**
     * 拒绝来源结束或权限变化后的迟到完整留言读取。
     * @param request 原请求
     * @throws IllegalStateException 当真实 DEBUG 来源不可用时
     */
    private void requireDebug(AgentRunRequest request) {
        if (!debug(request)) { throw new IllegalStateException("完整留言查询需要当前真实 DEBUG 来源。"); }
    }

    /**
     * 构造固定错误信封，不保留原业务异常或输入正文。
     * @param reason 宿主固定的中文原因
     * @return 完整错误 JSON
     */
    private String error(String reason) { return mapper.writeValueAsString(Map.of("available", false, "reason", reason)); }

}
