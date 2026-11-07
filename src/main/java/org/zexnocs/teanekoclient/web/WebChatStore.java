package org.zexnocs.teanekoclient.web;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataService;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 网页平台的档案、聊天、消息、受理账本与图片；只经原 EasyData DTO 事务操作。
 * 元数据、每个聊天窗口和每张图片分行保存；读事务共享目录锁，修改事务持有目录写锁，避免跨域竞态。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：消息回执区分持久化、事件通知与已登记业务完成事实。
 * <br>2026/11/05：原子删除网页房间、消息及附件，不影响共享记忆或配置。
 * <br>2026/11/05：允许私聊原成员修改网页角色，保持唯一成员和原聊天归属。
 * <br>2026/11/05：持久回执保留实际失败阶段与脱敏原因，支持后续查询。
 * <br>2026/11/06：网页管理消息使用原房间事务，发送前核对授权及版本。
 * <br>2026/11/07：纯读取使用共享锁，初始化及修改继续使用写锁仲裁。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.7-l
 */
@Service
public final class WebChatStore {
    private final Function<String, IEasyDataDto> data;
    private final ObjectMapper mapper;
    private final Supplier<WebChatSettings.Limits> limits;

    /**
     * @param data 原 EasyData 服务，固定实体与业务域
     * @param mapper 原存储转换器
     * @param files 本地容量配置
     */
    @Autowired
    public WebChatStore(IEasyDataService data, ObjectMapper mapper, IFileConfigService files) {
        this(name -> data.getEasyDataFor(GeneralEasyDataObject.class, "web.chat.v1", name), mapper,
                () -> files.requireCurrent(WebChatSettings.class).limits());
    }

    /**
     * @param data 宿主固定域入口，测试借原事务夹具
     * @param mapper 原转换器
     * @param limits 当前校验容量
     */
    public WebChatStore(Function<String, IEasyDataDto> data, ObjectMapper mapper,
                        Supplier<WebChatSettings.Limits> limits) {
        this.data = data;
        this.mapper = mapper;
        this.limits = limits;
    }

    /**
     * @param uuid 已由原 TeaUser 确认的网页默认档案
     * @param guard 当前真实会话
     * @return 当前档案与聊天，不初始化任何 Agent 配置或记忆
     */
    public CompletionStage<WebChat.Home> home(UUID uuid, Runnable guard) {
        // 2026/11/06：常规首页读取不取得写锁，仅缺少默认档案时进入初始化事务并复核。
        return read(guard, state -> state.catalog.profiles().containsKey(WebChat.DEFAULT_PROFILE)
            ? Optional.of(homeView(state, uuid)) : Optional.<WebChat.Home>empty())
            .thenCompose(existing -> existing.map(
                java.util.concurrent.CompletableFuture::completedStage).orElseGet(() -> transaction(guard, state -> {
                if (!state.catalog.profiles().containsKey(WebChat.DEFAULT_PROFILE)) {
                    if (state.catalog.profiles().size() >= limits.get().profiles()) {
                        throw new IllegalStateException("网页档案容量不足，无法登记默认档案。");
                    }
                    state.catalog.profiles().put(WebChat.DEFAULT_PROFILE,
                        new WebChat.Profile(WebChat.DEFAULT_PROFILE, uuid, "网页用户"));
                    state.save();
                }
                return homeView(state, uuid);
            })));
    }

    /**
     * @param state 同一事务的目录
     * @param uuid 原身份映射
     * @return 不修改目录的首页视图
     */
    private WebChat.Home homeView(State state, UUID uuid) {
        if (!state.profile(WebChat.DEFAULT_PROFILE).uuid().equals(uuid)) {
            throw new IllegalStateException("网页默认档案的 TeaUser 映射已变化，不能自动改写旧身份。");
        }
        return new WebChat.Home(List.copyOf(state.catalog.profiles().values()),
            state.catalog.rooms().values().stream().map(WebChatStore::view).toList(), WebChat.SELF, Instant.now());
    }

