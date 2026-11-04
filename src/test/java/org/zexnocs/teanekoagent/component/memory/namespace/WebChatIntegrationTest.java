package org.zexnocs.teanekoagent.component.memory.namespace;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoapp.command.TeaNekoCommandConverter;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekoclient.web.*;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.command.ManagementCommandPolicy;
import org.zexnocs.teanekocore.command.api.CommandOrigin;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 复用原事务夹具验证网页入站、真实发送、重连、身份与媒体边界，不启动模型或真实平台。
 * <br>2026/11/03：通过真实 Spring 装配平台组件，验证注册目录及事件实际通知前撤权。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证网页目录刷新复用本地账号并继续执行管理授权。
 * <br>2026/11/04：验证事件通知结束后仍等待真实业务阶段，失败回执不冒充回复成功。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：用已有有界等待工具等待真实入站终态，取消循环休眠。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
@Timeout(20)
class WebChatIntegrationTest {
    private static final Runnable ALLOWED = () -> {};

    /** 2026/11/04：网页已保存不等于 Agent 已结束，业务失败必须反映在同一操作回执中。 */
    @Test
    void dispatchReceiptWaitsForBusinessCompletion() {
        try (var db = new NamespaceDatabaseFixture(); var root = new Root(db, Runnable::run)) {
            var work = new CompletableFuture<Void>();
            doAnswer(call -> {
                var event = assertInstanceOf(WebMessageReceiveEvent.class, call.getArgument(0));
                event.trackProcessing(work);
                return TaskFuture.fromStage(mock(ILogger.class), "测试通知完成", CompletableFuture.completedStage(new TaskResult<Void>(true, null)));
            }).when(root.events).pushEventWithFuture(any());
            var room = root.room("PRIVATE", "阶段测试");
            // 2026/11/04：补全上下文由真实房间成员投影，不能借用管理员身份或发送能力。
            var commands = mock(org.zexnocs.teanekocore.command.CommandCatalog.class);
            when(commands.list(any(), any())).thenAnswer(call -> {
                org.zexnocs.teanekocore.command.CommandData<?> source = call.getArgument(0);
                assertEquals(CommandPermission.MEMBER, source.getPermission());
                var data = assertInstanceOf(WebMessageData.class, source.getRawData());
                assertThrows(SecurityException.class, () -> data.getMessageSender().addTextMessage("不能执行预览").send());
                return List.of();
            });
            var catalog = new WebCommandCatalog(root.store, root.context.getBean(WebMessageFactory.class),
                    new TeaNekoCommandConverter(root.scopes), commands, Runnable::run);
            assertTrue(catalog.list(room.id(), WebChat.DEFAULT_PROFILE, ALLOWED).toCompletableFuture().join().isEmpty());
            assertThrows(CompletionException.class, () -> catalog.list(room.id(), "not-a-member", ALLOWED).toCompletableFuture().join());
            var send = new WebChat.Send(UUID.randomUUID(), WebChat.DEFAULT_PROFILE, "你好", false, "", "", Instant.now());
            root.service.submit(room.id(), send, ALLOWED).toCompletableFuture().join();
            assertEquals("PERSISTED", root.service.operation(room.id(), send.operationId(), ALLOWED).toCompletableFuture().join().status());
            work.completeExceptionally(new IllegalStateException("测试业务失败。"));
            assertEquals("INDETERMINATE", root.service.operation(room.id(), send.operationId(), ALLOWED).toCompletableFuture().join().status());
            verify(root.events, times(1)).pushEventWithFuture(any());
        }
    }

