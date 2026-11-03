package org.zexnocs.teanekoclient.web;

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.tools.IAvatarGetter;
import org.zexnocs.teanekoapp.client.tools.IPlatformUserInfoConstructor;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;
import org.zexnocs.teanekoapp.response.api.IPlatformUserResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberInfoSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetGroupMemberListSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IGetMessageSender;
import org.zexnocs.teanekoapp.sender.api.sender_box.IPlatformUserGetSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * 网页平台原生工具箱，查询真实持久档案和窗口，不通过模型文本创建成员或权限。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class WebToolbox implements ITeaNekoToolbox {
    @Getter private final ILogger logger;
    @Getter private final WebMessageSenderTools messageSenderTools;
    private final WebChatStore store;
    private final WebMessageFactory messages;

    /**
     * @param logger 原诊断服务
     * @param messageSenderTools 原持久发送器
     * @param store 原网页目录
     * @param messages 原消息映射
     */
    public WebToolbox(ILogger logger, WebMessageSenderTools messageSenderTools, WebChatStore store, WebMessageFactory messages) {
        this.logger = logger;
        this.messageSenderTools = messageSenderTools;
        this.store = store;
        this.messages = messages;
    }

    /** @return 服务端固定机器人号，正确识别提及自己 */
    @Override
    public Optional<String> getSelfPlatformId() {
        return Optional.of(WebChat.SELF);
    }

    /** @return 原持久消息查询；结果不铸造新执行能力 */
    @Override
    public IGetMessageSender getGetMsgSender() {
        return id -> {
            CompletionStage<ITaskResult<ITeaNekoMessageData>> stage = store.message(id, () -> {})
                    .thenCompose(found -> messages.create(found.room(), found.message(), () -> {}, () -> {
                        throw new SecurityException("历史网页消息查询不提供新的执行或发送能力。");
                    })).thenApply(data -> new TaskResult<>(true, data));
            return TaskFuture.fromStage(logger, "网页原消息查询", stage);
        };
    }

    /** @return 实际网页群成员，不能查询其他平台号 */
    @Override
    public IGetGroupMemberInfoSender getGroupMemberInfoSender() {
        return (group, user) -> TaskFuture.fromStage(logger, "网页群成员查询", store.room(group, () -> {})
                .thenCompose(room -> profile(user).thenApply(profile -> member(room, profile))));
    }

    /** @return 原聊天成员与固定机器人，不推测不存在的联系人 */
    @Override
    public IGetGroupMemberListSender getGroupMemberListSender() {
        return group -> {
            CompletionStage<List<? extends IGroupMemberResponseData>> work = store.room(group, () -> {}).thenCompose(room -> {
                CompletionStage<List<IGroupMemberResponseData>> stage = java.util.concurrent.CompletableFuture.completedStage(new ArrayList<>());
                for (var value : room.members()) {
                    stage = stage.thenCompose(values -> profile(value.profile()).thenApply(profile -> {
                        values.add(member(room, profile));
                        return values;
                    }));
                }
                return stage.thenApply(values -> {
                    values.add(member(room, WebChat.bot()));
                    return List.copyOf(values);
                });
            });
            return TaskFuture.fromStage(logger, "网页群成员列表", work);
        };
    }

    /** @return 原网页平台档案 */
    @Override
    public IPlatformUserGetSender getPlatformUserGetSender() {
        return id -> TaskFuture.fromStage(logger, "网页用户查询", profile(id).thenApply(UserInfo::new));
    }

    /** @return 仅展示固定平台号，不阻塞线程等待数据库 */
    @Override
    public IPlatformUserInfoConstructor getPlatformUserInfoConstructorSender() {
        return id -> DefaultTeaNekoContentListBuilder.builder().addText("网页平台用户：" + id).build();
    }

    /** @return 明确的网页默认头像，不抓取外部地址 */
    @Override
    public IAvatarGetter getAvatarGetter() {
        return id -> "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXxkAAAAASUVORK5CYII=";
    }

    /**
     * @param id 原平台号
     * @return 原真实档案或明确的机器人档案
     */
    private CompletionStage<WebChat.Profile> profile(String id) {
        return id.equals(WebChat.SELF) ? java.util.concurrent.CompletableFuture.completedStage(WebChat.bot())
                : store.profile(id, () -> {});
    }

    /**
     * @param room 原网页群
     * @param profile 原平台档案
     * @return 原群内实际角色
     */
    private MemberInfo member(WebChat.Room room, WebChat.Profile profile) {
        if (!room.kind().equals("GROUP")) {
            throw new IllegalArgumentException("该网页聊天不是群聊。");
        }
        var member = profile.id().equals(WebChat.SELF) ? new WebChat.Member(WebChat.SELF, "TeaNeko", "MEMBER")
                : room.members().stream().filter(value -> value.profile().equals(profile.id())).findFirst()
                .orElseThrow(() -> new SecurityException("该网页用户不在当前群内。"));
        return new MemberInfo(profile, member);
    }

    /**
     * 原生群成员接口的网页投影，不提供的等级和头衔保持未知。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Getter
    private static final class MemberInfo implements IGroupMemberResponseData {
        private final String nickname;
        private final String userId;
        private final String card;
        private final String role;
        private final @Nullable Instant joinInstant = null;
        private final @Nullable Instant lastSentInstant = null;
        private final @Nullable Integer level = null;
        private final @Nullable String title = null;

        /**
         * @param profile 原档案
         * @param member 原成员
         */
        MemberInfo(WebChat.Profile profile, WebChat.Member member) {
            nickname = profile.name();
            userId = profile.id();
            card = member.card();
            role = member.role().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * 原生平台档案投影，零等级表示网页平台没有等级制度。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Getter
    private static final class UserInfo implements IPlatformUserResponseData {
        private final String platformUserId;
        private final String nickname;
        private final int level = 0;

        /** @param profile 原实际平台档案 */
        UserInfo(WebChat.Profile profile) {
            platformUserId = profile.id();
            nickname = profile.name();
        }
    }
}