    /**
     * @param request 原档案创建请求
     * @param uuid 原 TeaUser 实际映射
     * @param guard 当前会话
     * @return 服务端固定的平台档案
     */
    public CompletionStage<WebChat.Profile> profile(WebChat.CreateProfile request, UUID uuid, Runnable guard) {
        String name = name(request.name());
        String id = generated("profile", request.operationId());
        String digest = StableDigest.sha256("profile", name);
        return transaction(guard, state -> {
            var replay = state.replay(request.operationId(), digest);
            if (replay != null) {
                return Objects.requireNonNull(state.catalog.profiles().get(replay));
            }
            if (state.catalog.profiles().size() >= limits.get().profiles()) {
                throw new IllegalStateException("网页档案数量已达到本地上限。");
            }
            if (state.catalog.profiles().containsKey(id)) {
                throw new IllegalStateException("原档案仍存在，不能用过期创建编号重新覆盖。");
            }
            var profile = new WebChat.Profile(id, uuid, name);
            state.catalog.profiles().put(id, profile);
            state.remember(request.operationId(), digest, id);
            return profile;
        });
    }

    /**
     * @param request 原聊天请求
     * @param scope 由宿主按原 scope 服务生成
     * @param guard 原会话
     * @return 已提交聊天，私聊每个档案只登记一个
     */
    public CompletionStage<WebChat.Room> create(WebChat.CreateRoom request, String scope, Runnable guard) {
        String name = name(request.name());
        if (!Set.of("PRIVATE", "GROUP").contains(request.kind()) || request.profiles() == null
                || request.profiles().isEmpty() || request.profiles().size() > 32
                || new HashSet<>(request.profiles()).size() != request.profiles().size()
                || request.kind().equals("PRIVATE") && request.profiles().size() != 1) {
            throw new IllegalArgumentException("网页聊天类型或成员列表无效。");
        }
        String digest = StableDigest.sha256("room", name, request.kind(), mapper.writeValueAsString(request.profiles()));
        return transaction(guard, state -> {
            var replay = state.replay(request.operationId(), digest);
            if (replay != null) {
                return state.room(replay);
            }
            if (state.catalog.rooms().size() >= limits.get().rooms()) {
                throw new IllegalStateException("网页聊天数量已达到本地上限。");
            }
            var members = request.profiles().stream().map(id -> {
                var profile = state.profile(id);
                return new WebChat.Member(profile.id(), profile.name(), "MEMBER");
            }).toList();
            if (request.kind().equals("PRIVATE") && state.catalog.rooms().values().stream()
                    .anyMatch(room -> room.kind().equals("PRIVATE") && room.members().getFirst().profile().equals(members.getFirst().profile()))) {
                throw new IllegalStateException("该档案已有私聊，请选择原私聊。");
            }
            var room = new WebChat.Room(generated("room", request.operationId()), request.kind(), name, scope,
                    0, members, Instant.now());
            if (state.catalog.rooms().containsKey(room.id())) {
                throw new IllegalStateException("原聊天仍存在，不能用过期创建编号重新覆盖。");
            }
            state.catalog.rooms().put(room.id(), room);
            state.remember(request.operationId(), digest, room.id());
            return room;
        });
    }

    /**
     * 2026/11/05：私聊只允许更新原成员，不能通过成员管理增加、替换或移除私聊身份。
     * @param id 当前聊天
     * @param request 专用成员变更，不接受 DEBUG
     * @param guard 当前管理员会话
     * @return 原子版本更新后的聊天
     */
    public CompletionStage<WebChat.Room> member(String id, WebChat.ChangeMember request, Runnable guard) {
        Objects.requireNonNull(request.member());
        String digest = StableDigest.sha256("member", id, mapper.writeValueAsString(request));
        return transaction(guard, state -> {
            var replay = state.replay(request.operationId(), digest);
            if (replay != null) {
                return state.room(replay);
            }
            var room = state.room(id);
            if (room.kind().equals("PRIVATE") && (!request.action().equals("UPSERT")
                    || !room.members().getFirst().profile().equals(request.member().profile()))) {
                throw new IllegalArgumentException("私聊只允许更新原有成员，不能增加、替换或移除成员。");
            }
            if (room.revision() != request.revision()) {
                throw new ConcurrentModificationException("网页成员版本已经变化。");
            }
            var member = request.member();
            state.profile(member.profile());
            var values = new ArrayList<>(room.members());
            values.removeIf(value -> value.profile().equals(member.profile()));
            if (request.action().equals("UPSERT")) {
                if (!Set.of("MEMBER", "ADMIN", "OWNER").contains(member.role())) {
                    throw new IllegalArgumentException("模拟成员角色不允许此值。");
                }
                values.add(new WebChat.Member(member.profile(), name(member.card()), member.role()));
            }
            if (values.isEmpty() || values.size() > 32) {
                throw new IllegalArgumentException("网页群必须保留一至三十二位模拟成员。");
            }
            var updated = new WebChat.Room(room.id(), room.kind(), room.name(), room.scope(), room.revision() + 1,
                    values, room.createdAt());
            state.catalog.rooms().put(id, updated);
            state.remember(request.operationId(), digest, id);
            return updated;
        });
    }

