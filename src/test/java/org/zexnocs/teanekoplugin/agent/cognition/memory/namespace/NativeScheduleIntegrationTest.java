package org.zexnocs.teanekoplugin.agent.cognition.memory.namespace;

import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService;
import org.zexnocs.teanekoplugin.agent.automation.schedule.EasyDataScheduleStore;
import org.zexnocs.teanekoplugin.agent.automation.schedule.adapter.AgentScheduleLifecycle;
import org.zexnocs.teanekoplugin.agent.automation.schedule.adapter.NativeScheduleAdapter;
import org.zexnocs.teanekoplugin.agent.automation.integration.command.AgentTaskCommands;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoUserData;
import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Status.CANCELLED;
import static org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Status.INDETERMINATE;
import static org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Status.PAUSED;

/**
 * 实际原生任务入口到专用数据库及发送适配的必要闭环，不连接真实平台。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：原业务夹具显式使用历史装配，继续回归旧行为。
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：补充组件审查修复的离线回归及初始化夹具。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Timeout(20)
class NativeScheduleIntegrationTest {
    /**
     * 复用事务夹具，平台仅模拟当前身份与实际发送回执。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：夹具明确保留历史装配及原业务行为，适配可选组件路径。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private static final class Fixture implements AutoCloseable {
        final NamespaceDatabaseFixture db = new NamespaceDatabaseFixture();
        final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor();
        final ILogger logger = mock(ILogger.class);
        final ITeaNekoClient client = mock(ITeaNekoClient.class);
        final ITeaUserService users = mock(ITeaUserService.class);
        final ITeaNekoToolbox toolbox = mock(ITeaNekoToolbox.class);
        final IGetGroupMemberInfoSender members = mock(IGetGroupMemberInfoSender.class);
        final IMessageSenderTools messages = mock(IMessageSenderTools.class);
        final IEasyMessageSenderBuilder sender = mock(IEasyMessageSenderBuilder.class, RETURNS_SELF);
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final Map<String, String> roles = new ConcurrentHashMap<>();
        final Map<String, UUID> ids = Map.of("alice", UUID.randomUUID(), "bob", UUID.randomUUID());
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2029-01-01T00:00:00Z"));
        final EasyDataScheduleStore store = new EasyDataScheduleStore(db.data, db.mapper);
        final CompletableFuture<org.zexnocs.teanekoapp.response.api.IMessageSendResponseData> sent = new CompletableFuture<>();
        final CompletableFuture<Void> sending = new CompletableFuture<>();
        final AgentScheduleService schedules;
        final AgentScheduleLifecycle lifecycle;
        final AgentTaskCommands commands;

        /** 装配实际原生 scope 解析、身份策略和发送器，恢复完成后才开放命令。 */
        @SuppressWarnings("unchecked")
        Fixture() {
            var clients = mock(TeaNekoClientScanner.class);
            when(client.getClientId()).thenReturn("native");
            // 2026/11/06：目录查询夹具提供不可变快照，不绕过正式扫描器修改注册表。
            doReturn(java.util.Map.of("native", IndependentPair.of(mock(TeaNekoClient.class), client))).when(clients).getHandlerMap();
            doAnswer(call -> clients.getHandlerMap().get(call.getArgument(0))).when(clients).getPair(anyString());
            when(client.getTeaNekoToolbox()).thenReturn(toolbox);
            when(toolbox.getGroupMemberInfoSender()).thenReturn(members);
            when(toolbox.getMessageSenderTools()).thenReturn(messages);
            when(users.get(eq(client), anyString())).thenAnswer(call -> ids.get(call.<String>getArgument(1)));
            roles.put("alice", "member");
            roles.put("bob", "member");
            doAnswer(call -> {
                var member = mock(IGroupMemberResponseData.class);
                String user = call.getArgument(1);
                when(member.getUserId()).thenReturn(user);
                when(member.getRole()).thenAnswer(ignored -> roles.get(user));
                return new TaskFuture<>(logger, "当前成员", CompletableFuture.completedFuture(member));
            }).when(members).get(anyString(), anyString());
            when(messages.getGroupBuilder("42")).thenReturn(sender);
            when(messages.getPrivateBuilder("alice")).thenReturn(sender);
            when(sender.sendWithFuture()).thenAnswer(ignored -> {
                sending.complete(null);
                return new TaskFuture<>(logger, "提醒发送", sent);
            });
            var adapter = new NativeScheduleAdapter(scope -> new AgentScopePolicy(true, enabled.get(), AgentScopePolicy.Mode.PASSIVE_BASIC),
                clients, new TeaNekoScopeService(clients), users, Runnable::run);
            var clock = mock(Clock.class);
            when(clock.instant()).thenAnswer(ignored -> now.get());
            schedules = new AgentScheduleService(store, clock, adapter, Map.of("remind", adapter));
            var timers = mock(ITimerService.class);
            lifecycle = new AgentScheduleLifecycle(schedules, timers);
            lifecycle.start().toCompletableFuture().join();
            lifecycle.start().toCompletableFuture().join();
            // 2026/11/07：重复启动不重复注册带插件归属的真实定时器。
            verify(timers, times(1)).register(any());
            var configs = mock(IConfigDataService.class);
            var config = new AgentScopeConfigData(null);
            AgentComponentFixtures.set(config, "schedule", "taskTimeZone", "Asia/Shanghai");
            when(configs.getConfigData(any(), eq(AgentScopeConfigData.class), anyString())).thenAnswer(ignored -> {
                config.setEnabled(enabled.get());
                return Optional.of(config);
            });
            commands = new AgentTaskCommands(schedules, lifecycle, Runnable::run, deadlines, AgentComponentFixtures.access(configs, new AgentScopeConfigManager()));
        }

        /**
         * 仅构造原生消息，模型无法指定目标或其他 UUID。
         * @param user 实际平台用户
         * @param message 稳定消息 ID
         * @param scope 实际来源 scope
         * @return 原生消息
         */
        ITeaNekoMessageData source(String user, String message, String scope) {
            var data = mock(ITeaNekoMessageData.class); var person = mock(ITeaNekoUserData.class);
            when(data.getUserData()).thenReturn(person); when(person.getUuid()).thenReturn(ids.get(user));
            when(person.getUserIdInPlatform()).thenReturn(user); when(data.getMessageId()).thenReturn(message);
            when(data.getScopeId()).thenReturn(scope); when(data.getClient()).thenReturn(client); when(data.getTime()).thenReturn(now.get()); return data;
        }

        /** 释放实际测试拥有者和时限线程，解除等待中的发送回执。 */
        @Override public void close() { sent.complete(null); schedules.close(); deadlines.shutdownNow(); db.close(); }
    }

    /** 原生命令先提交后反馈，重放不新增；调度等待真实发送，失败不重试或补发。 */
    @Test void nativeCommandPersistsBeforeFeedbackAndWaitsForDelivery() {
        try (var f = new Fixture()) {
            var data = f.source("alice", "creation", "native-group@42");
            var replies = mock(IEasyMessageSenderBuilder.class); var reply = new CompletableFuture<Void>();
            when(data.getMessageSender()).thenReturn(replies); when(replies.sendReplyMessageAsync(anyString())).thenReturn(reply);
            var handler = new org.zexnocs.teanekoplugin.agent.automation.integration.command.AutomationCommands(null, f.commands);
            var nativeCommand = CommandData.<ITeaNekoMessageData>builder().rawData(data).build();
            var response = handler.remind(nativeCommand, "2029-01-01T08:00:10+08:00", "Asia/Shanghai", 0, List.of("开会")).toCompletableFuture();
            verify(replies, timeout(5000)).sendReplyMessageAsync(anyString());
            assertEquals(1, f.store.page("").toCompletableFuture().join().size());
            assertFalse(response.isDone()); reply.complete(null); response.join();
            handler.remind(nativeCommand, "2029-01-01T08:00:10+08:00", "Asia/Shanghai", 0, List.of("开会")).toCompletableFuture().join();
            var task = f.store.page("").toCompletableFuture().join().getFirst(); assertEquals(1, f.store.page("").toCompletableFuture().join().size());
            f.now.set(task.nextFire()); var tick = f.schedules.tick().toCompletableFuture(); f.sending.join(); assertFalse(tick.isDone());
            f.sent.completeExceptionally(new IllegalStateException("平台确认未知。")); tick.join();
            assertEquals(INDETERMINATE, f.store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            f.schedules.tick().toCompletableFuture().join(); verify(f.sender, times(1)).sendWithFuture();
            verify(f.sender).setRetryCount(0); verify(f.sender).setRecordFailed(false); verify(f.sender).addTextMessage("提醒：开会");
        }
    }

    /**
     * 2026/11/05：非空但缺消息 ID 的回执只记为结果不明，禁止再次发送。
     * @throws Exception 当有界平台阶段等待失败时
     */
    @Test
    void missingMessageIdDoesNotConfirmReminder() throws Exception {
        try (var f = new Fixture()) {
            var data = f.source("alice", "创建提醒", "native-group@42");
            var actor = NativeMessageSources.actor(data);
            var first = f.now.get().plusSeconds(5);
            var spec = new org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask.Spec("检查回执", actor,
                    "remind", Map.of("text", "内容"), first, "UTC", 0);
            var task = f.schedules.create(UUID.randomUUID(), spec).toCompletableFuture().get(5, TimeUnit.SECONDS);
            f.now.set(first);
            var tick = f.schedules.tick().toCompletableFuture();
            f.sending.get(5, TimeUnit.SECONDS);
            f.sent.complete(mock(org.zexnocs.teanekoapp.response.api.IMessageSendResponseData.class));
            tick.get(5, TimeUnit.SECONDS);
            assertEquals(INDETERMINATE, f.store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            f.schedules.tick().toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(f.sender, times(1)).sendWithFuture();
        }
    }

    /** 当前成员、归属、禁用和私聊边界独立检查；夏令时空洞与错误偏移不能保存。 */
    @Test void currentIdentityOwnershipAndTimeZoneRemainAuthoritative() {
        try (var f = new Fixture()) {
            var alice = f.source("alice", "one", "native-group@42"); var bob = f.source("bob", "two", "native-group@42");
            f.commands.create(alice, "2029-01-01T08:00:10+08:00", "Asia/Shanghai", 0, List.of("提醒一"), null).toCompletableFuture().join();
            f.commands.create(bob, "2029-01-01T08:00:10+08:00", "Asia/Shanghai", 0, List.of("提醒二"), null).toCompletableFuture().join();
            var own = f.schedules.list(NativeMessageSources.actor(alice), "").toCompletableFuture().join();
            assertEquals(1, own.tasks().size());
            var bobTask = f.store.page("").toCompletableFuture().join().stream().filter(t -> t.spec().creator().platformUserId().equals("bob")).findFirst().orElseThrow();
            assertThrows(CompletionException.class, () -> f.commands.inspect(alice, bobTask.id().toString(), true, null).toCompletableFuture().join());
            f.roles.put("alice", "admin"); f.enabled.set(false);
            assertEquals(2, f.schedules.list(NativeMessageSources.actor(alice), "").toCompletableFuture().join().tasks().size());
            f.commands.inspect(alice, bobTask.id().toString(), true, null).toCompletableFuture().join();
            f.now.set(bobTask.nextFire()); f.schedules.tick().toCompletableFuture().join();
            assertEquals(PAUSED, f.store.find(own.tasks().getFirst().id()).toCompletableFuture().join().orElseThrow().status());
            verify(f.sender, never()).sendWithFuture();
            f.enabled.set(true);
            var privateData = f.source("alice", "private", "private@" + f.ids.get("alice"));
            f.commands.create(privateData, "2029-01-01T08:00:20+08:00", "Asia/Shanghai", 0, List.of("私聊"), null).toCompletableFuture().join();
            f.now.set(Instant.parse("2029-01-01T00:00:20Z")); f.sent.complete(null); f.schedules.tick().toCompletableFuture().join();
            verify(f.messages).getPrivateBuilder("alice");
            var bad = f.commands.create(alice, "2029-03-11T02:30:00-05:00", "America/New_York", 0, List.of("无效"), null).toCompletableFuture().join();
            assertTrue(bad.contains("不存在")); assertEquals(3, f.store.page("").toCompletableFuture().join().size());
        }
    }

    /** 当前原文产生稳定时间候选并完成创建、查询、取消；模糊时间和引用不生成任务。 */
    @Test void naturalTaskRequestsUseOriginalTimeAndNeverGuessAmbiguity() {
        try (var f = new Fixture()) {
            var source = f.source("alice", "natural", "native-group@42");
            assertTrue(f.commands.natural(source, "你好").isEmpty());
            f.commands.natural(source, "10分钟后提醒我喝水").orElseThrow().toCompletableFuture().join();
            var task = f.store.page("").toCompletableFuture().join().getFirst();
            assertEquals(source.getTime().plusSeconds(600), task.nextFire());
            f.now.set(f.now.get().plusSeconds(100));
            f.commands.natural(source, "10分钟后提醒我喝水").orElseThrow().toCompletableFuture().join();
            assertEquals(1, f.store.page("").toCompletableFuture().join().size());
            assertTrue(f.commands.natural(source, "查看我的任务").orElseThrow().toCompletableFuture().join().contains(task.id().toString()));
            var vague = f.commands.natural(source, "明天早上提醒我开会").orElseThrow().toCompletableFuture().join();
            assertTrue(vague.contains("明确"));
            assertTrue(f.commands.natural(source, "取消任务 上一个").orElseThrow().toCompletableFuture().join().contains("UUID"));
            f.commands.natural(source, "有人说：10分钟后提醒我喝水").orElseThrow().toCompletableFuture().join();
            assertEquals(1, f.store.page("").toCompletableFuture().join().size());
            f.commands.natural(source, "取消任务 " + task.id()).orElseThrow().toCompletableFuture().join();
            assertEquals(CANCELLED, f.store.find(task.id()).toCompletableFuture().join().orElseThrow().status());
            var calendar = f.source("alice", "calendar", "native-group@42");
            f.commands.natural(calendar, "明天09:00提醒我开会").orElseThrow().toCompletableFuture().join();
            assertTrue(f.store.page("").toCompletableFuture().join().stream().anyMatch(item ->
                    item.spec().zoneId().equals("Asia/Shanghai") && item.nextFire().equals(Instant.parse("2029-01-02T01:00:00Z"))));
        }
    }
}
