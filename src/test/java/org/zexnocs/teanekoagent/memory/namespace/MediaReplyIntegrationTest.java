package org.zexnocs.teanekoagent.memory.namespace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.memory.EasyDataDeliveryStore;
import org.zexnocs.teanekoagent.adapter.onebot.OnebotExpressionClient;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.agent.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptTranscript;
import org.zexnocs.teanekoagent.expression.*;
import org.zexnocs.teanekoagent.file_config.AgentExpressionFileConfig;
import org.zexnocs.teanekoagent.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoagent.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.history.AgentDeliveryService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult;
import org.zexnocs.teanekoagent.sticker.*;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.fake_client.FakeTeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IImageTeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 复用事务与平台夹具验证从完整目录到独立发送、回执、回避及实际文件占用的闭环。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：验证生产插件令牌往返、哈希碰撞键序和资源防篡改。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.7.6-l
 */
@Timeout(20)
class MediaReplyIntegrationTest{
    @TempDir Path directory;
    /** 2026/11/04：资源签名必须能经实际应用的 Jackson 模块往返，普通文字不能被表情资料阻断。 */
    @Test
    void resourcesRoundTripWithApplicationMapper() {
        try (var h = new Harness(directory, true); var control = ExecutionControl.create()) {
            h.scope.setEnabled(true);
            h.scope.setNativeExpressionsEnabled(true);
            for (int size = 2; size <= 8; size++) {
                var revisions = new LinkedHashMap<String, Long>();
                for (int index = 0; index < size; index++) {
                    StringBuilder key = new StringBuilder();
                    for (int bit = 0; bit < 3; bit++) {
                        key.append((index & (1 << bit)) == 0 ? "Aa" : "BB");
                    }
                    revisions.put(key.toString(), (long) index);
                }
                h.pluginToken.set(new org.zexnocs.teanekocore.plugin.PluginRegistry.Token(revisions));
                var request = h.request("网页资源验证" + size);
                var input = h.input(request, control);
                assertDoesNotThrow(() -> h.service.verify(request, input, control).toCompletableFuture().join());
                if (size == 2) {
                    // 2026/11/04：规范化只消除键顺序差异，不允许修改目录内容后沿用原签名。
                    var changed = input.fragments().stream().map(fragment -> fragment.name().equals(StickerReplyService.FRAGMENT)
                            ? new AgentPromptFragment(fragment.name(), fragment.text().replace("微笑", "篡改描述"),
                            fragment.expiresAt(), fragment.transientToolResults()) : fragment).toList();
                    var tampered = new AgentPromptInput(request.messages(), changed, 0);
                    assertThrows(CompletionException.class, () -> h.service.verify(request, tampered, control)
                            .toCompletableFuture().join());
                }
            }
        }
    }
    /**
     * 测试客户端显式声明媒体能力，实际编码直接借用已注册 OneBot 适配器。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.6.2-l
     */
    @TeaNekoExpressionClient(FakeTeaNekoClient.ID) public static final class ClientAdapter implements IExpressionClientAdapter {
        final OnebotExpressionClient delegate;volatile CompletableFuture<Void> pending;
        /** @param delegate 原容器编码器 */ public ClientAdapter(OnebotExpressionClient delegate){this.delegate=delegate;}
        /**
         * @param data 原目标
         * @return 明确支持图片
         */ @Override public boolean images(ITeaNekoMessageData data){return true;}
        /**
         * @param data 原目标
         * @param settings 设置
         * @return 明确原生目录
         */ @Override public Map<String,String> faces(ITeaNekoMessageData data, MediaReplySettings settings){return Map.of("14","微笑");}
        /**
         * @param data 原目标
         * @param bytes 原图编码
         * @param mode 方式
         * @return 可控真实确认
         */
        @Override public CompletionStage<Void> image(ITeaNekoMessageData data,String bytes,Mode mode){return delegate.image(data,bytes,mode).thenCompose(ignored->pending==null?CompletableFuture.completedStage(null):pending);}
        /**
         * @param data 原目标
         * @param id 原生 ID
         * @param mode 方式
         * @return 实际确认
         */
        @Override public CompletionStage<Void> face(ITeaNekoMessageData data,String id,Mode mode){return delegate.face(data,id,mode);}
    }
/**
     * 局部默认装配，所有文件和目录仲裁真实执行，只有原生来源由受控租约替身提供。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：增加生产序列化与插件令牌夹具，验证资源签名往返。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.7.6-l
     */
    private static final class Harness implements AutoCloseable{
        // 2026/11/04：在单个夹具内切换碰撞键集合，覆盖生产令牌的嵌套 Map。
        final java.util.concurrent.atomic.AtomicReference<org.zexnocs.teanekocore.plugin.PluginRegistry.Token> pluginToken =
                new java.util.concurrent.atomic.AtomicReference<>(new org.zexnocs.teanekocore.plugin.PluginRegistry.Token(Map.of()));
        final NamespaceDatabaseFixture db=new NamespaceDatabaseFixture();final AnnotationConfigApplicationContext context=new AnnotationConfigApplicationContext();
        final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();final ScheduledExecutorService timers=Executors.newSingleThreadScheduledExecutor();
        final ILogger logger=mock(ILogger.class);final FakeClient client=new FakeClient(logger);
        final AgentStickerFileConfig gallery=new AgentStickerFileConfig();final AgentMediaReplyFileConfig media=new AgentMediaReplyFileConfig();final AgentScopeConfigData scope=new AgentScopeConfigData();
        final Map<UUID,ITeaNekoMessageData> sources=new ConcurrentHashMap<>();
        final ExpressionRuntime runtime;final StickerReplyService service;final StickerReplyTools tools;final IStickerCatalog catalog;final LocalStickerAssets assets;
        final ReplyCoordinator queue;final ExpressionDelivery delivery;final EasyDataDeliveryStore ledger;
        /** @param path 测试目录 */
        Harness(Path path){
            this(path, false);
        }
        /**
         * @param path 测试目录
         * @param applicationMapper 是否使用生产序列化模块
         */
        Harness(Path path, boolean applicationMapper){
            gallery.setStorageRoot(path.toString());var expression=new AgentExpressionFileConfig();expression.setDefaultDelay(0);
            var files=mock(IFileConfigService.class);when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(gallery);when(files.requireCurrent(AgentMediaReplyFileConfig.class)).thenReturn(media);when(files.requireCurrent(AgentExpressionFileConfig.class)).thenReturn(expression);
            var configs=mock(IConfigDataService.class);doReturn(Optional.of(scope)).when(configs).getConfigData(any(),eq(AgentScopeConfigData.class),anyString());
            var bridge=mock(NativeCommandBridge.class);when(bridge.source(any())).thenAnswer(call->Optional.ofNullable(sources.get(((AgentRunRequest)call.getArgument(0)).requestId())));
            ledger=new EasyDataDeliveryStore(db.data,db.store,db.mapper);
            // 2026/11/04：复现生产模块与无模块夹具的序列化差异。
            var actualMapper = applicationMapper ? new org.zexnocs.teanekocore._app_config.JacksonConfig().objectMapper() : db.mapper;
            var beans=context.getBeanFactory();beans.registerSingleton("data",db.data);beans.registerSingleton("mapper",actualMapper);beans.registerSingleton("files",files);beans.registerSingleton("configs",configs);beans.registerSingleton("bridge",bridge);
            if (applicationMapper) {
                // 2026/11/04：生产具有包含依赖的插件令牌；哈希冲突键复现序列化往返中的 Map 顺序变化。
                var plugins = mock(org.zexnocs.teanekocore.plugin.PluginRegistry.class);
                var token = new org.zexnocs.teanekocore.plugin.PluginRegistry.Token(Map.of("Aa", 1L, "BB", 2L));
                pluginToken.set(token);
                when(plugins.capture(anyString())).thenAnswer(ignored -> pluginToken.get());
                when(plugins.isEnabled(anyString())).thenReturn(true);
                beans.registerSingleton("plugins", plugins);
            }
            beans.registerSingleton("manager",new AgentScopeConfigManager());beans.registerSingleton("virtualExecutor",workers);beans.registerSingleton("taskExecutor",timers);beans.registerSingleton("logger",logger);beans.registerSingleton("scanner",new BeanScanner(context,logger));
            beans.registerSingleton("ledger",new AgentDeliveryService(ledger,workers,Clock.systemUTC(),()->Duration.ofDays(7)));
            context.register(org.zexnocs.teanekoagent._app_config.AgentExpressionConfig.class,org.zexnocs.teanekoagent._app_config.AgentStickerConfig.class,
                    EasyDataStickerCatalog.class, LocalStickerAssets.class, WeightedStickerAdmission.class,OnebotExpressionClient.class,ClientAdapter.class,StickerReplyService.class,StickerReplyTools.class,MediaReplyFeature.class,
                    ExpressionReplyProtocol.class,TextReplyDirective.class,PlainReplyProtocol.class,JsonReplyProtocol.class,TextReplyHandler.class,ImageReplyHandler.class,StickerReplyDirective.class,FaceReplyDirective.class,StickerReplyHandler.class,FaceReplyHandler.class,ExpressionDelivery.class);
            context.refresh();verifyNoInteractions(files,configs,bridge);catalog=context.getBean(IStickerCatalog.class);assets=context.getBean(LocalStickerAssets.class);service=context.getBean(StickerReplyService.class);tools=context.getBean(StickerReplyTools.class);runtime=context.getBean(ExpressionRuntime.class);queue=context.getBean(ReplyCoordinator.class);delivery=context.getBean(ExpressionDelivery.class);
            db.store.create(new MemoryNamespace(MemoryNamespaceId.SHARED,0,0,Instant.now(),null,null),10).toCompletableFuture().join();
        }
        /**
         * @param group 真实区域
         * @return 独立请求及宿主来源绑定
         */
        AgentRunRequest request(String group){var data=client.handleGroupMessage(group,"用户","昵称",CommandPermission.MEMBER,"你好").getData();var actor=NativeCommandBridge.actor(data);var request=new AgentRunRequest(UUID.randomUUID(),actor,AgentActivity.COMMAND,List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("你好").build()).build()),null,null);sources.put(request.requestId(),data);return request;}
        /**
         * @param request 当前请求
         * @param control 当前控制
         * @return 原表达与真实资源资料
         */
        AgentPromptInput input(AgentRunRequest request,ExecutionControl control){var fragments=new ArrayList<AgentPromptFragment>();fragments.add(new AgentPromptFragment(ExpressionRuntime.FRAGMENT,runtime.contract(request)));fragments.addAll(service.fragments(request,control).toCompletableFuture().join());return new AgentPromptInput(request.messages(),fragments,0);}
        /**
         * @param value 区分原图的像素
         * @return 发布并激活的真实文件条目
         * @throws Exception 当本地图像编码失败时
         */
        Stickers.Entry seed(int value)throws Exception{
            var image=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,value);String url;try(var out=new ByteArrayOutputStream()){ImageIO.write(image,"png",out);url="data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());}
            var job=new Stickers.Job();job.setLease(UUID.randomUUID());job.setSource(new Stickers.Source(new AgentScope("test","来源"),UUID.randomUUID(),"用户","机器人","消息",0,Instant.now(),false,UUID.randomUUID(),url));
            var settings=gallery.snapshot();var identity=assets.identity(settings);try(var control=ExecutionControl.create()){var prepared=assets.prepare(job,settings,control);job.setBlob(prepared.blob());assets.publish(job,settings,control);}
            var e=new Stickers.Entry();e.setId("stk_"+job.getBlob().hash().substring(0,32));e.setHash(job.getBlob().hash());e.setBlob(job.getBlob());e.setState(Stickers.EntryStatus.ACTIVE);e.setCreated(Instant.now());e.setActivatedAt(Instant.now());e.setCachedUntil(Instant.now().plusSeconds(3600));e.setAnalysis(new Stickers.Analysis(Stickers.Kind.STICKER,.99,true,"猫的表情","开心地打招呼","happy",List.of(),.9,"适合聊天"));
            catalog.change(settings,s->{s.setStorageIdentity(identity);s.getEntries().put(e.getHash(),e);s.setRevision(s.getRevision()+1);return true;}).toCompletableFuture().join();return e;
        }
        /** @return 实际目录副本 */
        Stickers.State state(){return catalog.change(gallery.snapshot(),s->s).toCompletableFuture().join();}
        /**
         * @param request 原请求
         * @param input 原布局
         * @param control 原控制
         * @return 经真实注解参数绑定的完整回执
         */
        ToolInvocationResult list(AgentRunRequest request,AgentPromptInput input,ExecutionControl control){
            var definition=new AgentDefinition("测试",new LLMModelOptionsSnapshot(null),Set.of(AgentActivity.COMMAND,AgentActivity.TOOL));
            var runtime=new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(null),ModelCapabilities.textOnly(),LLMInteractionSettings.defaults());var bindings=context.getBean(MediaReplyFeature.class).tools().select(definition,request,runtime,input);
            var tool=new LLMToolCall("call_1","function","sticker_list","{\"categoryId\":\"happy\"}");return new LLMToolSet(db.mapper,bindings).execute(tool,new ToolInvocationContext(request.requestId(),EntitySnapshot.of(EntityId.random(),ComponentPatch.builder().put(AgentComponents.ACTOR,request.actor()).build()),control)).toCompletableFuture().join();
        }
        /**
         * @param request 原请求
         * @param input 原布局
         * @param result 实际工具回执
         * @param body 最终指令
         * @return 原交互及工具完整配对
         */
        LLMInteractionResult interaction(AgentRunRequest request,AgentPromptInput input,ToolInvocationResult result,String body){var messages= new ArrayList<>(input.messages());if(result!=null){messages.add(LLMAssistantMessage.builder().contents(List.of()).toolCalls(List.of(result.call())).build());messages.add(result.toMessage());}
            var answer=LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText("⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|BEGIN|protocol=teaneko:expression|v=1⟧\n"+body+"\n⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|END⟧").build()).build();messages.add(answer);return new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS,messages,answer,List.of(),result==null?List.of():List.of(result),Map.of());}
        /**
         * @param request 原请求
         * @param plan 固定计划
         * @return 使用真实持久账本的交付阶段
         */
        CompletionStage<Void> send(AgentRunRequest request,ReplyPlans.Approved plan){var result=mock(AgentRunResult.class);when(result.replyPlan()).thenReturn(plan);when(result.memoryReference()).thenReturn(new MemoryNamespaceReference(request.actor().scope(),MemoryNamespaceId.SHARED,0,0));
            return queue.execute("测试",request.actor(),request.requestId(),"fake-bot",control->delivery.send(sources.get(request.requestId()),request,result,control,()->{control.checkActive();return CompletableFuture.completedStage(null);}));}
        /** 释放原资源和夹具，不连接真实服务。 */
        @Override public void close(){context.close();workers.close();timers.shutdownNow();db.close();}
    }
    /**
     * 注解目录、冻结协议、独立图片/原生表情和真实逐段账本完整接通，重放不重复展示。
     * @throws Exception 当本地编码失败时
     */
    @Test void sendsQueriedStickerAndNativeFaceWithReceipts()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            var sticker=h.seed(1);var request=h.request("群甲");var input=h.input(request,control);var listing=h.list(request,input,control);assertEquals(ToolInvocationResult.Status.SUCCESS,listing.status());assertFalse(listing.truncated());
            var page=h.db.mapper.readValue(listing.output(),MediaReplies.Page.class);assertTrue(page.complete());assertEquals(sticker.getId(),page.items().getFirst().id());assertFalse(listing.output().contains("来源"));
            String n=ExpressionRuntime.nonce(request.requestId());var interaction=h.interaction(request,input,listing,"⟦TNK:"+n+"|TEXT|wait=0⟧\n好呀\n⟦TNK:"+n+"|STICKER|id="+sticker.getId()+"|mode=reply|wait=0⟧\n⟦TNK:"+n+"|FACE|id=14|wait=0⟧");
            var plan=Objects.requireNonNull(h.runtime.compile(request,input,interaction,control).toCompletableFuture().join());var transcript=AgentPromptTranscript.createExpression(input,interaction,new LLMMessageCodec(h.db.mapper),plan);String projected=h.db.mapper.writeValueAsString(transcript.messages());assertFalse(projected.contains("TNK:"));assertFalse(projected.contains(page.proof()));
            h.send(request,plan).toCompletableFuture().join();assertEquals(3,h.client.getSentMessages().size());var image=h.client.getSentMessages().get(1).getMessages();assertEquals(2,image.size());assertEquals(1,image.stream().filter(c->c.getContentPart() instanceof IReplyTeaNekoContentPart).count());assertTrue(image.stream().anyMatch(c->c.getContentPart() instanceof IImageTeaNekoContentPart p&&p.getUrl().startsWith("base64://")));
            assertEquals(1,h.client.getSentMessages().get(2).getMessages().size());assertEquals("face",h.client.getSentMessages().get(2).getMessages().getFirst().getType());assertEquals(1,h.state().getEntries().get(sticker.getHash()).getRobotShows());
            h.send(request,plan).toCompletableFuture().join();assertEquals(3,h.client.getSentMessages().size());assertEquals(1,h.state().getEntries().get(sticker.getHash()).getRobotShows());
            var next=h.request("群甲");assertFalse(h.service.canList(next,h.input(next,control)));var other=h.request("群乙");assertTrue(h.service.canList(other,h.input(other,control)));
        }
    }
    /**
     * 历史、截断、改写的目录及未查询 ID 无权发送；版本变化发生在整计划发送前拒绝。
     * @throws Exception 当本地编码失败时
     */
    @Test void rejectsStaleOrUnqueriedResourcesBeforeSending()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            var sticker=h.seed(2);var request=h.request("群甲");var input=h.input(request,control);String line="⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|STICKER|id="+sticker.getId()+"|wait=0⟧";
            assertThrows(CompletionException.class,()->h.runtime.compile(request,input,h.interaction(request,input,null,line),control).toCompletableFuture().join());
            var real=h.list(request,input,control);var truncated=new ToolInvocationResult(real.call(),real.status(),real.output(),true);assertThrows(CompletionException.class,()->h.runtime.compile(request,input,h.interaction(request,input,truncated,line),control).toCompletableFuture().join());
            var other=h.request("群乙");var otherInput=h.input(other,control);String otherLine="⟦TNK:"+ExpressionRuntime.nonce(other.requestId())+"|STICKER|id="+sticker.getId()+"|wait=0⟧";
            assertThrows(CompletionException.class,()->h.runtime.compile(other,otherInput,h.interaction(other,otherInput,real,otherLine),control).toCompletableFuture().join());
            var forged=new ToolInvocationResult(real.call(),real.status(),real.output().replace("开心地打招呼","另一种用途"),false);assertThrows(CompletionException.class,()->h.runtime.compile(request,input,h.interaction(request,input,forged,line),control).toCompletableFuture().join());
            var plan=Objects.requireNonNull(h.runtime.compile(request,input,h.interaction(request,input,real,line),control).toCompletableFuture().join());h.catalog.change(h.gallery.snapshot(),s->{s.getEntries().get(sticker.getHash()).setRevision(2);return true;}).toCompletableFuture().join();
            assertThrows(CompletionException.class,()->h.send(request,plan).toCompletableFuture().join());assertTrue(h.client.getSentMessages().isEmpty());assertTrue(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());
            var fresh=h.request("群甲");var freshInput=h.input(fresh,control);var freshTool=h.list(fresh,freshInput,control);String freshLine="⟦TNK:"+ExpressionRuntime.nonce(fresh.requestId())+"|STICKER|id="+sticker.getId()+"|wait=0⟧";
            var freshPlan=Objects.requireNonNull(h.runtime.compile(fresh,freshInput,h.interaction(fresh,freshInput,freshTool,freshLine),control).toCompletableFuture().join());h.assets.delete(Objects.requireNonNull(sticker.getBlob()),h.gallery.snapshot());
            assertThrows(CompletionException.class,()->h.send(fresh,freshPlan).toCompletableFuture().join());assertTrue(h.client.getSentMessages().isEmpty());assertTrue(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());
        }
    }
    /**
     * 取消已开始的发送不提前释放文件；原阶段确认后才记展示和释放，随后才允许物理回收。
     * @throws Exception 当本地编码失败时
     */
    @Test void keepsLeaseUntilPhysicalCompletionAndCountsOnlyConfirmation()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            var sticker=h.seed(3);var request=h.request("群甲");var input=h.input(request,control);var tool=h.list(request,input,control);var draft=new ReplyPlans.Draft(new MediaReplies.Sticker(sticker.getId(),null),Mode.PLAIN,0);
            var permit=h.service.sticker(new ReplyCompileContext(request,input,control,List.of(tool),List.of(draft)),sticker.getId()).toCompletableFuture().join();var pending=new CompletableFuture<Void>();h.context.getBean(ClientAdapter.class).pending=pending;
            var sending=h.service.sendSticker(new ReplySendContext(h.sources.get(request.requestId()),request,0,control,()->{control.checkActive();return CompletableFuture.completedStage(null);}),permit,Mode.PLAIN).toCompletableFuture();
            await().atMost(Duration.ofSeconds(5)).until(()->!h.client.getSentMessages().isEmpty());control.cancel();assertFalse(sending.isDone());assertEquals(0,h.state().getEntries().get(sticker.getHash()).getRobotShows());assertFalse(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());
            var controls=new ArrayList<ExecutionControl>();var physical=new ArrayList<CompletableFuture<Void>>();
            try{
                for(int i=0;i<8;i++){
                    var own=ExecutionControl.create();controls.add(own);var next=h.request("并发群"+i);var actual=h.input(next,own);var listed=h.list(next,actual,own);
                    var selected=h.service.sticker(new ReplyCompileContext(next,actual,own,List.of(listed),List.of(draft)),sticker.getId()).toCompletableFuture().join();
                    physical.add(h.service.sendSticker(new ReplySendContext(h.sources.get(next.requestId()),next,0,own,()->{own.checkActive();return CompletableFuture.completedStage(null);}),selected,Mode.PLAIN).toCompletableFuture());
                }
                await().atMost(Duration.ofSeconds(5)).until(()->h.client.getSentMessages().size()==8);assertFalse(physical.getLast().isDone());controls.getLast().cancel();assertThrows(CompletionException.class,()->physical.getLast().join());
                assertEquals(8,h.state().getEntries().get(sticker.getHash()).getReaders().size());
                h.catalog.change(h.gallery.snapshot(),s->{s.getEntries().get(sticker.getHash()).setState(Stickers.EntryStatus.RETIRED);return true;}).toCompletableFuture().join();var storage=h.context.getBean(StickerExtensionRegistry.class).storage(h.gallery.snapshot());
                var ports=new StickerExtensionRegistry.Selected(mock(IStickerAnalyzer.class),storage.assets(),h.context.getBean(WeightedStickerAdmission.class),storage.catalog(),storage.tokens());
                assertTrue(h.context.getBean(StickerLibrary.class).garbage(h.gallery.snapshot(),ports).toCompletableFuture().join().isEmpty());pending.complete(null);sending.join();for(int i=0;i<7;i++){physical.get(i).join();}
                assertTrue(h.state().getEntries().get(sticker.getHash()).getReaders().isEmpty());assertEquals(8,h.state().getEntries().get(sticker.getHash()).getRobotShows());
            }finally{pending.complete(null);controls.forEach(ExecutionControl::close);}
        }
    }
    /**
     * 类别不能完整装入预算时不给部分 ID；资源必须独立成条，原生目录变化立即拒绝旧许可。
     * @throws Exception 当本地编码失败时
     */
    @Test void respectsCompleteCategoryBudgetAndMediaSwitches()throws Exception{
        try(var h=new Harness(directory);var control=ExecutionControl.create()){
            h.media.setMaxCategoryCharacters(1024);for(int i=0;i<10;i++){h.seed(10+i);}
            var sad=h.seed(99);var thanks=h.seed(100);h.catalog.change(h.gallery.snapshot(),s->{
                for(var pair:Map.of(sad.getHash(),"sad",thanks.getHash(),"thanks").entrySet()){var e=s.getEntries().get(pair.getKey());var old=Objects.requireNonNull(e.getAnalysis());e.setAnalysis(new Stickers.Analysis(old.kind(),old.confidence(),true,old.visualDescription(),old.usageDescription(),pair.getValue(),old.secondaryTags(),old.quality(),old.reasons()));}return true;
            }).toCompletableFuture().join();
            var request=h.request("群甲");var input=h.input(request,control);var output=h.list(request,input,control);var page=h.db.mapper.readValue(output.output(),MediaReplies.Page.class);assertFalse(page.complete());assertTrue(page.items().isEmpty());assertEquals("CATEGORY_BUDGET",page.reason());
            var validPage=h.service.list(request,input,"sad",control).toCompletableFuture().join();assertTrue(validPage.complete());var validTool=new ToolInvocationResult(new LLMToolCall("call_2","function","sticker_list","{\"categoryId\":\"sad\"}"),ToolInvocationResult.Status.SUCCESS,h.db.mapper.writeValueAsString(validPage),false);
            assertEquals(sad.getId(),h.service.sticker(new ReplyCompileContext(request,input,control,List.of(output,validTool),List.of()),sad.getId()).toCompletableFuture().join().item().id());
            assertThrows(CompletionException.class,()->h.service.list(request,input,"thanks",control).toCompletableFuture().join());
            assertThrows(IllegalArgumentException.class,()->new StickerReplyDirective().parse(new ReplyPlans.Directive("STICKER",Map.of("id","valid_id","mode","at"),"")));
            var permit=h.service.face(new ReplyCompileContext(request,input,control),"14");h.scope.setNativeExpressionsEnabled(false);
            assertThrows(CompletionException.class,()->h.service.sendFace(new ReplySendContext(h.sources.get(request.requestId()),request,0,control,()->CompletableFuture.completedStage(null)),permit,Mode.PLAIN).toCompletableFuture().join());assertTrue(h.client.getSentMessages().isEmpty());
        }
    }
}
