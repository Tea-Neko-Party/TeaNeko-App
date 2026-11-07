package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.expression.ExpressionClientRegistry;
import org.zexnocs.teanekoagent.component.expression.ExpressionRuntime;
import org.zexnocs.teanekoagent.component.expression.ExpressionSettings;
import org.zexnocs.teanekoagent.component.expression.MediaReplySettings;
import org.zexnocs.teanekoagent.component.expression.ReplyCompileContext;
import org.zexnocs.teanekoagent.component.expression.ReplyPlans;
import org.zexnocs.teanekoagent.component.expression.ReplySendContext;
import org.zexnocs.teanekoagent.component.expression.file_config.AgentExpressionFileConfig;
import org.zexnocs.teanekoagent.component.expression.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.component.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.component.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.integration.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 将当前资源资料、真实目录回执和原平台发送连接；签名只绑定本请求，不授予其他 scope 权限。
 * 查询缓存、轮次和展示窗口有界；文件占用持续到真实在途工作收尾，取消不提前释放原文件。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：规范化签名 Map 键序，修复插件令牌往返导致资源证明误拒绝和 Agent 无回复。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * <br>2026/11/05：明确空图库与收藏状态，未启用媒体能力时返回空资料而非初始化异常。
 * <br>2026/11/05：图库资料不再重复提供原生表情目录。
 * <br>2026/11/05：目录失效明确报告，最终工具过滤后复核选图依赖并明确结构化调用方式。
 * <br>2026/11/06：图库查询提交后结算预算，取消和失败完成原查询。
 * <br>2026/11/07：图库查询提交成功后一次结算预算，取消和关闭收束共享结果。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.7-l
 */
