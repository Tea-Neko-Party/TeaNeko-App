package org.zexnocs.teanekoplugin.general.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询一个 scopeID 中的活跃度信息
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
@Service
@RequiredArgsConstructor
public class GroupActivityQueryService {
    private final TeaNekoScopeService teaNekoScopeService;
    private final GroupActivityService groupActivityService;

    /**
     * 获取一个低活跃度成员的细节信息。
     *
     * @param client 用于获取各种工具类
     * @param map    指定 scopeID 中的活跃度信息，key 是 userID，value 是 (活跃度数据, 违反的活跃度规则)
     * @param userId 指定的用户号
     * @return 该成员的详细信息
     */
    public List<ITeaNekoMessage> getOneDetail(
            ITeaNekoClient client,
            String groupId,
            Map<String, Pair<GroupActivityData, GroupActivityRule>> map,
            String userId) {
        // 获取指定成员的低活跃度信息
        var pair = map.get(userId);
        var tools = client.getTeaNekoToolbox();
        var msgListBuilder = tools.getMessageSenderTools().getMsgListBuilder();
        if(pair == null) {
            return msgListBuilder
                    .addTextMessage("该成员不是低活跃度成员喵。")
                    .build();
        }
        var activityData = pair.first();
        var rule =  pair.second();
        // 使用中国式的时间格式设置上次发言时间和入群时间
        var util = ChinaDateUtil.Instance;
        var lastSpeakTime = util.convertToDateTimeString(activityData.getLastSpeakTimeMs());
        var joinTime = util.convertToDateTimeString(activityData.getJoinTimeMs());
        // 计算距离上次发言的时间
        var lastSpeakDelta = activityData.getSpeak();
        msgListBuilder.addTextMessage(String.format("""
                    群 %s 检测成员低活跃度
                    原因：%s
                    
                    用户详细信息：
                    账号：%s
                    昵称：%s
                    群昵称：%s
                    头衔：%s
                    群等级：%s
                    加入时间：%s
                    上次发言时间：%s
                    距离上次发言过了 %s 天
                    """,
                groupId, rule.getExpressionString(),
                userId, activityData.getNickname(), activityData.getCard(), activityData.getTitle(),
                activityData.getLevel(), joinTime, lastSpeakTime, lastSpeakDelta));
        var avatar = tools.getAvatarGetter().getUrl(userId);
        if(avatar != null) {
            msgListBuilder.addImageMessage(avatar);
        }
        msgListBuilder.addTextMessage("""
                    
                    /ga 踢出 %s %s
                    /ga 提醒 %s %s"""
                .formatted(groupId, userId,
                        groupId, userId));
        return msgListBuilder.build();
    }

    /**
     * 发送一个群转发消息，内容是低活跃度成员的列表。
     *
     * @param client  用于获取各种工具类
     * @param groupId 指定的群号
     * @param map     指定 scopeID 中的活跃度信息，key 是 userID，value 是 (活跃度数据, 违反的活跃度规则)
     * @return {@link List }<{@link List }<{@link ITeaNekoMessage }>>
     */
    public List<List<ITeaNekoMessage>> getAllDetail(
            ITeaNekoClient client,
            String groupId,
            Map<String, Pair<GroupActivityData, GroupActivityRule>> map) {
        if(map == null || map.isEmpty()) {
            var tools = client.getTeaNekoToolbox();
            var msgListBuilder = tools.getMessageSenderTools().getMsgListBuilder();
            return List.of(msgListBuilder.addTextMessage("该群没有低活跃度成员或者还没有扫描喵。").build());
        }
        List<List<ITeaNekoMessage>> messageList = new ArrayList<>();
        for(var key: map.keySet()) {
            messageList.add(getOneDetail(client, groupId, map, key));
        }
        return messageList;
    }
}
