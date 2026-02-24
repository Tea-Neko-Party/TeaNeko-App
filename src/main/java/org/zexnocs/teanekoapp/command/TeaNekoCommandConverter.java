package org.zexnocs.teanekoapp.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.interfaces.ICommandConverter;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.framework.pair.Pair;

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
public class TeaNekoCommandConverter implements ICommandConverter<ITeaNekoMessageData> {
    /**
     * 将数据解析成指令数据对象。
     * 用于子类的实现。
     *
     * @param data 需要被转化的数据
     * @return 转化后的指令数据对象
     */
    @Override
    public CommandData<ITeaNekoMessageData> __parse(ITeaNekoMessageData data) {
        var senderData = data.getSenderData();
        // 预处理消息列表
        var messageList = _preProcessMessageList(data.getMessage());

        // 根据消息列表获取解析后的字符串列表
        var parsedList = _getParsedList(messageList);

        // 如果为空则返回 null，表示没有指令
        if(parsedList.isEmpty()) {
            return null;
        }
        // 获取作用域和作用域 ID
        var scopePair = getScopeAndScopeId(data);

        return CommandData.<ITeaNekoMessageData>builder()
                .body(parsedList.getFirst())
                .args(parsedList.subList(1, parsedList.size()).toArray(new String[0]))
                .scope(scopePair.first())
                .scopeId(scopePair.second())
                .permission(senderData.getRole())
                .senderId(senderData.getUuid().toString())
                .rawData(data)
                .build();
    }

    /**
     * 根据
     * {@code T}
     * 获取到
     * {@code Pair.of(scope, scopeId)}
     *
     * @param data 需要被转化的数据
     * @return {@link Pair }<{@link CommandScope }, {@link String }>
     */
    @Override
    public Pair<CommandScope, String> getScopeAndScopeId(ITeaNekoMessageData data) {
        var senderData = data.getSenderData();
        // 获取作用域
        var scope = switch (data.getMessageType()) {
            case PRIVATE, PRIVATE_TEMP -> CommandScope.PRIVATE;
            case GROUP -> CommandScope.GROUP;
        };

        // 构造作用域 ID
        var scopeId = switch (scope) {
            case PRIVATE -> "private@" + senderData.getUuid();
            case GROUP   -> data.getClient().getClientId() + "-group@" + senderData.getGroupId();
            // default 不可能发生，因为上面已经覆盖了所有情况
            default -> throw new RuntimeException("Invalid scope" + scope.toString());
        };
        return IndependentPair.of(scope, scopeId);
    }

    /**
     * 预处理 messageList，删除 reply + at 的情况。
     *
     * @param messageList 需要被转化的数据
     * @return 预处理后的消息列表
     */
    List<ITeaNekoMessage> _preProcessMessageList(List<ITeaNekoMessage> messageList) {
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
    private List<String> _getParsedList(List<ITeaNekoMessage> messageList) {
        return messageList
                .stream()
                .map(ITeaNekoMessage::getContent)      // 转化成 Stream<ITeaNekoContent>
                .map(ITeaNekoContent::toCommandArgs)   // 转化成 Stream<String[]>
                .flatMap(Arrays::stream)               // 扁平化为 Stream<String>
                .filter(s -> !s.isBlank())       // 过滤掉空字符串
                .toList();
    }
}
