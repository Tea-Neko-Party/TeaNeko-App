package org.zexnocs.teanekoapp.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.interfaces.ICommandConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * TeaNekoApp 的指令转换器。将 ITeaNekoMessageData 转化成 CommandData。
 *
 * @author zExNocs
 * @date 2026/02/25
 * @since 4.0.9
 */
@Service
@RequiredArgsConstructor
public class TeaNekoCommandConverter implements ICommandConverter<ITeaNekoMessageData> {
    private final TeaNekoScopeService teaNekoScopeService;

    /**
     * 将数据解析成指令数据对象。
     * 用于子类的实现。
     *
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    @Override
    public CommandData<ITeaNekoMessageData> __parse(ITeaNekoMessageData data) {
        var senderData = data.getUserData();
        // 预处理消息列表
        var messageList = _preProcessMessageList(data.getMessages());

        // 根据消息列表获取解析后的字符串列表
        var parsedList = _getParsedList(messageList);

        // 如果为空则返回 null，表示没有指令
        if(parsedList.isEmpty()) {
            return null;
        }

        return CommandData.<ITeaNekoMessageData>builder()
                .body(parsedList.getFirst())
                .args(parsedList.subList(1, parsedList.size()).toArray(new String[0]))
                .scope(getScope(data))
                .scopeId(data.getScopeId())
                .permission(senderData.getRole())
                .senderId(senderData.getUuid().toString())
                .rawData(data)
                .clientClass(data.getClient().getClass())
                .build();
    }

    /**
     * 根据
     * {@code T}
     * 获取到
     * {@code scope}
     *
     * @param data 需要被转化的数据
     * @return {@link CommandScope }
     */
    public CommandScope getScope(ITeaNekoMessageData data) {
        return switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> CommandScope.PRIVATE;
            case GROUP -> CommandScope.GROUP;
            default -> CommandScope.OTHER;
        };
    }

    /**
     * 预处理 messageList，删除 reply + at 的情况。
     *
     * @param messageList 需要被转化的数据
     * @return 预处理后的消息列表
     */
    List<? extends ITeaNekoMessage> _preProcessMessageList(List<? extends ITeaNekoMessage> messageList) {
        var result = new ArrayList<>(messageList);
        // 处理 reply 的情况：如果第一个是 reply，则删除
        if(!result.isEmpty() && result.getFirst().getType().equalsIgnoreCase("reply")) {
            // 删除 reply
            result.removeFirst();
            // 一般 reply 后面会跟着一个 at 信息，如果是 at 信息，则删除
            if(!result.isEmpty() && result.getFirst().getType().equalsIgnoreCase("at")) {
                result.removeFirst();
            }
        }
        return result;
    }

    /**
     * 获取解析后的消息列表
     * @param messageList 消息列表
     * @return 解析后的消息列表
     */
    private List<String> _getParsedList(List<? extends ITeaNekoMessage> messageList) {
        return messageList
                .stream()
                .map(ITeaNekoMessage::getContent)      // 转化成 Stream<ITeaNekoContent>
                .map(ITeaNekoContent::toCommandArgs)   // 转化成 Stream<String[]>
                .flatMap(Arrays::stream)               // 扁平化为 Stream<String>
                .filter(s -> !s.isBlank())       // 过滤掉空字符串
                .toList();
    }


    /**
     * 根据消息类型和用户数据，构造一个作用域 ID，用于在 TeaNeko 中区分不同来源的消息。
     *
     * @param type 消息类型
     * @param userData 用户数据
     * @param client 客户端，用于构造群消息的作用域 ID
     * @return {@link String } 构造后的作用域 ID
     */
    public String getScopeId(TeaNekoMessageType type,
                             TeaNekoUserData userData,
                             ITeaNekoClient client) {
        // 构造作用域 ID
        return switch (type) {
            case PRIVATE, PRIVATE_TEMP -> teaNekoScopeService.getPrivateScopeId(userData.getUuid());
            case GROUP -> teaNekoScopeService.getGroupScopeId(client, userData.getGroupId());
            default -> "other@" + userData.getUuid();
        };
    }
}