    /**
     * @param guard 当前宿主授权
     * @return 内部已确认聊天，不直接作为 HTTP 响应
     */
    public CompletionStage<List<WebChat.Room>> rooms(Runnable guard) {
        return read(guard, state -> List.copyOf(state.catalog.rooms().values()));
    }

    /**
     * @param id 当前聊天
     * @param guard 原授权
     * @return 已知聊天；未知目标不按请求临时构造
     */
    public CompletionStage<WebChat.Room> room(String id, Runnable guard) {
        return read(guard, state -> state.room(id));
    }

    /**
     * @param id 档案引用
     * @param guard 原授权
     * @return 已存在档案
     */
    public CompletionStage<WebChat.Profile> profile(String id, Runnable guard) {
        return read(guard, state -> state.profile(id));
    }

    /**
     * @param room 当前聊天
     * @param after 原游标
     * @param guard 当前会话
     * @return 至多五十条真实消息，窗口前移时明确通知
     */
    public CompletionStage<WebChat.Page> page(String room, long after, Runnable guard) {
        if (after < 0) {
            throw new IllegalArgumentException("网页消息游标不能为负数。");
        }
        return read(guard, state -> {
            state.room(room);
            var journal = journal(room, EasyDataLock.READ);
            long first = journal.messages().isEmpty() ? journal.sequence() + 1 : journal.messages().getFirst().sequence();
            boolean reset = after > journal.sequence() || after > 0 && after < first - 1;
            long cursor = reset ? first - 1 : after;
            var found = journal.messages().stream().filter(message -> message.sequence() > cursor).limit(51).toList();
            var page = found.stream().limit(50).toList();
            return new WebChat.Page(page, page.isEmpty() ? journal.sequence() : page.getLast().sequence(), found.size() > 50, reset);
        });
    }

    /**
     * @param room 当前聊天
     * @param request 原消息请求
     * @param instance 原物理工作进程
     * @param guard 事务内实时授权
     * @return 只有新持久认领才能触发一次原入站链
     */
    public CompletionStage<Claim> claim(String room, WebChat.Send request, UUID instance, Runnable guard) {
        return transaction(guard, state -> {
            var target = state.room(room);
            var journal = journal(room);
            String digest = StableDigest.sha256(mapper.writeValueAsString(request));
            var binding = state.catalog.submissions().get(request.operationId());
            if (binding != null && (!binding.room().equals(room) || !binding.digest().equals(digest))) {
                throw new SecurityException("网页消息编号不能跨聊天或参数重用。");
            }
            var previous = journal.operations().get(request.operationId());
            if (previous != null) {
                if (!previous.digest().equals(digest)) {
                    throw new IllegalArgumentException("原网页消息编号已经绑定其他参数。");
                }
                return new Claim(false, target, null, previous.receipt());
            }
            var now = Instant.now();
            if (request.observedAt().isBefore(now.minusSeconds(300)) || request.observedAt().isAfter(now.plusSeconds(30))) {
                throw new IllegalStateException("网页聊天观察时间已过期，请刷新后准备新操作。");
            }
            journal.operations().values().removeIf(value -> value.at().plusSeconds(86400).isBefore(now));
            if (journal.operations().size() >= 512) {
                throw new IllegalStateException("本聊天的受理账本窗口已满。");
            }
            state.catalog.submissions().values().removeIf(value -> value.at().plusSeconds(86400).isBefore(now));
            if (state.catalog.submissions().size() >= 4096) {
                throw new IllegalStateException("网页入站全局回执窗口已满。");
            }
            var member = member(target, request.profile());
            var parts = new ArrayList<WebChat.Part>();
            if (!request.reply().isEmpty()) {
                requireMessage(journal, request.reply());
                parts.add(new WebChat.Part("REPLY", request.reply()));
            }
            if (request.mention()) {
                parts.add(new WebChat.Part("AT", WebChat.SELF));
            }
            if (!request.text().isEmpty()) {
                parts.add(new WebChat.Part("TEXT", request.text()));
            }
            if (!request.media().isEmpty()) {
                state.media(room, request.media());
                parts.add(new WebChat.Part("IMAGE", request.media()));
            }
            var message = append(room, journal, state.profile(member.profile()), member.card(), member.role(), false, "", parts);
            var receipt = new WebChat.Receipt(request.operationId(), message.id(), "PERSISTED", "消息已保存，尚未证明入站通知或 Agent 回复完成。");
            journal.operations().put(request.operationId(), new Operation(digest, instance, receipt, now));
            saveJournal(room, journal);
            state.catalog.submissions().put(request.operationId(), new Submission(room, digest, now));
            state.save();
            return new Claim(true, target, message, receipt);
        });
    }

