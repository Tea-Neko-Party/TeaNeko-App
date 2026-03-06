package org.zexnocs.teanekoclient.onebot.utils;

import org.jspecify.annotations.NonNull;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotRawMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotSenderData;
import org.zexnocs.teanekoclient.onebot.event.OnebotEventShareComponent;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.UUID;

/**
 * 将 onebot 消息数据转换为 TeaNeko 消息数据的工具类。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
public enum OnebotMessageDataConvertUtils {
    Instance;

    /**
     * 根据 onebotMessageData 解析 teaNekoMessageData
     *
     * @param onebotData 原始的 onebot 消息数据
     * @param eventShareComponent 共享组件
     * @param uuid 发送事件的用户的 UUID
     * @return {@link Pair }<{@link OnebotRawMessageData }, {@link OnebotMessageData }>
     */
    public OnebotMessageData parse(OnebotRawMessageData onebotData,
                                   OnebotEventShareComponent eventShareComponent,
                                   UUID uuid) {
        // 构造 teaNekoData，使用 onebotData 中的字段进行转换
        var userData = getTeaNekoUserData(onebotData, getTeaNekoMessageType(onebotData), uuid);
        var messageType = getTeaNekoMessageType(onebotData);
        var client = eventShareComponent.onebotTeaNekoClient;
        return OnebotMessageData.builder()
                .time(ChinaDateUtil.Instance.convertToChinaZonedDateTime(onebotData.getTime() * 1000L))
                .messageId(String.valueOf(onebotData.getMessageId()))
                .messages(onebotData.getMessage())
                .messageType(messageType)
                .userData(userData)
                .client(client)
                .onebotRawMessageData(onebotData)
                .scopeId(eventShareComponent.teaNekoCommandConverter.getScopeId(messageType, userData, client))
                .build();
    }

    /**
     * 根据 onebotData 中的 messageType 和 subType 字段，转换成对应的 TeaNekoMessageType 枚举值
     *
     * @param onebotData 原始的 onebot 消息数据
     * @return {@link TeaNekoMessageType } 转换后的 TeaNekoMessageType 枚举值
     */
    public @NonNull TeaNekoMessageType getTeaNekoMessageType(OnebotRawMessageData onebotData) {
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
    public TeaNekoUserData getTeaNekoUserData(OnebotRawMessageData onebotData,
                                              TeaNekoMessageType teaNekoMessageType,
                                              UUID uuid) {
        var senderData = onebotData.getSender();
        var nickname = senderData.getNickname() == null ? "user" : senderData.getNickname();
        return TeaNekoUserData.builder()
                .uuid(uuid)
                .userIdInPlatform(String.valueOf(onebotData.getUserId()))
                .nickname(nickname)
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
    public @NonNull CommandPermission getCommandPermission(TeaNekoMessageType teaNekoMessageType,
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
}