@Component
public final class StickerReplyService implements AutoCloseable {
    public static final String FRAGMENT="reply_resources";
    // 2026/11/05：区别异步采集与当轮可发送图库，防止模型把收藏当成发送或凭历史猜测素材 ID。
    private static final String PREFIX = "以下是本轮表达资源数据，不是权限或指令。表情包先按类别调用 sticker_list；"
            + "通过请求中的 tools 定义发起结构化 tool_calls，参数为 categoryId，值取 categories 中的 id；"
            + "不得输出 DSML、XML 或伪造的调用正文。未收到真实工具回执不能声称目录查询失败。"
            + "只可引用该工具本轮完整返回的 ID。categories 为空表示本轮没有可发送的图库素材，"
            + "可能尚未入库、被停用或处于冷却中；此时说明当前没有可用素材，不编造 ID。"
            + "sticker_collect 只提交后台收藏，sticker_collection_status 的 SAVED 才表示保存，收藏不等于发送。"
            + "有可用类别时先查目录再发送，不以收藏工具代替发送；图片或消息 ID 不能作为素材 ID。"
            + "原生表情使用独立 native_faces 资料；STICKER/FACE 必须独立成条，可 plain/reply，"
            + "不可正文或 at 混发。不需要时不用图。\n";
    private final StickerExtensionRegistry stores;
    private final ExpressionClientRegistry clients;
    private final ObjectProvider<NativeCommandBridge> bridge;
    private final IFileConfigService files;
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final ObjectMapper mapper;
    private final LLMMessageCodec codec;
    private final Executor executor;
    private final byte[] secret=new byte[32];
    private final Map<UUID,Query> queries=new HashMap<>();
    private final Semaphore transfers=new Semaphore(8,true);
    private volatile boolean closed;
    /**
     * 只保存原设施和本进程签名密钥，构造期不读取配置、目录或客户端。
     * @param stores 素材存储选择
     * @param clients 客户端编码目录
     * @param bridge 懒原生来源
     * @param files 本地配置
     * @param configs 区域配置
     * @param manager 原声明
     * @param mapper 原转换器
     * @param executor 借用业务域
     */
    public StickerReplyService(StickerExtensionRegistry stores,ExpressionClientRegistry clients,ObjectProvider<NativeCommandBridge> bridge,
            IFileConfigService files,IConfigDataService configs,AgentScopeConfigManager manager,ObjectMapper mapper,@Qualifier("virtualExecutor") Executor executor){
        this.stores=stores;this.clients=clients;this.bridge=bridge;this.files=files;this.configs=configs;this.manager=manager;this.executor=executor;
        // 2026/11/04：资源证明按稳定键序序列化，插件令牌 Map 经 JSON 往返后仍产生相同签名字节。
        this.mapper = mapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.codec=new LLMMessageCodec(mapper);new SecureRandom().nextBytes(secret);
    }
    /**
     * 固定真实客户端目录及当前类别摘要，不把全部图库注入每轮模型。
     * 2026/11/05：保留空类别事实供模型说明，缺少原目标或媒体能力时不生成资源资料。
     * @param request 原请求
     * @param control 借用控制
     * @return 没有明确客户端能力时为空
     */
    public CompletionStage<List<AgentPromptFragment>> fragments(AgentRunRequest request,ExecutionControl control){
        return CompletableFuture.supplyAsync(() -> access(request), executor).thenComposeAsync(available -> {
            control.checkActive();
            if (available.isEmpty()) {
                return CompletableFuture.completedStage(List.of());
            }
            var a = available.orElseThrow();
            return a.storage().catalog().change(a.settings(),s->{
                control.checkActive();var usage=touch(s,request,a.data());boolean images=a.settings().enabled()&&a.scope().isStickerReplyEnabled()&&a.client().images();
                var categories=new ArrayList<MediaReplies.Category>();
                if(images){for(var category:new TreeMap<>(a.settings().categories()).entrySet()){
                    int count= eligible(s,a,usage,category.getKey()).size();if(count>0){categories.add(new MediaReplies.Category(category.getKey(),category.getValue(),count));}
                }}
                // 2026/11/05：原生表情资料与许可由表达组件独立提供，图库不重复授权。
                var faces=Map.<String,String>of();
                var binding=new MediaReplies.Binding(request.requestId(),target(request,a.data()),digest(a),s.getStorageIdentity(),clientDigest(a.client()),a.storage().tokens(),Instant.now().plusSeconds(600));
                var unsigned=new MediaReplies.Resources(binding,categories,faces,images,"");var resource=new MediaReplies.Resources(binding,categories,faces,images,sign(unsigned));
                return List.of(new AgentPromptFragment(FRAGMENT,PREFIX+mapper.writeValueAsString(resource),binding.expiresAt(),Set.of("sticker_list")));
            });
        },executor);
    }
    /**
     * 只检查实际保留资料，普通新素材增加不会使无关的文字回答失效。
     * 2026/11/05：运行复核同样保留目录失败上下文，不把资料失效报告为未知异常。
     * @param request 原请求
     * @param input 实际布局
     * @param control 原控制
     * @return 当前资料仍有效的阶段
     */
    public CompletionStage<Void> verify(AgentRunRequest request, AgentPromptInput input, ExecutionControl control) {
        return CompletableFuture.runAsync(() -> {
            control.checkActive();
            if (input.fragments().stream().anyMatch(fragment -> fragment.name().equals(FRAGMENT))) {
                checkedResources(request, input);
            }
        }, executor);
    }
    /**
     * 2026/11/05：未装配或空图库返回不可用，已经提供但校验失败的资料须明确报告。
     * @param request 原请求
     * @param input 实际布局
     * @return 本轮可使用目录工具时为真
     * @throws StickerReplyException 当已经提供的目录校验失败时
     */
    public boolean canList(AgentRunRequest request, @Nullable AgentPromptInput input) {
        if (input == null || input.fragments().stream().noneMatch(f -> f.name().equals(FRAGMENT))
                || input.fragments().stream().noneMatch(f -> f.name().equals(ExpressionRuntime.FRAGMENT))) {
            return false;
        }
        var resource = checkedResources(request, input);
        return resource.images() && !resource.categories().isEmpty();
    }

    /**
     * 2026/11/05：防止资料要求调用工具、最终白名单却没有该工具，复核不恢复已撤销权限。
     * @param request 原请求
     * @param input 最终资料布局
     * @param toolNames 最终获准的工具名称
     * @throws StickerReplyException 当可用图库的表达合同或查询工具缺失时
     */
    public void verifyTools(AgentRunRequest request, AgentPromptInput input, Set<String> toolNames) {
        if (input.fragments().stream().noneMatch(f -> f.name().equals(FRAGMENT))) {
            return;
        }
        var resource = checkedResources(request, input);
        if (resource.images() && !resource.categories().isEmpty()
                && (!toolNames.contains("sticker_list")
                || input.fragments().stream().noneMatch(f -> f.name().equals(ExpressionRuntime.FRAGMENT)))) {
            throw new StickerReplyException();
        }
    }