    /**
     * @param room 原聊天
     * @param operation 原编号
     * @param status 实际入站及已登记处理阶段状态，不代表所有外部动作都成功
     * @param guard 原授权或宿主仅保存事实的检查
     * @return 已持久阶段
     */
    public CompletionStage<WebChat.Receipt> finish(String room, UUID operation, String status, Runnable guard) {
        // 2026/11/05：旧调用沿用固定说明，实际入站失败可通过重载保存脱敏诊断。
        String note = status.equals("DISPATCHED")
                ? "本条消息的入站通知及已登记处理阶段已结束，请查看对话中的实际回复。"
                : "消息已保存，但入站或业务处理未完成；请检查 Agent 活动与模型配置。已发生动作不会撤销，不自动重发。";
        return finish(room, operation, status, note, guard);
    }

    /**
     * 2026/11/05：保留实际失败的安全说明，原始异常和聊天正文不进入回执。
     * @param room 原聊天
     * @param operation 原编号
     * @param status 实际处理状态
     * @param note 宿主生成的脱敏说明，不接受第三方异常原文
     * @param guard 原授权或仅保存事实的检查
     * @return 原操作的最终持久回执
     */
    public CompletionStage<WebChat.Receipt> finish(String room, UUID operation, String status, String note, Runnable guard) {
        return transaction(guard, state -> {
            state.room(room);
            var journal = journal(room);
            var old = Objects.requireNonNull(journal.operations().get(operation));
            if (!old.receipt().status().equals("PERSISTED")) {
                return old.receipt();
            }
            var receipt = new WebChat.Receipt(operation, old.receipt().messageId(), status, note);
            journal.operations().put(operation, new Operation(old.digest(), old.instance(), receipt, old.at()));
            saveJournal(room, journal);
            return receipt;
        });
    }

    /**
     * @param room 原聊天
     * @param operation 原编号
     * @param instance 当前进程
     * @param active 当前工作拥有者，在真实事务内求值
     * @param guard 当前查询会话
     * @return 原受理事实；孤立工作明确不重放
     */
    public CompletionStage<WebChat.Receipt> operation(String room, UUID operation, UUID instance,
                                                      BooleanSupplier active, Runnable guard) {
        return transaction(guard, state -> {
            state.room(room);
            var journal = journal(room);
            var value = journal.operations().get(operation);
            if (value == null || value.at().plusSeconds(86400).isBefore(Instant.now())) {
                throw new NoSuchElementException("网页消息原回执不在保留窗口。");
            }
            if (value.receipt().status().equals("PERSISTED") && (!instance.equals(value.instance()) || !active.getAsBoolean())) {
                var receipt = new WebChat.Receipt(operation, value.receipt().messageId(), "INDETERMINATE",
                        "原工作拥有者已经丢失，实际入站结果未确认，不自动重放。");
                journal.operations().put(operation, new Operation(value.digest(), value.instance(), receipt, value.at()));
                saveJournal(room, journal);
                return receipt;
            }
            return value.receipt();
        });
    }

    /**
     * @param source 原网页消息与能力
     * @param parts 已归一化的受限片段
     * @return 真实提交的机器人消息，不表示网页已收到或已读
     */
    public CompletionStage<WebChat.Message> send(WebMessageData source, List<WebChat.Part> parts) {
        return transaction(source::checkCommandSource, state -> {
            var room = state.room(source.roomId());
            if (room.revision() != source.memberRevision()) {
                throw new SecurityException("网页成员已变更，旧消息不能继续借用发送能力。");
            }
            member(room, source.getUserData().getUserIdInPlatform());
            validateParts(state, room.id(), parts);
            var journal = journal(room.id());
            var message = append(room.id(), journal, WebChat.bot(), "TeaNeko", "MEMBER", true, source.getMessageId(), parts);
            saveJournal(room.id(), journal);
            return message;
        });
    }

