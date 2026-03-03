package org.zexnocs.teanekoclient.onebot.event.message;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotSenderData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekoclient.onebot.event.PostReceiveEvent;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.event.AbstractEvent;
import org.zexnocs.teanekocore.event.core.Event;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.UUID;

/**
 * 在发送 {@link OnebotMessageReceiveEvent} 之前触发的事件，主要用于构造 {@link TeaNekoMessageData}。
 *
 * @author zExNocs
 * @date 2026/03/02
 * @since 4.0.11
 */
@Event(OnebotPreMessageReceiveEvent.KEY)
public class OnebotPreMessageReceiveEvent extends AbstractEvent<OnebotMessageData> {
    public final static String KEY = PostReceiveEvent.SUFFIX_KEY + "message";

    /**
     * 共享组件，包含了事件处理过程中需要用到的各种组件和工具类，例如 JSON 解析器、日志记录器等。
     */
    private final OnebotEventShareComponent eventShareComponent;

    /**
     * 接收原始信息字符串和共享组件。
     * @param information 原始信息字符串
     * @param eventShareComponent 共享组件
     */
    public OnebotPreMessageReceiveEvent(String information, OnebotEventShareComponent eventShareComponent) {
        super(OnebotMessageData.fromJson(information, eventShareComponent.objectMapper));
        this.eventShareComponent = eventShareComponent;
    }

    /**
     * 在通知处理器之后调用的方法。
     * 尝试解析原始信息字符串，得到 onebotMessageData 和 teaNekoMessageData 的解析结果，
     * 并构造一个新的 OnebotMessageReceiveEvent 事件，并将其推送。
     */
    @Override
    public void _afterNotify() {
        var data = getData();
        this.eventShareComponent.iTeaUserService
                .getOrCreate(this.eventShareComponent.onebotTeaNekoClient, String.valueOf(data.getUserId()))
                .thenComposeTask(uuid ->
                        this.eventShareComponent.iEventService
                                .pushEventWithFuture(new OnebotMessageReceiveEvent(data,
                                        parse(data, eventShareComponent, uuid))))
                .finish();
    }

    /**
     * 根据 onebotMessageData 解析 teaNekoMessageData
     *
     * @param onebotData 原始的 onebot 消息数据
     * @param eventShareComponent 共享组件
     * @param uuid 发送事件的用户的 UUID
     * @return {@link Pair }<{@link OnebotMessageData }, {@link TeaNekoMessageData }>
     */
    private static TeaNekoMessageData parse(OnebotMessageData onebotData,
                                            OnebotEventShareComponent eventShareComponent,
                                             UUID uuid) {
        // 构造 teaNekoData，使用 onebotData 中的字段进行转换
        return TeaNekoMessageData.builder()
                .time(ChinaDateUtil.Instance.convertToChinaZonedDateTime(onebotData.getTime() * 1000L))
                .messageId(String.valueOf(onebotData.getMessageId()))
                .messages(onebotData.getMessage())
                .messageType(getTeaNekoMessageType(onebotData))
                .userData(getTeaNekoUserData(onebotData, getTeaNekoMessageType(onebotData), uuid))
                .client(eventShareComponent.onebotTeaNekoClient)
                .build();
    }

    /**
     * 根据 onebotData 中的 messageType 和 subType 字段，转换成对应的 TeaNekoMessageType 枚举值
     *
     * @param onebotData 原始的 onebot 消息数据
     * @return {@link TeaNekoMessageType } 转换后的 TeaNekoMessageType 枚举值
     */
    private static @NonNull TeaNekoMessageType getTeaNekoMessageType(OnebotMessageData onebotData) {
        TeaNekoMessageType type;
        var rawType = onebotData.getMessageType();
        if(rawType.equalsIgnoreCase("private")) {
            if(onebotData.getSubType().equalsIgnoreCase("group")) {
                type = TeaNekoMessageType.PRIVATE_TEMP;
            } else {
                type = TeaNekoMessageType.PRIVATE;
            }
        } else if (rawType.equalsIgnoreCase("group")) {
            type = TeaNekoMessageType.GROUP;
        } else {
            type = TeaNekoMessageType.OTHER;
        }
        return type;
    }

    /**
     * 根据 onebot Data 构造 user data
     *
     * @param onebotData 原始的 onebot 消息数据
     * @param teaNekoMessageType 消息类型，用于构造 role 字段
     * @param uuid 发送事件的用户的 UUID
     * @return {@link TeaNekoUserData } 构造后的
     */
    private static TeaNekoUserData getTeaNekoUserData(OnebotMessageData onebotData,
                                                      TeaNekoMessageType teaNekoMessageType,
                                                      UUID uuid) {
        var senderData = onebotData.getSender();
        var nickname = senderData.getNickname() == null ? "user" : senderData.getNickname();
        return TeaNekoUserData.builder()
                .uuid(uuid)
                .userIdInPlatform(String.valueOf(onebotData.getUserId()))
                .nickname(nickname)
                .card(senderData.getCard())
                .level(getLevel(senderData))
                .role(getCommandPermission(teaNekoMessageType, senderData))
                .groupId(String.valueOf(onebotData.getGroupId()))
                .build();
    }

    /**
     * 根据消息类型和 senderData 中的 role 字段，转换成对应的 CommandPermission 枚举值
     * todo: 构造 debugger 权限
     *
     * @param teaNekoMessageType 消息类型，用于判断默认权限
     * @param senderData onebot 消息中的 sender 字段数据，用于获取 role 字段
     * @return {@link CommandPermission } 转换后的 CommandPermission 枚举值
     */
    private static @NonNull CommandPermission getCommandPermission(TeaNekoMessageType teaNekoMessageType,
                                                                   OnebotSenderData senderData) {
        return switch (teaNekoMessageType) {
            // 如果是私聊消息或者临时消息，默认权限是 OWNER
            case PRIVATE, PRIVATE_TEMP -> CommandPermission.OWNER;
            // 如果是群消息，根据 senderData 中的 role 字段进行转换，owner -> OWNER，admin -> ADMIN，其他 -> MEMBER
            case GROUP -> {
                var rawRole = senderData.getRole();
                if(rawRole.equalsIgnoreCase("owner")) {
                    yield CommandPermission.OWNER;
                } else if (rawRole.equalsIgnoreCase("admin")) {
                    yield CommandPermission.ADMIN;
                } else {
                    yield CommandPermission.MEMBER;
                }
            }
            // 其他类型的消息，默认权限是 MEMBER
            default -> CommandPermission.MEMBER;
        };
    }

    /**
     * 根据 senderData 中的 level 字段，解析出一个整数，如果解析失败或者该字段不存在，则默认值为 100
     *
     * @param senderData onebot 消息中的 sender 字段数据
     * @return int
     */
    private static int getLevel(OnebotSenderData senderData) {
        try {
            if (senderData.getLevel() != null) {
                return Integer.parseInt(senderData.getLevel());
            } else {
                return 100;
            }
        } catch (NumberFormatException e) {
            return 100;
        }
    }
}