    /**
     * 为已提供的目录校验保留明确失败原因，不静默删除对应工具。
     * @param request 原请求
     * @param input 含资源片段的布局
     * @return 校验通过的资源
     * @throws StickerReplyException 当资源签名、配置或来源已失效时
     */
    private MediaReplies.Resources checkedResources(AgentRunRequest request, AgentPromptInput input) {
        try {
            return resources(request, input);
        } catch (RuntimeException failure) {
            throw new StickerReplyException(failure);
        }
    }
    /**
     * 同请求同类别复用原回执，至多展开配置允许的两个类别；预算不够返回明确不完整状态且不授权条目。
     * @param request 原请求
     * @param input 实际布局
     * @param category 当前提供的类别 ID
     * @param control 借用工具控制
     * @return 完整类别或没有条目的明确拒绝
     */
    public CompletionStage<MediaReplies.Page> list(AgentRunRequest request, AgentPromptInput input,
                                                   String category, ExecutionControl control) {
        // 2026/11/06：事务重试只重建候选页；内存预算、签名及共享结果在真实提交后结算一次。
        control.checkActive();
        var resources = resources(request, input);
        var access = access(request).orElseThrow();
        if (!resources.images() || resources.categories().stream().noneMatch(value -> value.id().equals(category))) {
            throw new IllegalArgumentException("类别不是本轮提供的可用类别。");
        }
        CompletableFuture<MediaReplies.Page> result;
        Query query;
        synchronized (queries) {
            if (closed) {
                throw new IllegalStateException("图库查询宿主已关闭。");
            }
            queries.values().removeIf(value -> value.expires.isBefore(Instant.now()));
            query = queries.get(request.requestId());
            if (query == null) {
                if (queries.size() >= 1024) {
                    throw new IllegalStateException("本轮媒体查询目录已满。");
                }
                query = new Query(resources.binding().expiresAt());
                queries.put(request.requestId(), query);
            }
            var previous = query.pages.get(category);
            if (previous != null) {
                return control.guard(previous.minimalCompletionStage());
            }
            if (query.pages.size() >= access.media().maxCategories()) {
                return CompletableFuture.failedStage(new IllegalArgumentException("本轮类别展开次数已达到上限。"));
            }
            result = new CompletableFuture<>();
            query.pages.put(category, result);
        }
        var original = query;
        var termination = control.onTermination(reason -> {
            synchronized (queries) {
                result.completeExceptionally(new ExecutionStoppedException(reason));
            }
        });
        CompletableFuture.completedFuture(null).thenCompose(ignored ->
            access.storage().catalog().change(access.settings(), state -> {
                control.checkActive();
                check(resources.binding(), request, access);
                var usage = touch(state, request, access.data());
                if (!state.getStorageIdentity().equals(resources.binding().storage())) {
                    throw new IllegalStateException("素材卷身份已经变化。");
                }
                var items = eligible(state, access, usage, category).stream()
                    .map(entry -> new MediaReplies.Listing(entry.getId(), entry.getRevision(), description(entry)))
                    .sorted(Comparator.comparing(MediaReplies.Listing::id)).toList();
                return new MediaReplies.Page(request.requestId(), resources.proof(), category,
                    state.getRevision(), items, true, "", "");
            })).thenAccept(candidate -> {
            long bytes = mapper.writeValueAsBytes(candidate).length + 512L;
            long inputBytes = codec.signature(input.messages()).bytes();
            String categoryReason = candidate.items().size() > access.settings().maxPerCategory()
                || mapper.writeValueAsString(candidate).length() + 128 > access.media().maxCategoryCharacters()
                ? "CATEGORY_BUDGET" : "";
            synchronized (queries) {
                control.checkActive();
                if (closed) {
                    throw new IllegalStateException("图库查询宿主已关闭。");
                }
                if (result.isDone()) {
                    return;
                }
                String reason = categoryReason;
                if (request.inputMaxBytes() > 0
                    && inputBytes + original.bytes + bytes + 1024 > request.inputMaxBytes()) {
                    reason = "INPUT_BUDGET";
                }
                var page = reason.isEmpty() ? candidate : new MediaReplies.Page(candidate.requestId(),
                    candidate.resourceProof(), category, candidate.catalogRevision(), List.of(), false, reason, "");
                var signed = new MediaReplies.Page(page.requestId(), page.resourceProof(), page.category(),
                    page.catalogRevision(), page.items(), page.complete(), page.reason(), sign(page));
                if (reason.isEmpty()) {
                    original.bytes += bytes;
                }
                result.complete(signed);
            }
        }).whenComplete((ignored, failure) -> {
            termination.close();
            if (failure != null) {
                result.completeExceptionally(failure);
            }
        });
        return result.minimalCompletionStage();
    }
    /**
     * 编译时只从本次成功且未截断的真实工具回执取 ID，再核对当前条目版本。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 原编译上下文
     * @param id 选择的素材 ID
     * @return 单项不可变许可
     */
    public CompletionStage<MediaReplies.Permit> sticker(ReplyCompileContext context, String id) {
        checkQuantity(context, true);
        var resource = resources(context.request(), context.input());
        var a = access(context.request()).orElseThrow();
        MediaReplies.Listing selected = null;
        for (var tool : context.tools()) {
            if (!tool.call().getName().equals("sticker_list") || tool.status() != ToolInvocationResult.Status.SUCCESS || tool.truncated()) {
                continue;
            }
            var page = mapper.readValue(tool.output(), MediaReplies.Page.class);
            var unsigned = new MediaReplies.Page(page.requestId(), page.resourceProof(), page.category(), page.catalogRevision(), page.items(), page.complete(), page.reason(), "");
            if (!page.requestId().equals(context.request().requestId()) || !page.resourceProof().equals(resource.proof()) || !valid(unsigned, page.proof())) {
                throw new IllegalArgumentException("本轮图库回执证明无效。");
            }
            if (!page.complete()) {
                continue;
            }
            for (var candidate : page.items()) {
                if (candidate.id().equals(id)) {
                    if (selected != null && !selected.equals(candidate)) {
                        throw new IllegalArgumentException("素材在本轮存在冲突版本。");
                    }
                    selected = candidate;
                }
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("只能使用本轮实际完整查询过的素材 ID。");
        }
        var item = selected;
        return a.storage().catalog().change(a.settings(), s -> {
            context.getControl().checkActive();
            check(resource.binding(), context.request(), a);
            var entry = s.getEntries().values().stream().filter(e -> e.getId().equals(item.id()) && e.getRevision() == item.revision()).findFirst().orElseThrow();
            var usage = touch(s, context.request(), a.data());
            if (!usable(entry, a, usage) || !description(entry).equals(item.description())) {
                throw new IllegalStateException("素材版本、类别或回避条件已经变化。");
            }
            return permit(resource.binding(), item(entry), true);
        });
    }
    /**
     * 旧图库宿主入口，新的原生表情资料由独立表达服务提供。
     * @param context 原编译上下文
     * @param id 原生 ID
     * @return 当前资料确实提供过的原生许可
     * @deprecated 2026/11/05：改用 NativeFaceService.face，不依赖图库资料。
     */
    @Deprecated(since = "4.8.3-l", forRemoval = false)
    public MediaReplies.Permit face(ReplyCompileContext context,String id){
        checkQuantity(context,false);var resource=resources(context.request(),context.input());var label=resource.faces().get(id);
        if(label==null){throw new IllegalArgumentException("原生表情不在本轮客户端目录。");}
        return permit(resource.binding(),new MediaReplies.Item(id,1,resource.binding().client(),label),false);
    }
    /**
     * 读取原图的实际阶段结束后才释放占用；图片单独编码，真实确认后另记机器人展示。
     * @param context 原发送目标和守卫
     * @param permit 已冻结许可
     * @param mode 独立方式
     * @return 原平台确认与资源释放
     */
    public CompletionStage<Void> sendSticker(ReplySendContext context, MediaReplies.Permit permit, org.zexnocs.teanekoagent.component.expression.AgentReplyPlan.Mode mode) {
        return CompletableFuture.runAsync(() -> acquireTransfer(context), executor).thenCompose(ignored -> {
            try {
                return sendStickerWithLease(context, permit, mode).whenComplete((done, failure) -> transfers.release());
            } catch (RuntimeException failure) {
                transfers.release();
                return CompletableFuture.failedStage(failure);
            }
        });
    }
    /**
     * 限制整个实例同时持有原图和编码的数量，等待只发生在借用虚拟域，不持有目录锁或文件占用。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 原请求控制
     */
    private void acquireTransfer(ReplySendContext context) {
        try {
            while (true) {
                context.getControl().checkActive();
                if (closed) {
                    throw new IllegalStateException("媒体发送宿主已关闭。");
                }
                if (transfers.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                    try {
                        context.getControl().checkActive();
                        if (closed) {
                            throw new IllegalStateException("媒体发送宿主已关闭。");
                        }
                        return;
                    } catch (RuntimeException failure) {
                        transfers.release();
                        throw failure;
                    }
                }
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待图片发送容量已中断。");
        }
    }
    /**
     * 已取得实例发送容量后的真实文件与平台生命周期，实际结束后由外层释放容量。
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 原发送上下文
     * @param permit 原许可
     * @param mode 独立方式
     * @return 实际原图发送和目录占用释放
     */
    private CompletionStage<Void> sendStickerWithLease(ReplySendContext context,MediaReplies.Permit permit,org.zexnocs.teanekoagent.component.expression.AgentReplyPlan.Mode mode){
        return context.guard().get().thenComposeAsync(ignored->{
            var a=verifyPermit(context,permit,true);
            if(!a.storage().assets().identity(a.settings()).equals(permit.binding().storage())){throw new IllegalStateException("素材卷与原计划不一致。");}
            return a.storage().catalog().change(a.settings(),s->{
                context.getControl().checkActive();check(permit.binding(),context.request(),a);var e=entry(s,permit.item());var usage=touch(s,context.request(),a.data());
                if(!usable(e,a,usage)||!s.getStorageIdentity().equals(permit.binding().storage())){throw new IllegalStateException("素材不再可发送。");}
                e.getReaders().values().removeIf(t->!t.isAfter(Instant.now()));if(e.getReaders().size()>=32){throw new IllegalStateException("素材发送占用已满。");}
                var token=UUID.randomUUID();e.getReaders().put(token,Instant.now().plusSeconds(600));return new Lease(a,token,Objects.requireNonNull(e.getBlob()),permit);
            });
        },executor).thenCompose(lease->{
            CompletionStage<Void> sending;
            try{sending=CompletableFuture.supplyAsync(()->lease.access().storage().assets().read(lease.blob(),lease.access().settings(),context.getControl()),executor)
                    .thenComposeAsync(bytes->context.guard().get().thenComposeAsync(ignored->{
                        var a=verifyPermit(context,permit,true);
                        return a.storage().catalog().change(a.settings(),s->{var e=entry(s,permit.item());if(!e.getReaders().containsKey(lease.token())||!usable(e,a,touch(s,context.request(),a.data()))){throw new IllegalStateException("素材发送占用或状态已失效。");}return true;})
                                .thenComposeAsync(checked->{context.getControl().checkActive();verifyPermit(context,permit,true);return clients.image(a.client(),a.data(),Base64.getEncoder().encodeToString(bytes),mode,a.media());},executor);
                    },executor),executor).thenComposeAsync(ignored->shown(context,lease),executor);}
            catch(RuntimeException failure){sending=CompletableFuture.failedStage(failure);}
            return releasing(lease,sending);
        });
    }
    /**
     * 保留旧许可的发送入口，生产表达处理器已使用独立服务。
     * @param context 原发送目标
     * @param permit 冻结原生许可
     * @param mode 独立方式
     * @return 实际平台确认
     * @deprecated 2026/11/05：改用 NativeFaceService.sendFace，许可必须来自对应服务。
     */
    @Deprecated(since = "4.8.3-l", forRemoval = false)
    public CompletionStage<Void> sendFace(ReplySendContext context,MediaReplies.Permit permit,org.zexnocs.teanekoagent.component.expression.AgentReplyPlan.Mode mode){
        return context.guard().get().thenComposeAsync(ignored->{var a=verifyPermit(context,permit,false);return clients.face(a.client(),a.data(),permit.item().id(),mode,a.media());},executor);
    }
    /**
     * @param context 原目标
     * @param lease 原占用
     * @return 幂等记录真实展示，不推断人类喜欢程度
     */
    private CompletionStage<Void> shown(ReplySendContext context, Lease lease) {
        var a = lease.access();
        return a.storage().catalog().change(a.settings(), s -> {
            var now = Instant.now();
            s.getShown().values().removeIf(t -> t.plusSeconds(600).isBefore(now));
            String key = context.request().requestId() + ":" + context.index();
            if (s.getShown().containsKey(key)) {
                return true;
            }
            if (s.getShown().size() >= 8192) {
                throw new IllegalStateException("机器人展示幂等窗口已满。");
            }
            s.getShown().put(key, now);
            var e = s.getEntries().get(lease.blob().hash());
            if (e != null) {
                e.setRobotShows(e.getRobotShows() + 1);
                e.setLastRobot(now);
            }
            var usage = touch(s, context.request(), a.data());
            usage.getUsed().put(permitId(lease.permit()), usage.getRequests().get(context.request().requestId()));
            return true;
        }).thenAccept(ignored -> {
        });
    }
    /**
     * @param lease 原文件占用
     * @param physical 实际在途工作
     * @return 保留原失败且等清理完成的阶段
     */
    private CompletionStage<Void> releasing(Lease lease, CompletionStage<Void> physical) {
        return physical.handle((ignored, failure) -> failure).thenCompose(failure -> lease.access().storage().catalog().change(lease.access().settings(), s -> {
            var e = s.getEntries().get(lease.blob().hash());
            if (e != null) {
                e.getReaders().remove(lease.token());
            }
            return true;
        }).handle((ignored, cleanup) -> {
            if (failure != null) {
                if (cleanup != null) {
                    failure.addSuppressed(cleanup);
                }
                throw new CompletionException(failure);
            }
            if (cleanup != null) {
                throw new CompletionException(cleanup);
            }
            return null;
        }));
    }
    /**
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 原发送
     * @param permit 许可
     * @param sticker 预期种类
     * @return 当前匹配的宿主端口
     */
    private Access verifyPermit(ReplySendContext context,MediaReplies.Permit permit,boolean sticker){
        context.getControl().checkActive();if(permit.sticker()!=sticker||!valid(new MediaReplies.Permit(permit.binding(),permit.item(),sticker,""),permit.proof())){throw new IllegalArgumentException("资源许可不属于本次发送类型。");}
        var a=access(context.request()).orElseThrow();check(permit.binding(),context.request(),a);
        if(a.data()!=context.data()||sticker&&(!a.settings().enabled()||!a.scope().isStickerReplyEnabled()||!a.client().images())
                ||!sticker&&(!a.scope().isNativeExpressionsEnabled()||!Objects.equals(a.client().faces().get(permit.item().id()),permit.item().description()))){throw new IllegalStateException("原发送来源或资源开关已失效。");}return a;
    }
    /**
     * @param request 原请求
     * @param input 实际布局
     * @return 校验过的当轮资源资料
     */
    private MediaReplies.Resources resources(AgentRunRequest request,AgentPromptInput input){
        var text=input.fragments().stream().filter(f->f.name().equals(FRAGMENT)).findFirst().orElseThrow().text();
        if(!text.startsWith(PREFIX)){throw new IllegalArgumentException("本轮资源资料格式无效。");}var r=mapper.readValue(text.substring(PREFIX.length()),MediaReplies.Resources.class);
        if(!valid(new MediaReplies.Resources(r.binding(),r.categories(),r.faces(),r.images(),""),r.proof())){throw new IllegalArgumentException("本轮资源资料证明无效。");}
        check(r.binding(),request,access(request).orElseThrow());return r;
    }
    /**
     * @param binding 原绑定
     * @param request 原请求
     * @param a 当前宿主
     */
    private void check(MediaReplies.Binding binding,AgentRunRequest request,Access a){
        stores.verifyStorage(a.storage());clients.verify(a.client(),a.data(),a.media());
        if(closed||!binding.requestId().equals(request.requestId())||!binding.target().equals(target(request,a.data()))||!binding.settings().equals(digest(a))
                ||!binding.client().equals(clientDigest(a.client()))||!binding.tokens().equals(a.storage().tokens())||!binding.expiresAt().isAfter(Instant.now())){throw new IllegalStateException("资源目录、配置、原目标或插件版本已经变化。");}
    }
    /**
     * @param request 原请求
     * @return 当前已启用表达且有明确客户端适配的宿主
     */
    private Optional<Access> access(AgentRunRequest request){
        if(closed||request.activity()!=AgentActivity.COMMAND&&request.activity()!=AgentActivity.AUTOMATIC_REPLY){return Optional.empty();}
        var source=bridge.getObject().source(request).orElse(null);if(source==null){return Optional.empty();}
        var scope=configs.getConfigData(manager,AgentScopeConfigData.class,request.actor().scope().scopeId()).orElse(null);
        var media=files.requireCurrent(AgentMediaReplyFileConfig.class).snapshot();var expression=files.requireCurrent(AgentExpressionFileConfig.class).snapshot();
        if(scope==null||!scope.isEnabled()||!scope.isExpressionEnabled()||!media.enabled()||!expression.enabled()){return Optional.empty();}
        var client=clients.view(source,media).orElse(null);if(client==null){return Optional.empty();}var settings=files.requireCurrent(AgentStickerFileConfig.class).snapshot();
        return Optional.of(new Access(source,scope,settings,media,expression,client,stores.storage(settings)));
    }
    /**
     * @param s 原目录
     * @param request 原请求
     * @param source 原目标
     * @return 当前区域有界轮次
     */
    private Stickers.ScopeUsage touch(Stickers.State s,AgentRunRequest request,ITeaNekoMessageData source){
        String key=scopeKey(request,source);var now=Instant.now();s.getScopeUses().values().removeIf(u->u.getUpdated().plus(Duration.ofDays(7)).isBefore(now));
        if(!s.getScopeUses().containsKey(key)&&s.getScopeUses().size()>=1024){var old=s.getScopeUses().entrySet().stream().filter(e->e.getValue().getUpdated().plusSeconds(600).isBefore(now)).min(Comparator.comparing(e->e.getValue().getUpdated())).orElseThrow(()->new IllegalStateException("媒体区域轮次窗口已满。"));s.getScopeUses().remove(old.getKey());}
        var usage=s.getScopeUses().computeIfAbsent(key,k->new Stickers.ScopeUsage());usage.setUpdated(now);
        if(!usage.getRequests().containsKey(request.requestId())){usage.setTurn(Math.incrementExact(usage.getTurn()));usage.getRequests().put(request.requestId(),usage.getTurn());while(usage.getRequests().size()>16){usage.getRequests().remove(usage.getRequests().keySet().iterator().next());}}
        usage.getUsed().values().removeIf(t->usage.getTurn()-t>16);return usage;
    }
    /**
     * @param s 原目录
     * @param a 当前限制
     * @param usage 当前轮次
     * @param category 主类别
     * @return 本轮全部可用条目
     */
    private List<Stickers.Entry> eligible(Stickers.State s,Access a,Stickers.ScopeUsage usage,String category){return s.getEntries().values().stream().filter(e->usable(e,a,usage)&&Objects.requireNonNull(e.getAnalysis()).primaryCategory().equals(category)).toList();}
    /**
     * @param e 条目
     * @param a 当前开关
     * @param usage 区域记录
     * @return 当前可选时为真
     */
    private boolean usable(Stickers.Entry e,Access a,Stickers.ScopeUsage usage){
        var blob=e.getBlob();var analysis=e.getAnalysis();var last=usage.getUsed().get(e.getId());
        return e.getState()==Stickers.EntryStatus.ACTIVE&&blob!=null&&blob.bytes()<=a.settings().maxFileBytes()
                &&analysis!=null&&a.settings().categories().containsKey(analysis.primaryCategory())&&(last==null||usage.getTurn()-last>a.media().cooldownTurns());
    }
    /**
     * @param s 原目录
     * @param item 原条目
     * @return 版本及指纹仍相同的实体数据
     */
    private Stickers.Entry entry(Stickers.State s,MediaReplies.Item item){var e=s.getEntries().get(item.hash());if(e==null||!e.getId().equals(item.id())||e.getRevision()!=item.revision()){throw new IllegalStateException("素材条目版本已失效。");}return e;}
    /**
     * @param entry 有效条目
     * @return 无来源的完整用途投影
     */
    private static MediaReplies.Item item(Stickers.Entry entry){return new MediaReplies.Item(entry.getId(),entry.getRevision(),entry.getHash(),description(entry));}
    /**
     * 提供画面摘要与完整用途，避免同类图片只剩相同情绪标签；省略画面尾部时明确标记。
     * @param entry 实际条目
     * @return 保留完整用途的紧凑描述
     */
    private static String description(Stickers.Entry entry){
        var analysis=Objects.requireNonNull(entry.getAnalysis());String visual=analysis.visualDescription();int end=Math.min(48,visual.length());
        if(end<visual.length()&&Character.isHighSurrogate(visual.charAt(end-1))){end--;}
        return "画面："+visual.substring(0,end)+(end<visual.length()?"…":"")+"；用途："+analysis.usageDescription();
    }
    /**
     * @param binding 原绑定
     * @param item 固定条目
     * @param sticker 种类
     * @return 单项宿主签名
     */
    private MediaReplies.Permit permit(MediaReplies.Binding binding,MediaReplies.Item item,boolean sticker){var unsigned=new MediaReplies.Permit(binding,item,sticker,"");return new MediaReplies.Permit(binding,item,sticker,sign(unsigned));}
    /**
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param context 全部草稿
     * @param sticker 要检查的种类
     */
    private void checkQuantity(ReplyCompileContext context,boolean sticker){
        context.getControl().checkActive();var ids=context.drafts().stream().map(ReplyPlans.Draft::content).filter(c->sticker?c instanceof MediaReplies.Sticker:c instanceof MediaReplies.Face)
                .map(c->c instanceof MediaReplies.Sticker s?s.id():((MediaReplies.Face)c).id()).toList();
        if(ids.size()>(sticker?files.requireCurrent(AgentMediaReplyFileConfig.class).snapshot().maxPerReply():4)||ids.stream().distinct().count()!=ids.size()){throw new IllegalArgumentException("本轮媒体数量超限或重复使用同一 ID。");}
    }
    /**
     * @param a 当前宿主
     * @return 不包含原设置文本的摘要
     */
    private String digest(Access a){return StableDigest.sha256(mapper.writeValueAsString(a.settings()),mapper.writeValueAsString(a.media()),a.expression().toString(),Boolean.toString(a.scope().isStickerReplyEnabled()),Boolean.toString(a.scope().isNativeExpressionsEnabled()));}
    /**
     * @param view 明确客户端目录
     * @return 包含插件代次的稳定摘要
     */
    private String clientDigest(ExpressionClientRegistry.View view){return StableDigest.sha256(mapper.writeValueAsString(view));}
    /**
     * @param request 原请求
     * @param data 原数据
     * @return 固定发送身份摘要
     */
    private static String target(AgentRunRequest request,ITeaNekoMessageData data){return StableDigest.sha256(request.actor().toString(),data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse(""));}
    /**
     * @param request 原请求
     * @param data 原目标
     * @return 只按区域与机器人账号隔离的展示键
     */
    private static String scopeKey(AgentRunRequest request,ITeaNekoMessageData data){return StableDigest.sha256(request.actor().scope().toString(),data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse(""));}
    /**
     * @param permit 原许可
     * @return 素材 ID
     */
    private static String permitId(MediaReplies.Permit permit){return permit.item().id();}
    /**
     * @param value 结构化数据
     * @return 不可由模型仿造的本进程证明
     */
    private String sign(Object value){try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(mapper.writeValueAsBytes(value)));}catch(GeneralSecurityException failure){throw new IllegalStateException("宿主资源签名设施不可用。");}}
    /**
     * @param value 不含证明的数据
     * @param proof 原签名
     * @return 常量时间比较是否相同
     */
    private boolean valid(Object value,String proof){return proof!=null&&MessageDigest.isEqual(sign(value).getBytes(StandardCharsets.US_ASCII),proof.getBytes(StandardCharsets.US_ASCII));}
    /** 关闭只撤销本进程查询缓存，原文件占用继续由真实在途阶段释放。 */
    @Override
    public void close() {
        // 2026/11/07：关闭必须收束尚未提交的共享查询，迟到提交不能重新签发许可。
        List<CompletableFuture<MediaReplies.Page>> pending;
        synchronized (queries) {
            closed = true;
            pending = queries.values().stream().flatMap(query -> query.pages.values().stream()).toList();
            queries.clear();
        }
        pending.forEach(result -> result.completeExceptionally(new IllegalStateException("图库查询宿主已关闭。")));
    }
    /**
     * 当前受控宿主，只在方法链中传递。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param data 原目标
     * @param scope 当前区域限制
     * @param settings 素材预算
     * @param media 媒体预算
     * @param expression 表达预算
     * @param client 当前客户端
     * @param storage 原文件与目录
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType private record Access(ITeaNekoMessageData data,AgentScopeConfigData scope,StickerSettings settings,MediaReplySettings media,ExpressionSettings expression,ExpressionClientRegistry.View client,StickerExtensionRegistry.Storage storage){}
    /**
     * 实际文件在途占用，不进入计划或模型。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param access 原后端
     * @param token 原占用
     * @param blob 原文件
     * @param permit 原许可
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType private record Lease(Access access,UUID token,Stickers.Blob blob,MediaReplies.Permit permit){}
    /**
     * 有界请求内类别缓存，不保存聊天或执行控制。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    private static final class Query{
        private final Instant expires;private long bytes;private final Map<String,CompletableFuture<MediaReplies.Page>> pages=new HashMap<>();
        /** @param expires 原资料到期 */ private Query(Instant expires){this.expires=expires;}
    }
}