    /**
     * 2026/11/06：指令台使用明确管理能力发送，不伪造网页成员的原生消息。
     * @param id 原房间标识
     * @param revision 捕获的房间版本
     * @param text 明确要发送的文本
     * @param guard 当前管理授权
     * @return 原事务实际保存的机器人消息
     */
    public CompletionStage<WebChat.Message> sendManaged(String id, long revision, String text, Runnable guard) {
        if (text.isBlank() || text.length() > 4096) {
            throw new IllegalArgumentException("指令台消息需要一至四千零九十六字符。");
        }
        return transaction(guard, state -> {
            var room = state.room(id);
            if (room.revision() != revision) {
                throw new IllegalStateException("网页房间已变化，请重新读取指令台目录。");
            }
            var journal = journal(id);
            var message = append(id, journal, WebChat.bot(), "TeaNeko", "MEMBER", true, "",
                    List.of(new WebChat.Part("text", text)));
            saveJournal(id, journal);
            return message;
        });
    }

    /**
     * @param id 已知消息编号，必须含网页聊天前缀
     * @param guard 原查询授权
     * @return 原聊天和原消息快照，历史结果不携带新的发送能力
     */
    public CompletionStage<Found> message(String id, Runnable guard) {
        String[] fields = id.split(":", -1);
        if (id.length() > 128 || fields.length != 3 || !fields[0].equals("web-msg")) {
            throw new IllegalArgumentException("网页消息编号无效。");
        }
        return read(guard, state -> new Found(state.room(fields[1]),
            requireMessage(journal(fields[1], EasyDataLock.READ), id)));
    }

    /**
     * @param room 原聊天
     * @param operation 上传幂等编号
     * @param png 已解码重编码的有界 PNG
     * @param guard 当前会话
     * @return 本聊天媒体号，不返回服务端路径或原字节
     */
    public CompletionStage<String> upload(String room, UUID operation, byte[] png, Runnable guard) {
        if (png.length == 0 || png.length > 1_048_576) {
            throw new IllegalArgumentException("网页图片超过一 MiB 上限。");
        }
        String id = generated("image:" + room, operation);
        String encoded = Base64.getEncoder().encodeToString(png);
        String digest = StableDigest.sha256(encoded);
        return transaction(guard, state -> {
            state.room(room);
            var old = state.catalog.media().get(id);
            if (old != null) {
                if (!old.digest().equals(digest) || !old.room().equals(room)) {
                    throw new IllegalArgumentException("上传编号已绑定其他图片。");
                }
                return id;
            }
            if (state.catalog.media().size() >= limits.get().media()) {
                throw new IllegalStateException("网页图片容量已满，请先删除未使用图片。");
            }
            write(data.apply("media-" + id), "png", encoded);
            state.catalog.media().put(id, new Media(room, digest, Instant.now()));
            state.save();
            return id;
        });
    }

    /**
     * @param room 已知聊天
     * @param id 本聊天图片号
     * @param guard 当前会话或已授权消息的读取守卫
     * @return 经过归一化的 PNG 字节
     */
    public CompletionStage<byte[]> image(String room, String id, Runnable guard) {
        return read(guard, state -> {
            state.room(room);
            state.media(room, id);
            var row = data.apply("media-" + id).atomic().find("png", EasyDataLock.READ);
            if (row == null || row.getValue().length() > 1_398_104) {
                throw new NoSuchElementException("网页图片不存在或超过容量。");
            }
            return Base64.getDecoder().decode(row.getValue());
        });
    }

    /**
     * @param room 已知聊天
     * @param guard 当前会话
     * @return 本聊天媒体号，不提供其他聊天的资源引用
     */
    public CompletionStage<List<String>> images(String room, Runnable guard) {
        return read(guard, state -> {
            state.room(room);
            return state.catalog.media().entrySet().stream().filter(item -> item.getValue().room().equals(room))
                .map(Map.Entry::getKey).toList();
        });
    }

    /**
     * @param room 当前聊天
     * @param id 当前图片
     * @param guard 当前会话
     * @return 未被当前持久窗口引用的图片已经删除
     */
    public CompletionStage<Void> deleteImage(String room, String id, Runnable guard) {
        return transaction(guard, state -> {
            state.room(room);
            state.media(room, id);
            if (journal(room).messages().stream().flatMap(message -> message.parts().stream())
                    .anyMatch(part -> part.kind().equals("IMAGE") && part.value().equals(id))) {
                throw new IllegalStateException("当前消息窗口仍引用这张图片，不能删除。");
            }
            var dto = data.apply("media-" + id).atomic();
            var row = dto.find("png", EasyDataLock.WRITE);
            if (row != null) {
                dto.delete(row);
            }
            state.catalog.media().remove(id);
            state.save();
            return true;
        }).thenAccept(ignored -> {});
    }

