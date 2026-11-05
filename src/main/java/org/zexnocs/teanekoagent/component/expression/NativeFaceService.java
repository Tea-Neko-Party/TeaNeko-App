package org.zexnocs.teanekoagent.component.expression;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.expression.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoagent.component.sticker.MediaReplies;
import org.zexnocs.teanekoagent.integration.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 原生客户端表情的只读目录及当轮许可，不依赖图库、文件卷或图库事务。
 * <br>2026/11/05：使用独立证明固定原请求、目录和客户端插件版本，实际发送前重新复核。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
@Component
public final class NativeFaceService {
    public static final String FRAGMENT = "native_faces";
    private static final String PREFIX = "客户端原生表情目录；仅用 FACE 指令选择其中 ID，不属于图库素材。\n";
    private final ExpressionClientRegistry clients;
    private final ObjectProvider<NativeCommandBridge> bridge;
    private final IFileConfigService files;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final ObjectProvider<ExpressionRuntime> expressions;
    private final ObjectMapper mapper;
    private final byte[] secret = new byte[32];

    /**
     * 借用现有配置、来源与客户端目录，创建进程内签名密钥。
     * @param clients 已发布的客户端能力目录
     * @param bridge 原消息来源查询
     * @param files 当前媒体配置
     * @param configs 当前聊天配置
     * @param manager 配置声明
     * @param expressions 表达运行合同
     * @param mapper 项目序列化器
     */
    public NativeFaceService(ExpressionClientRegistry clients, ObjectProvider<NativeCommandBridge> bridge,
            IFileConfigService files, IConfigDataService configs, AgentScopeConfigManager manager,
            ObjectProvider<ExpressionRuntime> expressions, ObjectMapper mapper) {
        this.clients = clients;
        this.bridge = bridge;
        this.files = files;
        this.configs = configs;
        this.manager = manager;
        this.expressions = expressions;
        this.mapper = mapper.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        new SecureRandom().nextBytes(secret);
    }

    /**
     * 为获准请求生成只读目录及当轮证明。
     * @param request 原请求
     * @return 有明确来源且已启用的只读目录，不触及图库
     */
    public List<AgentPromptFragment> fragments(AgentRunRequest request) {
        var available = access(request);
        if (available.isEmpty()) { return List.of(); }
        var current = available.orElseThrow();
        var binding = new MediaReplies.Binding(request.requestId(), target(request, current.data()),
                StableDigest.sha256(mapper.writeValueAsString(current.settings())), "native-faces",
                StableDigest.sha256(mapper.writeValueAsString(current.client())), current.client().tokens(),
                Instant.now().plusSeconds(600));
        var unsigned = new MediaReplies.Resources(binding, List.of(), current.client().faces(), false, "");
        var directory = new MediaReplies.Resources(binding, List.of(), unsigned.faces(), false, sign(unsigned));
        return List.of(new AgentPromptFragment(FRAGMENT, PREFIX + mapper.writeValueAsString(directory), binding.expiresAt()));
    }

    /**
     * 实际采用原生资料时重新验证来源和版本。
     * @param request 原请求
     * @param input 实际保留的布局
     */
    public void verify(AgentRunRequest request, AgentPromptInput input) {
        if (input.fragments().stream().anyMatch(fragment -> fragment.name().equals(FRAGMENT))) {
            directory(request, input);
        }
    }

    /**
     * 校验整轮数量和所选 ID，冻结独立发送许可。
     * @param context 原编译上下文
     * @param id 模型选取的原生 ID
     * @return 当轮许可，不能由模型伪造
     */
    public MediaReplies.Permit face(ReplyCompileContext context, String id) {
        context.getControl().checkActive();
        var ids = context.drafts().stream().map(ReplyPlans.Draft::content).filter(MediaReplies.Face.class::isInstance)
                .map(content -> ((MediaReplies.Face) content).id()).toList();
        if (ids.size() > 4 || ids.size() != ids.stream().distinct().count()) {
            throw new IllegalArgumentException("本轮原生表情超出数量上限或重复使用同一 ID。");
        }
        var directory = directory(context.request(), context.input());
        var description = directory.faces().get(id);
        if (description == null) { throw new IllegalArgumentException("原生表情不在本轮已授权目录。"); }
        var item = new MediaReplies.Item(id, 1, directory.binding().client(), description);
        var unsigned = new MediaReplies.Permit(directory.binding(), item, false, "");
        return new MediaReplies.Permit(unsigned.binding(), item, false, sign(unsigned));
    }

