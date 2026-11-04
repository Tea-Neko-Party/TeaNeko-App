package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。


import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.component.expression.AgentReplyPlan.Mode;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptFragment;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptTranscript;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionDelays;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 核验注解代理、扩展分派、语义历史与取消后的真实顺序。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Timeout(15)
class ExpressionProtocolTest {
    /**
     * 局部注册表达组件，不启动应用和数据库。
     * @param calls 代理调用数
     * @return 测试拥有的容器
     */
    private AnnotationConfigApplicationContext context(AtomicInteger calls) {
        var context=new AnnotationConfigApplicationContext();
        context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor(){
            /**
             * 为文本解析器保留实际接口代理。
             * 2026/11/05：移除包迁移后重复或未使用的类型导入。
             * @param bean 原实例
             * @param name 名称
             * @return 原对象或代理
             */
            @Override public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String name){
                if(!(bean instanceof TextReplyDirective)){return bean;}
                var proxy=new ProxyFactory(bean);proxy.setInterfaces(IReplyDirectiveParser.class);
                proxy.addAdvice((MethodInterceptor) invocation->{calls.incrementAndGet();return invocation.proceed();});return proxy.getProxy();
            }
        });
        context.register(ExpressionReplyProtocol.class,TextReplyDirective.class,PlainReplyProtocol.class,JsonReplyProtocol.class,
                TextReplyHandler.class,ImageReplyHandler.class,CustomDirective.class,CustomProtocol.class);
        // 完整配置组合不访问实际文件、数据库或线程池；真实并发由下方独立用例覆盖。
        var files=mock(org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService.class);
        org.mockito.Mockito.when(files.requireCurrent(org.zexnocs.teanekoagent.component.expression.file_config.AgentExpressionFileConfig.class)).thenReturn(new org.zexnocs.teanekoagent.component.expression.file_config.AgentExpressionFileConfig());
        var configs=mock(org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService.class);
        org.mockito.Mockito.doReturn(Optional.of(new org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData())).when(configs)
                .getConfigData(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.eq(org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData.class),org.mockito.ArgumentMatchers.anyString());
        context.getBeanFactory().registerSingleton("files",files);context.getBeanFactory().registerSingleton("configs",configs);
        context.getBeanFactory().registerSingleton("manager",new org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager());
        context.getBeanFactory().registerSingleton("mapper",JsonMapper.builder().build());
        context.getBeanFactory().registerSingleton("virtualExecutor",mock(ExecutorService.class));context.getBeanFactory().registerSingleton("taskExecutor",mock(ScheduledExecutorService.class));
        context.getBeanFactory().registerSingleton("scanner",new BeanScanner(context,mock(ILogger.class)));
        var plugins=new org.zexnocs.teanekocore.plugin.PluginRegistry(List.of(
                ()->new org.zexnocs.teanekocore.plugin.PluginDescriptor("teaneko.agent","1",1,Set.of(),true),
                ()->new org.zexnocs.teanekocore.plugin.PluginDescriptor("test.extra","1",1,Set.of())));
        context.getBeanFactory().registerSingleton("plugins",plugins);
        context.register(org.zexnocs.teanekoagent.component.expression.configuration.AgentExpressionConfig.class,ExpressionFeature.class);
        context.refresh();org.mockito.Mockito.verifyNoInteractions(files,configs);return context;
    }
    /** @return 真实身份和独立原文组成的请求 */
    private AgentRunRequest request(){
        var actor=new AgentActorContext(new AgentScope("test","群甲"),AgentActorContext.Origin.USER,Optional.of(new UUID(0,1)),"用户","消息1","群甲");
        return new AgentRunRequest(UUID.randomUUID(),actor,AgentActivity.COMMAND,List.of(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("你好").build()).build()),null,null);
    }
    /**
     * 构造与实际布局一致的完整链。
     * @param input 实际输入
     * @param text 模型正文
     * @return 冻结交互
     */
    private LLMInteractionResult interaction(AgentPromptInput input,String text){
        var answer=LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build();
        var transcript= new ArrayList<>(input.messages());transcript.add(answer);
        return new LLMInteractionResult(LLMInteractionResult.Status.SUCCESS,transcript,answer,List.of(),List.of(),Map.of());
    }
    /**
     * 仅测试调用者同步观察真实异步编译，生产代码保持阶段组合。
     * @param runtime 编译器
     * @param request 原请求
     * @param input 实际输入
     * @param interaction 原交互
     * @return 完整计划
     */
    private ReplyPlans.Approved compiled(ExpressionRuntime runtime, AgentRunRequest request, AgentPromptInput input, LLMInteractionResult interaction) {
        try (var control=ExecutionControl.create()) {
            return Objects.requireNonNull(runtime.compile(request,input,interaction,control).toCompletableFuture().join());
        }
    }
    /** 新增类即可分派，保留代理；原模型链与无控制符历史都经过校验。 */
    @Test void scansExtensionsCompilesAndProjectsHistory(){
        var calls=new AtomicInteger();
        try(var context=context(calls)){
            var registry=context.getBean(ReplyExtensionRegistry.class);
            var mapper=JsonMapper.builder().build();var runtime=new ExpressionRuntime(registry,ExpressionSettings::defaults,scope->true,mapper);
            var request=request();String head="⟦TNK:"+ExpressionRuntime.nonce(request.requestId())+"|";
            var input=new AgentPromptInput(request.messages(),List.of(new AgentPromptFragment(ExpressionRuntime.FRAGMENT,runtime.contract(request),null)),0);
            var raw=interaction(input,head+"BEGIN|protocol=teaneko:expression|v=1⟧\n"+head+"TEXT|mode=reply|wait=0⟧\n好呀\n"+head+"CUSTOM|wait=60000⟧\n扩展正文\n"+head+"END⟧");
            var plan=Objects.requireNonNull(compiled(runtime,request,input,raw));
            assertEquals(List.of("好呀","扩展正文"),plan.steps().stream().map(ReplyPlans.Step::text).toList());
            assertEquals(3000,plan.steps().getLast().waitMillis());assertTrue(calls.get()>0);
            var codec=new LLMMessageCodec(mapper);var projected=AgentPromptTranscript.createExpression(input,raw,codec,plan);
            assertFalse(mapper.writeValueAsString(projected.revalidate(codec)).contains("⟦TNK:"));
            assertTrue(mapper.writeValueAsString(raw.transcript()).contains("⟦TNK:"));
            var custom=Objects.requireNonNull(compiled(runtime,request,input,interaction(input,head+"BEGIN|protocol=test:echo|v=1⟧\n"+head+"END⟧")));
            assertEquals("新增协议可用",custom.steps().getFirst().text());
            var plugins=context.getBean(org.zexnocs.teanekocore.plugin.PluginRegistry.class);
            plugins.setEnabled("test.extra",false);assertThrows(IllegalStateException.class,()->runtime.verify(custom));
            plugins.setEnabled("test.extra",true);assertThrows(IllegalStateException.class,()->runtime.verify(custom));
            context.registerBean("重复文本指令",TextReplyDirective.class);
            assertThrows(IllegalArgumentException.class,()->new ReplyExtensionRegistry(new BeanScanner(context,mock(ILogger.class)),null));
        }
    }
    /** 非法控制失败，代码围栏及显式转义不触发指令。 */
    @Test void rejectsMalformedControlAndPreservesLiteralCode(){
        try(var context=context(new AtomicInteger())){
            var runtime=new ExpressionRuntime(context.getBean(ReplyExtensionRegistry.class),ExpressionSettings::defaults,scope->true,JsonMapper.builder().build());
            var request=request();var nonce=ExpressionRuntime.nonce(request.requestId());String head="⟦TNK:"+nonce+"|";
            var input=new AgentPromptInput(request.messages(),List.of(new AgentPromptFragment(ExpressionRuntime.FRAGMENT,runtime.contract(request),null)),0);
            for(String body:List.of(head+"TEXT|wait=1|wait=2⟧\n正文",head+"UNKNOWN⟧\n正文",head+"TEXT|wait=-1⟧\n正文")){
                assertThrows(IllegalArgumentException.class,()->compiled(runtime,request,input,interaction(input,head+"BEGIN|protocol=teaneko:expression|v=1⟧\n"+body+"\n"+head+"END⟧")));
            }
            String fence=String.valueOf((char)96).repeat(3);
            String literal=head+"BEGIN|protocol=teaneko:expression|v=1⟧\n"+head+"TEXT⟧\n"+fence+"text\n"+head+"TEXT|wait=99⟧\n"+fence+"\n\\"+head+"FACE⟧\n"+head+"END⟧";
            var plan=Objects.requireNonNull(compiled(runtime,request,input,interaction(input,literal)));
            assertEquals(1,plan.steps().size());assertTrue(plan.steps().getFirst().text().contains(head+"FACE⟧"));
            assertThrows(IllegalArgumentException.class,()->compiled(runtime,request,input,interaction(input,literal.replace(nonce,"000000000000"))));
        }
    }
    /**
     * 取消中间项不让后项越过前项，其他 scope 并发且停止立即结束等待。
     * @throws Exception 当有界异步验证失败时
     */
    @Test void preservesOrderAcrossCancellation() throws Exception {
        try(var workers=Executors.newVirtualThreadPerTaskExecutor();var timers=Executors.newSingleThreadScheduledExecutor();
            var coordinator=new ReplyCoordinator(workers,timers,Clock.systemUTC(),ExpressionSettings::defaults)){
            var actor=request().actor();var firstId=UUID.randomUUID();var secondId=UUID.randomUUID();
            var entered=new CountDownLatch(1);var order=new CopyOnWriteArrayList<String>();
            var first=coordinator.execute("测试",actor,firstId,"机器人",control->{entered.countDown();return ExecutionDelays.waitFor(control,timers,Duration.ofSeconds(5)).thenRun(()->order.add("旧尾部"));});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            var second=coordinator.execute("测试",actor,secondId,"机器人",control->{order.add("已取消项");return CompletableFuture.completedStage(null);});
            assertTrue(coordinator.cancel(actor,secondId));
            var third=coordinator.execute("测试",actor,UUID.randomUUID(),"机器人",control->{order.add("后项");return CompletableFuture.completedStage(null);});
            var other=new AgentActorContext(new AgentScope("test","群乙"),actor.origin(),actor.userId(),"用户","消息2","群乙");
            coordinator.execute("测试",other,UUID.randomUUID(),"机器人",control->CompletableFuture.completedStage(null)).toCompletableFuture().get(2,TimeUnit.SECONDS);
            assertFalse(third.toCompletableFuture().isDone());assertTrue(coordinator.cancel(actor,firstId));
            assertThrows(CompletionException.class,()->first.toCompletableFuture().join());assertThrows(CompletionException.class,()->second.toCompletableFuture().join());
            third.toCompletableFuture().get(2,TimeUnit.SECONDS);assertEquals(List.of("后项"),order);
        }
    }
    /**
     * 测试用新增指令。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @TeaNekoReplyDirective("CUSTOM")
    static class CustomDirective implements IReplyDirectiveParser {
        /**
         * 使用宿主公共文本校验。
         * @param directive 原指令
         * @return 文本草稿
         */
        @Override public ReplyPlans.Draft parse(ReplyPlans.Directive directive){return new ReplyPlans.Draft(new ReplyPlans.Text(directive.body()),Mode.PLAIN,Integer.valueOf(directive.fields().getOrDefault("wait","0")));}
        /** @return 新指令语法 */
        @Override public String instructions(){return "CUSTOM|wait=毫秒；后面为正文。";}
    }
    /**
     * 测试用独立协议。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @org.zexnocs.teanekocore.plugin.PluginOwned("test.extra")
    @TeaNekoReplyProtocol("test:echo")
    static class CustomProtocol implements IReplyProtocolParser {
        /**
         * 证明宿主按新注册键分派。
         * @param answer 正式答案
         * @param nonce 当轮标识
         * @param directives 指令入口
         * @return 新协议结果
         */
        @Override public List<ReplyPlans.Draft> parse(ILLMAssistantMessage answer,String nonce,Function<ReplyPlans.Directive,ReplyPlans.Draft> directives){
            return List.of(new ReplyPlans.Draft(new ReplyPlans.Text("新增协议可用"),Mode.PLAIN,0));
        }
    }
}