    /**
     * 2026/11/05：目录锁内删除房间、消息与图片，保留共享档案、配置及跨聊天记忆。
     * @param id 当前聊天标识
     * @param revision 用户确认的成员版本
     * @param guard 当前管理员会话
     * @return 已删除为真，重复删除为假
     * @throws ConcurrentModificationException 当确认后成员版本已经改变时
     */
    public CompletionStage<Boolean> deleteRoom(String id, long revision, Runnable guard) {
        return transaction(guard, state -> {
            var room = state.catalog.rooms().get(id);
            if (room == null) {
                return false;
            }
            if (room.revision() != revision) {
                throw new ConcurrentModificationException("聊天成员已变化，请刷新后重新确认删除。");
            }
            var media = state.catalog.media().entrySet().iterator();
            while (media.hasNext()) {
                var entry = media.next();
                if (entry.getValue().room().equals(id)) {
                    deleteRow("media-" + entry.getKey(), "png");
                    media.remove();
                }
            }
            deleteRow("room-" + id, "journal");
            state.catalog.rooms().remove(id);
            state.save();
            return true;
        });
    }

    /**
     * 2026/11/05：借用当前目录事务删除确切业务行，不绕过 EasyData 封装。
     * @param name 业务 DTO 名称
     * @param key 精确行键
     */
    private void deleteRow(String name, String key) {
        var rows = data.apply(name).atomic();
        var row = rows.find(key, EasyDataLock.WRITE);
        if (row != null) {
            rows.delete(row);
        }
    }

    /**
     * @param state 当前事务目录
     * @param room 原目标
     * @param parts 已归一化消息
     * @throws IllegalArgumentException 当片段数量、文本或类型无效时
     */
    private void validateParts(State state, String room, List<WebChat.Part> parts) {
        if (parts.isEmpty() || parts.size() > 100 || parts.stream().mapToInt(part -> part.value().length()).sum() > 65536) {
            throw new IllegalArgumentException("网页输出消息超过容量。");
        }
        for (var part : parts) {
            switch (part.kind()) {
                case "TEXT" -> { }
                case "IMAGE" -> state.media(room, part.value());
                case "REPLY" -> requireMessage(journal(room), part.value());
                case "AT" -> {
                    if (!part.value().equals(WebChat.SELF)) {
                        member(state.room(room), part.value());
                    }
                }
                default -> throw new IllegalArgumentException("网页输出不支持这种片段。");
            }
        }
    }

    /**
     * @param room 固定聊天
     * @param journal 当前窗口
     * @param profile 真实发送者
     * @param card 原名片
     * @param role 原平台角色
     * @param bot 是否机器人
     * @param origin 原触发消息
     * @param parts 受限片段
     * @return 刚加入窗口的真实消息
     */
    private WebChat.Message append(String room, Journal journal, WebChat.Profile profile, String card,
            String role, boolean bot, String origin, List<WebChat.Part> parts) {
        long sequence = journal.sequence() + 1;
        var message = new WebChat.Message("web-msg:" + room + ":" + sequence, sequence, profile, card, role, bot,
                origin, parts, Instant.now());
        journal.messages().add(message);
        int excess = journal.messages().size() - limits.get().messages();
        if (excess > 0) {
            journal.messages().subList(0, excess).clear();
        }
        return message;
    }

    /**
     * @param room 已知聊天
     * @return 当前窗口；序列号从持久末尾而非进程计数恢复
     */
    private Journal journal(String room) {
        return journal(room, EasyDataLock.WRITE);
    }

    /**
     * @param room 已知聊天
     * @param lock 读取窗口使用共享锁，修改仍使用排他锁
     * @return 当前窗口
     */
    private Journal journal(String room, EasyDataLock lock) {
        var row = data.apply("room-" + room).atomic().find("journal", lock);
        return row == null ? new Journal(0, List.of(), Map.of()) : mapper.readValue(row.getValue(), Journal.class);
    }

    /**
     * @param room 原聊天
     * @param journal 本事务窗口
     */
    private void saveJournal(String room, Journal journal) {
        long sequence = journal.messages().isEmpty() ? journal.sequence() : journal.messages().getLast().sequence();
        write(data.apply("room-" + room), "journal", mapper.writeValueAsString(new Journal(sequence, journal.messages(), journal.operations())));
    }

    /**
     * @param dto 固定业务 DTO
     * @param key 固定键
     * @param value 已受限序列化内容
     */
    private void write(IEasyDataDto dto, String key, String value) {
        var row = dto.atomic().find(key, EasyDataLock.WRITE);
        if (row == null) {
            row = dto.atomic().create(key, value);
        } else {
            row.setValue(value);
        }
        dto.atomic().save(row);
    }