    /**
     * 等待公共守卫后复核许可，借用客户端发送并等待确认。
     * @param context 原发送目标、控制及守卫
     * @param permit 当轮固定许可
     * @param mode 独立发送方式
     * @return 平台实际确认阶段
     */
    public CompletionStage<Void> sendFace(ReplySendContext context, MediaReplies.Permit permit, AgentReplyPlan.Mode mode) {
        context.getControl().checkActive();
        return context.guard().get().thenCompose(ignored -> {
            context.getControl().checkActive();
            if (permit.sticker() || !valid(new MediaReplies.Permit(permit.binding(), permit.item(), false, ""), permit.proof())) {
                throw new IllegalArgumentException("原生表情许可无效。");
            }
            var current = checked(context.request(), permit.binding());
            if (context.data() != current.data() || !Objects.equals(current.client().faces().get(permit.item().id()),
                    permit.item().description())) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
            return clients.face(current.client(), current.data(), permit.item().id(), mode, current.settings());
        });
    }

    /**
     * 仅解析当轮保留的签名资料，拒绝旧目录和伪造内容。
     * @param request 原请求
     * @param input 原布局
     * @return 验签并复核后的目录
     */
    private MediaReplies.Resources directory(AgentRunRequest request, AgentPromptInput input) {
        var text = input.fragments().stream().filter(fragment -> fragment.name().equals(FRAGMENT)).findFirst()
                .orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.UNAVAILABLE)).text();
        if (!text.startsWith(PREFIX)) { throw new IllegalArgumentException("原生表情目录格式无效。"); }
        var directory = mapper.readValue(text.substring(PREFIX.length()), MediaReplies.Resources.class);
        if (!valid(new MediaReplies.Resources(directory.binding(), List.of(), directory.faces(), false, ""), directory.proof())) {
            throw new IllegalArgumentException("原生表情目录证明无效。");
        }
        var current = checked(request, directory.binding());
        if (!current.client().faces().equals(directory.faces())) { throw new AgentPromptException(AgentPromptException.Reason.CHANGED); }
        return directory;
    }

    /**
     * 固定请求、真实目标、当前配置及客户端代次必须全部相符。
     * @param request 原请求
     * @param binding 固定目标及版本
     * @return 仍获准的当前客户端
     */
    private Access checked(AgentRunRequest request, MediaReplies.Binding binding) {
        var current = access(request).orElseThrow(() -> new AgentPromptException(AgentPromptException.Reason.CHANGED));
        clients.verify(current.client(), current.data(), current.settings());
        if (!binding.requestId().equals(request.requestId()) || !binding.target().equals(target(request, current.data()))
                || !binding.storage().equals("native-faces") || !binding.expiresAt().isAfter(Instant.now())
                || !binding.settings().equals(StableDigest.sha256(mapper.writeValueAsString(current.settings())))
                || !binding.client().equals(StableDigest.sha256(mapper.writeValueAsString(current.client())))
                || !binding.tokens().equals(current.client().tokens())) {
            throw new AgentPromptException(AgentPromptException.Reason.CHANGED);
        }
        return current;
    }

    /**
     * 读取当前授权，不因存在客户端目录而扩大表达能力。
     * @param request 原请求
     * @return 当前获准的原生目标，无适配器或关闭时为空
     */
    private Optional<Access> access(AgentRunRequest request) {
        if (!expressions.getObject().enabled(request)) { return Optional.empty(); }
        var source = bridge.getObject().source(request).orElse(null);
        var scope = configs.getConfigData(manager, AgentScopeConfigData.class, request.actor().scope().scopeId()).orElse(null);
        var settings = files.requireCurrent(AgentMediaReplyFileConfig.class).snapshot();
        if (source == null || scope == null || !scope.isEnabled() || !scope.isNativeExpressionsEnabled() || !settings.enabled()) {
            return Optional.empty();
        }
        return clients.view(source, settings).filter(view -> !view.faces().isEmpty())
                .map(view -> new Access(source, view, settings));
    }

    /**
     * 将真实发送身份及机器人账号绑定到许可。
     * @param request 原请求
     * @param data 原数据
     * @return 真实身份和机器人账号摘要
     */
    private static String target(AgentRunRequest request, ITeaNekoMessageData data) {
        return StableDigest.sha256(request.actor().toString(), data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse(""));
    }

    /**
     * 对固定数据计算进程内签名，不导出密钥。
     * @param value 固定许可或目录
     * @return 进程内签名
     */
    private String sign(Object value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(value)));
        } catch (GeneralSecurityException failure) { throw new IllegalStateException("原生表情签名设施不可用。", failure); }
    }

    /**
     * 用常量时间比较验证数据未被替换。
     * @param value 原固定数据
     * @param proof 原签名
     * @return 常量时间比较结果
     */
    private boolean valid(Object value, String proof) {
        return MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.US_ASCII), proof.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 当前原生目标及只读参数，不持有图库或文件状态。
     * @param data 原消息
     * @param client 当前目录
     * @param settings 当前媒体参数
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record Access(ITeaNekoMessageData data, ExpressionClientRegistry.View client, MediaReplySettings settings) { }
}
