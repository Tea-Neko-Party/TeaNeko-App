package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
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
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04����֤�Ӿ�������������ģ�����õ������ȡ�
 *
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 *
 * <br>2026/11/04：原业务夹具显式使用历史装配，继续回归旧行为。
 *
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Timeout(15)
class StickerAssetsAndModelTest {
    @TempDir Path directory;
    /**
     * 保留原动画、合成预览并执行预算、地址及取消约束。
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
            assertThrows(IllegalArgumentException.class,()->assets.prepare(job(gif(121)),settings,control));
            control.cancel();assertThrows(RuntimeException.class,()->assets.prepare(job(gif(2)),settings,control));
        }
    }
    /**
     * 完整局部装配构造期无 I/O；真实模型端口只有一次调用且无工具，结构异常不得入库。
     * 2026/11/05：显式确认必需结果的非空契约，避免直接解引用可空值。
     */
    @Test void assemblesLazilyAndUsesOneStrictVisualCall(){
        try(var db=new NamespaceDatabaseFixture();var agents=new AgentRegistry();var context=new AnnotationConfigApplicationContext();var control=ExecutionControl.create()){
            var file=new AgentStickerFileConfig();file.setStorageRoot(directory.toString());
            var files=mock(IFileConfigService.class);when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(file);
            var configs=mock(IConfigDataService.class);doReturn(Optional.of(new AgentScopeConfigData(null))).when(configs).getConfigData(any(),eq(AgentScopeConfigData.class),anyString());
            var runtime=new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(LLMModelOptions.builder().provider("test").model("视觉测试").maxTokens(4096).build()),ModelCapabilities.userImages(Set.of(LLMImageDetail.AUTO)),LLMInteractionSettings.defaults());
            var gate=new AgentAccessGate(agents,s->new AgentScopePolicy(true,true,AgentScopePolicy.Mode.PASSIVE_BASIC),d->new AgentReadiness(AgentReadiness.Status.READY,Optional.of(runtime)),mock(ILogger.class));
            var interactions=mock(ILLMInteractionService.class);var text=new AtomicReference<>(db.mapper.writeValueAsString(new Stickers.Analysis(Stickers.Kind.ILLUSTRATION,.99,false,"猫的插画","","",List.of(),.9,"主要用途是画作")));
            when(interactions.interact(any(),any(),any(),any(),any())).thenAnswer(call->{
                ILLMPrompt prompt=call.getArgument(0);LLMInteractionSettings limits=call.getArgument(3);
                assertEquals(1,limits.limits().maxModelCalls());assertEquals(0,limits.limits().maxToolCalls());assertTrue(Objects.requireNonNull(Objects.requireNonNull(prompt.getOptions()).getTools()).isEmpty());
                // 2026/11/04：视觉生成保留模型配置的完整输出额度。
                assertEquals(4096, Objects.requireNonNull(prompt.getOptions()).getMaxTokens());
                assertTrue(prompt.getMessages().getLast().getContents().stream()
                        .anyMatch(c -> c.getContentPart() instanceof ImageLLMContentPart));
                var answer=LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text.get()).build()).build();return CompletableFuture.completedStage(new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS,List.of(answer),answer,List.of(),List.of(),Map.of()));
            });
            var beans=context.getBeanFactory();beans.registerSingleton("data",db.data);beans.registerSingleton("mapper",db.mapper);beans.registerSingleton("files",files);beans.registerSingleton("configs",configs);
            beans.registerSingleton("manager",new AgentScopeConfigManager());beans.registerSingleton("gate",gate);beans.registerSingleton("factory",new AgentFactory(agents));beans.registerSingleton("interactions",interactions);
            beans.registerSingleton("virtualExecutor",mock(ExecutorService.class));beans.registerSingleton("taskExecutor",mock(ScheduledExecutorService.class));beans.registerSingleton("logger",mock(ILogger.class));beans.registerSingleton("scanner",new BeanScanner(context,mock(ILogger.class)));
            context.register(AgentStickerConfig.class, LLMStickerAnalyzer.class, EasyDataStickerCatalog.class,LocalStickerAssets.class,WeightedStickerAdmission.class);context.refresh();verifyNoInteractions(files,configs,interactions);assertTrue(agents.snapshots().isEmpty());
            var analyzer=context.getBean(LLMStickerAnalyzer.class);var source=job("https://gchat.qpic.cn/test").getSource();assertTrue(analyzer.ready(source,file.snapshot()));
            var prepared=new IStickerAssetStore.Prepared(new Stickers.Blob("0".repeat(64),"png",1,1,1,1),List.of("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII="),"静态图片");
            // 本测试使用直接执行域，避免模拟线程池吞掉异步解码。
            try(var direct=new LLMStickerAnalyzer(context.getBeanProvider(IAgentFactory.class),gate,interactions,files,configs,context.getBean(AgentScopeConfigManager.class),db.mapper,Runnable::run)){
                assertEquals(Stickers.Kind.ILLUSTRATION,direct.analyze(source,prepared,file.snapshot(),control).toCompletableFuture().join().kind());
                text.set("{\"kind\":\"STICKER\"}");assertThrows(CompletionException.class,()->direct.analyze(source,prepared,file.snapshot(),control).toCompletableFuture().join());control.checkActive();
            }
            verify(interactions,times(2)).interact(any(),any(),any(),any(),any());
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
        var writer=ImageIO.getImageWritersByFormatName("gif").next();try(var bytes=new ByteArrayOutputStream();var output=ImageIO.createImageOutputStream(bytes)){
            writer.setOutput(output);writer.prepareWriteSequence(null);for(int i=0;i<count;i++){var image=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,i);writer.writeToSequence(new IIOImage(image,null,null),null);}writer.endWriteSequence();output.flush();return "data:image/gif;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }finally{writer.dispose();}
    }
}