    /**
     * @param guard 当前宿主能力
     * @param action 当前事务逻辑，禁止在其中调用外部 IO
     * @param <T> 实际结果
     * @return 提交后结果；返回时仍复核原会话
     */
    private <T> CompletionStage<T> transaction(Runnable guard, Function<State, T> action) {
        return transaction(guard, EasyDataLock.WRITE, action);
    }

    /**
     * 2026/11/06：轮询读取共享目录锁，不升级写锁；与删除和成员修改继续保持同一锁序。
     * @param guard 当前授权
     * @param action 无写入的快照投影
     * @param <T> 返回类型
     * @return 授权复核后的只读结果
     */
    private <T> CompletionStage<T> read(Runnable guard, Function<State, T> action) {
        return transaction(guard, EasyDataLock.READ, action);
    }

    /**
     * @param guard 当前会话
     * @param lock 本次目录锁模式
     * @param action 原事务动作
     * @param <T> 返回类型
     * @return 原事务提交和返回授权复核后的结果
     */
    private <T> CompletionStage<T> transaction(Runnable guard, EasyDataLock lock, Function<State, T> action) {
        var dto = data.apply("catalog");
        var result = new AtomicReference<T>();
        var task = dto.getTaskConfig("网页聊天事务");
        task.addTransactionTask(() -> {
            result.set(null);
            guard.run();
            var row = dto.atomic().find("catalog", lock);
            var catalog = row == null ? new Catalog(Map.of(), Map.of(), Map.of(), Map.of(), Map.of())
                : mapper.readValue(row.getValue(), Catalog.class);
            result.set(Objects.requireNonNull(action.apply(new State(dto, catalog, lock == EasyDataLock.WRITE))));
            guard.run();
        });
        return task.pushAsync().thenApply(ignored -> {
            guard.run();
            return Objects.requireNonNull(result.get());
        }).toCompletableFuture().minimalCompletionStage();
    }

    /**
     * @param purpose 服务端固定用途
     * @param operation 原非空操作编号
     * @return 按用途隔离的确定标识，便于同编号安全恢复
     */
    public static String generated(String purpose, UUID operation) {
        return UUID.nameUUIDFromBytes((WebChat.ACTOR + ":" + purpose + ":" + Objects.requireNonNull(operation))
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * @param value 用户显示名，不作为身份或授权
     * @return 有界清理名称
     */
    private static String name(String value) {
        if (value == null || value.isBlank() || value.length() > 80 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("网页显示名称须为一至八十字符且不能包含控制符。");
        }
        return value.strip();
    }

    /**
     * @param room 已知聊天
     * @param profile 已登记平台号
     * @return 实际成员，不能从浏览器角色字段推断
     */
    private static WebChat.Member member(WebChat.Room room, String profile) {
        return room.members().stream().filter(value -> value.profile().equals(profile)).findFirst()
                .orElseThrow(() -> new SecurityException("该档案不是当前网页聊天成员。"));
    }

    /**
     * @param journal 固定聊天窗口
     * @param id 原引用号
     * @return 原聊天中仍可读取的消息
     */
    private static WebChat.Message requireMessage(Journal journal, String id) {
        return journal.messages().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("原消息不存在、不属于当前聊天或已离开保留窗口。"));
    }

    /**
     * @param room 内部聊天
     * @return 不含 scope 的 HTTP 视图
     */
    public static WebChat.RoomView view(WebChat.Room room) {
        return new WebChat.RoomView(room.id(), room.kind(), room.name(), room.revision(), room.members());
    }

    /**
     * 本事务内部目录，不跨事务持有。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private final class State {
        final IEasyDataDto dto;
        final Catalog catalog;
        private final boolean writable;

        /**
         * @param dto 固定根 DTO
         * @param catalog 当前目录
         * @param writable 是否持有目录写入资格
         */
        State(IEasyDataDto dto, Catalog catalog, boolean writable) {
            this.writable = writable;
            this.dto = dto;
            this.catalog = catalog;
        }

        /** 持久化本事务已明确变更的元数据。 */
        void save() {
            if (!writable) {
                throw new IllegalStateException("只读网页聊天事务不能保存状态。");
            }
            write(dto, "catalog", mapper.writeValueAsString(catalog));
        }

        /**
         * @param id 原聊天
         * @return 已存在聊天
         */
        WebChat.Room room(String id) {
            var room = catalog.rooms().get(id);
            if (room == null) {
                throw new NoSuchElementException("网页聊天不存在。");
            }
            return room;
        }

        /**
         * @param id 原档案
         * @return 已存在档案
         */
        WebChat.Profile profile(String id) {
            var profile = catalog.profiles().get(id);
            if (profile == null) {
                throw new NoSuchElementException("网页档案不存在。");
            }
            return profile;
        }

        /**
         * @param room 固定聊天
         * @param id 原图片
         * @return 本聊天已上传资源
         */
        Media media(String room, String id) {
            var media = catalog.media().get(id);
            if (media == null || !media.room().equals(room)) {
                throw new SecurityException("图片不属于当前网页聊天。");
            }
            return media;
        }

        /**
         * @param id 原管理编号
         * @param digest 完整参数摘要
         * @return 原结果标识，不存在时为空
         */
        @Nullable String replay(UUID id, String digest) {
            var old = catalog.changes().get(Objects.requireNonNull(id));
            if (old != null) {
                if (!old.digest().equals(digest)) {
                    throw new IllegalArgumentException("网页管理编号已经绑定其他参数。");
                }
                return old.target();
            }
            catalog.changes().values().removeIf(value -> value.at().plusSeconds(86400).isBefore(Instant.now()));
            if (catalog.changes().size() >= 256) {
                throw new IllegalStateException("网页管理回执窗口已满。");
            }
            return null;
        }

        /**
         * @param id 原管理编号
         * @param digest 原参数摘要
         * @param target 原结果标识
         */
        void remember(UUID id, String digest, String target) {
            catalog.changes().put(id, new Change(digest, target, Instant.now()));
            save();
        }
    }

