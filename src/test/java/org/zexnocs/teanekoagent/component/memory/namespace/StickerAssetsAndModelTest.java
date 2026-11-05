package org.zexnocs.teanekoagent.component.memory.namespace;

import org.zexnocs.teanekoagent.component.llm.framework.structured.LLMJsonTestSupport;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.zexnocs.teanekoagent.component.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.component.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.component.sticker.*;
import org.zexnocs.teanekoagent.component.sticker.configuration.AgentStickerConfig;
import org.zexnocs.teanekoagent.component.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.IAgentFactory;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证有界动画处理、受信地址和真实受限模型适配器，不连接外部服务。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04����֤�Ӿ�������������ģ�����õ������ȡ�
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 * <br>2026/11/04：原业务夹具显式使用历史装配，继续回归旧行为。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
 * <br>2026/11/05：局部装配补入客户端缓存目录，匹配生产配置的现有依赖。
 * <br>2026/11/05：复用视觉调用夹具验证模型状态、HTTP 诊断传递和错误正文脱敏。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/06：适配真实类型化 JSON 服务，保留原领域与提交边界回归。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.3-l
 */
@Timeout(15)
class StickerAssetsAndModelTest {
    @TempDir Path directory;
    /**
     * 保留原动画、合成预览并执行预算、地址及取消约束。
     * 2026/11/05：小子帧长动画可以采集，超出帧数硬限制时报告具体数字。
     * @throws Exception 当本地编码失败时
     */
    @Test void handlesAnimationAndRejectsUnsafeInputs()throws Exception{
        var config=new AgentStickerFileConfig();config.setStorageRoot(directory.toString());var settings=config.snapshot();var assets=new LocalStickerAssets();
        try(var control=ExecutionControl.create()){
            assertEquals(assets.identity(settings),assets.identity(settings));var job=job(gif(2));var prepared=assets.prepare(job,settings,control);
            assertEquals(2,prepared.blob().frames());assertEquals(2,prepared.previews().size());job.setBlob(prepared.blob());assets.publish(job,settings,control);assertTrue(assets.exists(prepared.blob(),settings));
            assets.delete(prepared.blob(),settings);assertFalse(assets.exists(prepared.blob(),settings));
            assertThrows(IllegalArgumentException.class,()->assets.prepare(job("http://127.0.0.1/private.png"),settings,control));
            assertThrows(IllegalArgumentException.class,()->assets.prepare(job("file:///private.png"),settings,control));
            assertThrows(IllegalArgumentException.class,()->assets.prepare(job("data:image/png;base64,"+Base64.getEncoder().encodeToString("并非图片".getBytes(java.nio.charset.StandardCharsets.UTF_8))),settings,control));
            assertEquals(121, assets.prepare(job(gif(121)), settings, control).blob().frames());
            var optimized = assets.prepare(job(gif(40, 1024)), settings, control);
            assertEquals(1024, optimized.blob().width());
            assertEquals(40, optimized.blob().frames());
            assertEquals(4, optimized.previews().size());
            var tooMany = assertThrows(IllegalArgumentException.class, () -> assets.prepare(job(gif(601)), settings, control));
            assertTrue(FailureDiagnostic.from(tooMany).description().contains("601"));
            assertTrue(FailureDiagnostic.from(tooMany).description().contains("600"));
            control.cancel();assertThrows(RuntimeException.class,()->assets.prepare(job(gif(2)),settings,control));
        }
    }
    /**
     * 完整局部装配构造期无 I/O；真实模型端口只有一次调用且无工具，结构异常不得入库。
     * 2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
     * 2026/11/05：只提供现有客户端目录依赖，测试不连接真实客户端。
     * 2026/11/05：失败状态保留原 HTTP 状态和位置，解析错误不记录模型或供应商正文。
     */
    @Test void assemblesLazilyAndUsesOneStrictVisualCall(){
        try(var db=new NamespaceDatabaseFixture();var agents=new AgentRegistry();var context=new AnnotationConfigApplicationContext();var control=ExecutionControl.create()){
            var file=new AgentStickerFileConfig();file.setStorageRoot(directory.toString());
            var files=mock(IFileConfigService.class);when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(file);
            var configs=mock(IConfigDataService.class);doReturn(Optional.of(new AgentScopeConfigData(null))).when(configs).getConfigData(any(),eq(AgentScopeConfigData.class),anyString());
            var runtime=new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("视觉测试").maxTokens(4096).build()),ModelCapabilities.userImages(Set.of(LLMImageDetail.AUTO)),LLMInteractionSettings.defaults());
            var gate=new AgentAccessGate(agents,s->new AgentScopePolicy(true,true,AgentScopePolicy.Mode.PASSIVE_BASIC),d->new AgentReadiness(AgentReadiness.Status.READY,Optional.of(runtime)),mock(ILogger.class));
            var interactions=mock(ILLMInteractionService.class);var text=new AtomicReference<>(db.mapper.writeValueAsString(new Stickers.Analysis(Stickers.Kind.ILLUSTRATION,.99,false,"猫的插画","","",List.of(),.9,"主要用途是画作")));
            // 2026/11/05：在同一真实分析流程中切换终止结果，不再创建重复装配夹具。
            var status = new AtomicReference<>(LLMInteractionResult.Status.SUCCESS);
            var diagnostic = new AtomicReference<FailureDiagnostic>();
            when(interactions.interact(any(),any(),any(),any(),any())).thenAnswer(call->{
                ILLMPrompt prompt=call.getArgument(0);LLMInteractionSettings limits=call.getArgument(3);
                assertEquals(1,limits.limits().maxModelCalls());assertEquals(0,limits.limits().maxToolCalls());assertTrue(Objects.requireNonNull(Objects.requireNonNull(prompt.getOptions()).getTools()).isEmpty());
                // 2026/11/04：视觉生成保留模型配置的完整输出额度。
                assertEquals(4096, Objects.requireNonNull(prompt.getOptions()).getMaxTokens());
                assertTrue(prompt.getMessages().getLast().getContents().stream()
                        .anyMatch(c -> c.getContentPart() instanceof ImageLLMContentPart));
                var answer = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text.get()).build()).build();
                return CompletableFuture.completedStage(new LLMInteractionResult(status.get(), List.of(answer), answer,
                        List.of(), List.of(), Map.of()).withFailure(diagnostic.get()));
            });
            var json = LLMJsonTestSupport.create(interactions, db.mapper);
            var beans=context.getBeanFactory();beans.registerSingleton("json", json);beans.registerSingleton("data",db.data);beans.registerSingleton("mapper",db.mapper);beans.registerSingleton("files",files);beans.registerSingleton("configs",configs);
            beans.registerSingleton("manager",new AgentScopeConfigManager());beans.registerSingleton("gate",gate);beans.registerSingleton("factory",new AgentFactory(agents));beans.registerSingleton("interactions",interactions);
            beans.registerSingleton("virtualExecutor",mock(ExecutorService.class));beans.registerSingleton("taskExecutor",mock(ScheduledExecutorService.class));beans.registerSingleton("logger",mock(ILogger.class));beans.registerSingleton("scanner",new BeanScanner(context,mock(ILogger.class)));
            // 2026/11/05：采集配置已改用客户端缓存目录，局部上下文同步提供该依赖。
            beans.registerSingleton("clients", mock(TeaNekoClientScanner.class));
            context.register(AgentStickerConfig.class, LLMStickerAnalyzer.class, EasyDataStickerCatalog.class,LocalStickerAssets.class,WeightedStickerAdmission.class);context.refresh();verifyNoInteractions(files,configs,interactions);assertTrue(agents.snapshots().isEmpty());
            var analyzer=context.getBean(LLMStickerAnalyzer.class);var source=job("https://gchat.qpic.cn/test").getSource();assertTrue(analyzer.ready(source,file.snapshot()));
            var prepared=new IStickerAssetStore.Prepared(new Stickers.Blob("0".repeat(64),"png",1,1,1,1),List.of("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII="),"静态图片");
            // 本测试使用直接执行域，避免模拟线程池吞掉异步解码。
            try(var direct=new LLMStickerAnalyzer(context.getBeanProvider(IAgentFactory.class),gate,json,files,configs,context.getBean(AgentScopeConfigManager.class),db.mapper,Runnable::run)){
                assertEquals(Stickers.Kind.ILLUSTRATION,direct.analyze(source,prepared,file.snapshot(),control).toCompletableFuture().join().kind());
                text.set("{\"kind\":\"STICKER\",\"private-value\":\"private-model-output\"}");
                var invalid = assertThrows(CompletionException.class,
                        () -> direct.analyze(source, prepared, file.snapshot(), control).toCompletableFuture().join());
                assertTrue(FailureDiagnostic.from(invalid).description().contains("缺少必需字段"));
                assertFalse(FailureDiagnostic.from(invalid).toString().contains("private-model-output"));

                status.set(LLMInteractionResult.Status.TRUNCATED);
                var truncated = assertThrows(CompletionException.class,
                        () -> direct.analyze(source, prepared, file.snapshot(), control).toCompletableFuture().join());
                assertTrue(FailureDiagnostic.from(truncated).description().contains("TRUNCATED"));
                assertTrue(FailureDiagnostic.from(truncated).description().contains("max-tokens"));

                status.set(LLMInteractionResult.Status.TIMED_OUT);
                var timedOut = assertThrows(CompletionException.class,
                        () -> direct.analyze(source, prepared, file.snapshot(), control).toCompletableFuture().join());
                assertTrue(FailureDiagnostic.from(timedOut).description().contains("执行时限"));
                assertTrue(FailureDiagnostic.from(timedOut).description().contains(control.id().toString()));

                var denied = WebClientResponseException.create(403, "Forbidden", HttpHeaders.EMPTY,
                        "private-provider-response".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
                var original = FailureDiagnostic.from(denied);
                diagnostic.set(original);
                status.set(LLMInteractionResult.Status.FAILED);
                var failed = assertThrows(CompletionException.class,
                        () -> direct.analyze(source, prepared, file.snapshot(), control).toCompletableFuture().join());
                var carried = FailureDiagnostic.from(failed);
                assertEquals(Integer.valueOf(403), carried.httpStatus());
                assertEquals(original.location(), carried.location());
                assertTrue(carried.types().contains(denied.getClass().getName()));
                assertTrue(carried.description().contains("模型状态=FAILED"));
                assertTrue(carried.description().contains("HTTP 403"));
                assertFalse(carried.toString().contains("private-provider-response"));
                assertFalse(carried.toString().contains("private-model-output"));
                control.checkActive();
            }
            verify(interactions, times(5)).interact(any(), any(), any(), any(), any());
        }
    }
    /**
     * @param url 可信引用或待拒绝输入
     * @return 带独立文件租约的作业
     */
    private Stickers.Job job(String url){var user=UUID.randomUUID();var job=new Stickers.Job();job.setLease(UUID.randomUUID());job.setSource(new Stickers.Source(new AgentScope("test","群"),user,"用户","机器人","消息",0,Instant.now(),false,user,url));return job;}
    /**
     * @param count 帧数
     * @return 本地编码动画
     * @throws Exception 当编码失败时
     */
    private String gif(int count)throws Exception{
        // 2026/11/05：默认小画布与优化子帧动画共用编码夹具。
        return gif(count, 4);
    }

    /**
     * 2026/11/05：独立设置逻辑画布和真实子帧，复现画布乘帧数的错误预算。
     * @param count 帧数
     * @param canvasSize 逻辑画布边长，实际子帧固定为四像素
     * @return 不访问网络的 GIF 数据 URI
     * @throws Exception 当编码失败时
     */
    private String gif(int count, int canvasSize) throws Exception {
        var writer=ImageIO.getImageWritersByFormatName("gif").next();try(var bytes=new ByteArrayOutputStream();var output=ImageIO.createImageOutputStream(bytes)){
            writer.setOutput(output);
            var metadata = writer.getDefaultStreamMetadata(null);
            var tree = (javax.imageio.metadata.IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_stream_1.0");
            var screen = (javax.imageio.metadata.IIOMetadataNode) tree.getElementsByTagName("LogicalScreenDescriptor").item(0);
            screen.setAttribute("logicalScreenWidth", Integer.toString(canvasSize));
            screen.setAttribute("logicalScreenHeight", Integer.toString(canvasSize));
            metadata.setFromTree("javax_imageio_gif_stream_1.0", tree);
            writer.prepareWriteSequence(metadata);
            for (int index = 0; index < count; index++) {
                var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
                image.setRGB(0, 0, index);
                // 2026/11/05：覆盖子帧恢复，确保无需为每个四像素子帧复制整个逻辑画布。
                var frameMetadata = writer.getDefaultImageMetadata(new javax.imageio.ImageTypeSpecifier(image), null);
                var frameTree = (javax.imageio.metadata.IIOMetadataNode)
                        frameMetadata.getAsTree("javax_imageio_gif_image_1.0");
                var disposal = (javax.imageio.metadata.IIOMetadataNode)
                        frameTree.getElementsByTagName("GraphicControlExtension").item(0);
                disposal.setAttribute("disposalMethod", "restoreToPrevious");
                frameMetadata.setFromTree("javax_imageio_gif_image_1.0", frameTree);
                writer.writeToSequence(new IIOImage(image, null, frameMetadata), null);
            }
            writer.endWriteSequence();
            output.flush();
            return "data:image/gif;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        }finally{writer.dispose();}
    }
}