    /**
     * @param width 测试图片宽度
     * @return 本用例拥有的静态 PNG
     * @throws Exception 当 JDK 图片编码不可用时
     */
    private byte[] png(int width) throws Exception {
        var image = new BufferedImage(width, 1, BufferedImage.TYPE_INT_ARGB);
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } finally {
            image.flush();
        }
    }

    /**
     * 原生消息只通知一次，图像和自我提及正确映射，发送以持久消息号确认，重启后只补读。
     * 2026/11/04：一并验证网页管理适配器的真实目录刷新能力。
     * 2026/11/05：用已有有界等待工具等待真实入站终态，取消循环休眠。
     * @throws Exception 当异步通知或图片夹具失败时
     */
    @Test
    void usesTheOriginalMessageContractAndPersistsActualDeliveryWithoutReplay() throws Exception {
        try (var db = new NamespaceDatabaseFixture(); var root = new Root(db, Runnable::run)) {
            var adapter = root.context.getBean(WebManagementAdapter.class);
            assertEquals(WebChat.SELF, adapter.discover(ALLOWED).toCompletableFuture().join().getFirst().ref());
            assertThrows(SecurityException.class, () -> adapter.discover(() -> { throw new SecurityException("会话已撤销。"); }));
            var room = root.room("GROUP", "网页测试群");
            byte[] image = root.images.normalize(png(1), ALLOWED).toCompletableFuture().join();
            String media = root.store.upload(room.id(), UUID.randomUUID(), image, ALLOWED).toCompletableFuture().join();
            var request = new WebChat.Send(UUID.randomUUID(), WebChat.DEFAULT_PROFILE, "你好", true, "", media, Instant.now());
            assertEquals("PERSISTED", root.service.submit(room.id(), request, ALLOWED).toCompletableFuture().join().status());
            var data = root.received.get(5, TimeUnit.SECONDS);
            assertEquals(CommandPermission.MEMBER, data.getUserData().getRole());
            assertEquals(WebTeaNekoClient.ID, data.getClient().getClientId());
            var input = NativeMessageInput.from(data);
            assertTrue(NativeMessageInput.contextText(data).contains("\"mentionedSelf\":true"));
            assertTrue(input.message().getContents().stream().anyMatch(item -> item.getType().equals("image")));
            assertEquals("PERSISTED", root.service.submit(room.id(), request, ALLOWED).toCompletableFuture().join().status());
            verify(root.events, times(1)).pushEventWithFuture(any());
            var sent = data.getMessageSender().addReplyMessage().addTextMessage("真实持久回复").sendWithFuture()
                    .asStage().toCompletableFuture().join();
            assertFalse(sent.getMessageId().isBlank());
            var page = root.store.page(room.id(), 0, ALLOWED).toCompletableFuture().join();
            assertEquals(2, page.items().size());
            assertTrue(page.items().getLast().bot());
            assertEquals(data.getMessageId(), page.items().getLast().origin());
            assertEquals(List.of(page.items().getLast()), root.store.page(room.id(), 1, ALLOWED)
                    .toCompletableFuture().join().items());
            var quoted = root.client.getTeaNekoToolbox().getGetMsgSender().getMsg(sent.getMessageId()).asStage()
                    .toCompletableFuture().join().getResult();
            assertNotNull(quoted);
            assertEquals(WebChat.SELF, quoted.getUserData().getUserIdInPlatform());
            assertThrows(SecurityException.class, () -> quoted.getMessageSender().addTextMessage("历史消息不能重获能力").send());
            root.notified.complete(new TaskResult<>(true, null));
            // 2026/11/05：等待真实入站通知结束，不依赖循环休眠或无限期等待。
            org.awaitility.Awaitility.await("网页消息入站通知")
                    .atMost(java.time.Duration.ofSeconds(5))
                    .until(() -> root.service.operation(room.id(), request.operationId(), ALLOWED)
                                    .toCompletableFuture().get(1, TimeUnit.SECONDS),
                            receipt -> !receipt.status().equals("PERSISTED"));
            try (var restarted = new Root(db, Runnable::run)) {
                assertEquals("DISPATCHED", restarted.service.submit(room.id(), request, ALLOWED).toCompletableFuture().join().status());
                verifyNoInteractions(restarted.events);
                assertEquals(page, restarted.store.page(room.id(), 0, ALLOWED).toCompletableFuture().join());
            }
        }
    }

    /**
     * 成员和会话撤权阻止迟到命令与发送，跨聊天资源不能混用，孤立操作不重放。
     * @throws Exception 当真实异步通知或边界图片创建失败时
     */
    @Test
    void rejectsRevokedSourcesCrossRoomReferencesAndUnsafeImages() throws Exception {
        try (var db = new NamespaceDatabaseFixture(); var root = new Root(db, Runnable::run)) {
            var room = root.room("GROUP", "来源群");
            var other = root.room("GROUP", "其他群");
            var valid = new AtomicBoolean(true);
            Runnable guard = () -> {
                if (!valid.get()) {
                    throw new SecurityException("测试撤销网页会话。");
                }
            };
            var request = new WebChat.Send(UUID.randomUUID(), WebChat.DEFAULT_PROFILE, "/agent chat 你好", true, "", "", Instant.now());
            root.service.submit(room.id(), request, guard).toCompletableFuture().join();
            var data = root.received.get(5, TimeUnit.SECONDS);
            var command = new TeaNekoCommandConverter(root.scopes).__parse(data);
            assertEquals(CommandOrigin.NATIVE, command.getOrigin());
            assertEquals(CommandPermission.MEMBER, command.getPermission());
            valid.set(false);
            // 2026/11/03：入队后退出时，原事件总线不能再开始通知监听器。
            assertThrows(SecurityException.class, new WebMessageReceiveEvent(data)::_beforeNotify);
            assertThrows(SecurityException.class, () -> ManagementCommandPolicy.check(command, Object.class.getMethod("toString")));
            assertThrows(SecurityException.class, () -> data.getMessageSender().addTextMessage("不能继续发送").send());
            valid.set(true);
            root.service.member(room.id(), new WebChat.ChangeMember(UUID.randomUUID(), room.revision(),
                    new WebChat.Member(WebChat.DEFAULT_PROFILE, "新名片", "ADMIN"), "UPSERT"), ALLOWED).toCompletableFuture().join();
            assertThrows(SecurityException.class, data::checkCommandSource);
            assertThrows(IllegalArgumentException.class, () -> new WebChat.ChangeMember(UUID.randomUUID(), 1,
                    new WebChat.Member(WebChat.DEFAULT_PROFILE, "不能赋予调试权限", "DEBUG"), "UPSERT"));
            assertThrows(CompletionException.class, () -> root.store.claim(other.id(), request, UUID.randomUUID(), ALLOWED)
                    .toCompletableFuture().join());
            String media = root.store.upload(room.id(), UUID.randomUUID(), png(1), ALLOWED).toCompletableFuture().join();
            assertThrows(CompletionException.class, () -> root.store.image(other.id(), media, ALLOWED).toCompletableFuture().join());
            var badReply = new WebChat.Send(UUID.randomUUID(), WebChat.DEFAULT_PROFILE, "引用不应跨聊天", false,
                    data.getMessageId(), "", Instant.now());
            assertThrows(CompletionException.class, () -> root.store.claim(other.id(), badReply, UUID.randomUUID(), ALLOWED)
                    .toCompletableFuture().join());
            var orphan = new WebChat.Send(UUID.randomUUID(), WebChat.DEFAULT_PROFILE, "孤立受理", false, "", "", Instant.now());
            root.store.claim(other.id(), orphan, UUID.randomUUID(), ALLOWED).toCompletableFuture().join();
            assertEquals("INDETERMINATE", root.service.operation(other.id(), orphan.operationId(), ALLOWED).toCompletableFuture().join().status());
            assertFalse(root.store.claim(other.id(), orphan, UUID.randomUUID(), ALLOWED).toCompletableFuture().join().acquired());
            byte[] bomb = png(5000);
            assertThrows(CompletionException.class, () -> root.images.normalize(bomb, ALLOWED).toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> root.images.normalize("<svg/>".getBytes(StandardCharsets.UTF_8), ALLOWED)
                    .toCompletableFuture().join());
        }
    }

    /**
     * 借用原事务、真实平台映射及发送器，事件只由受控完成阶段替代，不调用真实模型。
     * <br>2026/11/03：原 Spring 注入与 scanner 参与装配，不用手动客户端替代生产组件。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.8.0-l
     */
    private static final class Root implements AutoCloseable {
        final WebChatStore store;
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final WebImages images;
        final CompletableFuture<WebMessageData> received = new CompletableFuture<>();
        final CompletableFuture<ITaskResult<Void>> notified = new CompletableFuture<>();
        final IEventService events = mock(IEventService.class);
        final TeaNekoScopeService scopes;
        final WebTeaNekoClient client;
        final WebChatService service;

        /**
         * 2026/11/03：只替换外部服务，平台组件按原构造注入和延迟引用完成装配。
         * @param db 原事务夹具
         * @param executor 受控原执行域
         */
        Root(NamespaceDatabaseFixture db, Executor executor) {
            store = new WebChatStore(db.data.inNamespace("web-chat-test")::get, db.mapper, () -> new WebChatSettings().limits());
            var logger = mock(ILogger.class);
            var users = mock(ITeaUserService.class);
            when(users.getOrCreate(any(WebTeaNekoClient.class), anyString())).thenAnswer(call -> TaskFuture.fromStage(logger, "测试网页身份",
                    CompletableFuture.completedStage(UUID.nameUUIDFromBytes(("测试网页：" + call.getArgument(1)).getBytes(StandardCharsets.UTF_8)))));
            when(events.pushEventWithFuture(any())).thenAnswer(call -> {
                var event = assertInstanceOf(WebMessageReceiveEvent.class, call.getArgument(0));
                received.complete(event.getData());
                return TaskFuture.fromStage(logger, "测试原消息通知", notified);
            });
            context.registerBean(WebChatStore.class, () -> store);
            context.registerBean(ILogger.class, () -> logger);
            context.registerBean(ITeaUserService.class, () -> users);
            context.registerBean(IEventService.class, () -> events);
            context.registerBean(tools.jackson.databind.ObjectMapper.class, () -> db.mapper);
            context.registerBean("virtualExecutor", Executor.class, () -> executor);
            context.register(WebImages.class, WebTransport.class, WebMessageFactory.class, WebMessageSenderTools.class,
                    WebToolbox.class, WebTeaNekoClient.class, WebChatService.class, WebManagementAdapter.class,
                    org.zexnocs.teanekocore.utils.scanner.BeanScanner.class,
                    org.zexnocs.teanekoapp.client.TeaNekoClientScanner.class,
                    org.zexnocs.teanekoapp.web.client.ClientManagementDirectory.class, TeaNekoScopeService.class);
            context.refresh();
            images = context.getBean(WebImages.class);
            scopes = context.getBean(TeaNekoScopeService.class);
            client = context.getBean(WebTeaNekoClient.class);
            service = context.getBean(WebChatService.class);
            var scanner = context.getBean(org.zexnocs.teanekoapp.client.TeaNekoClientScanner.class);
            scanner.init();
            assertSame(client, Objects.requireNonNull(scanner.getPair(WebTeaNekoClient.ID)).second());
            service.home(ALLOWED).toCompletableFuture().join();
        }

        /**
         * @param kind 聊天类型
         * @param name 测试显示名
         * @return 实际已创建聊天
         */
        WebChat.RoomView room(String kind, String name) {
            var room = service.create(new WebChat.CreateRoom(UUID.randomUUID(), kind, name, List.of(WebChat.DEFAULT_PROFILE)), ALLOWED)
                    .toCompletableFuture().join();
            // 2026/11/03：生产管理注册表能解析实际客户端及同一 scope，而非只验证构造不报错。
            var resolved = context.getBean(org.zexnocs.teanekoapp.web.client.ClientManagementDirectory.class)
                    .invoke("web", adapter -> adapter.resolve(WebChat.SELF, room.id())).toCompletableFuture().join();
            assertEquals(store.room(room.id(), ALLOWED).toCompletableFuture().join().scope(), resolved.scope());
            return room;
        }

        /** 释放本测试拥有的网页能力，不关闭借用执行器。 */
        @Override
        public void close() {
            // 2026/11/03：生产销毁回调撤销能力，借用执行器不随平台关闭。
            context.close();
            notified.complete(new TaskResult<>(true, null));
        }
    }
}