    /**
     * 持久认领结果，只有 acquired 才交接原消息。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param acquired 是否取得唯一执行权
     * @param room 固定聊天
     * @param message 新原生消息，重放时为空
     * @param receipt 原回执
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Claim(boolean acquired, WebChat.Room room, WebChat.@Nullable Message message, WebChat.Receipt receipt) {}

    /**
     * 原生查询的消息和所属聊天。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param room 原聊天
     * @param message 原消息
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Found(WebChat.Room room, WebChat.Message message) {}

    /**
     * 元数据目录，各个媒体及聊天窗口单独分行。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param profiles 已创建档案
     * @param rooms 已创建聊天
     * @param changes 有界管理幂等记录
     * @param media 有界媒体目录
     * @param submissions 全局入站目标与参数绑定
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Catalog(Map<String, WebChat.Profile> profiles, Map<String, WebChat.Room> rooms,
                           Map<UUID, Change> changes, Map<String, Media> media, Map<UUID, Submission> submissions) {
        /**
         * @param profiles 独立可变档案
         * @param rooms 独立可变聊天
         * @param changes 独立幂等记录
         * @param media 独立媒体目录
         * @param submissions 独立入站绑定
         */
        Catalog {
            profiles = new LinkedHashMap<>(profiles);
            rooms = new LinkedHashMap<>(rooms);
            changes = new LinkedHashMap<>(changes);
            media = new LinkedHashMap<>(media);
            submissions = new LinkedHashMap<>(submissions);
        }
    }

    /**
     * 聊天窗口和入站账本共事务保存。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param sequence 原递增序列
     * @param messages 有界原消息
     * @param operations 有界受理记录
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Journal(long sequence, List<WebChat.Message> messages, Map<UUID, Operation> operations) {
        /**
         * @param sequence 原持久序列
         * @param messages 独立窗口
         * @param operations 独立原记录
         */
        Journal {
            messages = new ArrayList<>(messages);
            operations = new LinkedHashMap<>(operations);
        }
    }

    /**
     * 管理参数绑定。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param digest 原请求摘要
     * @param target 原结果
     * @param at 原时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Change(String digest, String target, Instant at) {}

    /**
     * 入站唯一物理拥有者。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param digest 原请求摘要
     * @param instance 原进程
     * @param receipt 原事实
     * @param at 原受理时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Operation(String digest, UUID instance, WebChat.Receipt receipt, Instant at) {}

    /**
     * 图片固定归属，不记录任意路径。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param room 所属聊天
     * @param digest 标准化内容摘要
     * @param at 保存时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Media(String room, String digest, Instant at) {}

    /**
     * 操作编号固定目标，不能借切换聊天重新投递。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param room 原目标
     * @param digest 原参数
     * @param at 原时刻
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Submission(String room, String digest, Instant at) {}
}
