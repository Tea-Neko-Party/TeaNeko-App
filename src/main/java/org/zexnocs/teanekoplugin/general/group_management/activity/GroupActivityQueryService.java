package org.zexnocs.teanekoplugin.general.group_management.activity;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekocore.framework.pair.IPair;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询一个 scopeID 中的活跃度信息
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 * @version 4.9.0-l
 */
@Service
@RequiredArgsConstructor
@PluginOwned("general.group-management")
public class GroupActivityQueryService {
    /**
     * 获取一个低活跃度成员的细节信息。
     *
     * @param client 用于获取各种工具类
     * @param map    指定 scopeID 中的活跃度信息，key 是 userID，value 是 (活跃度数据, 违反的活跃度规则)
     * @param userId 指定的用户号
     * @return 该成员的详细信息
     */
    public List<ITeaNekoContent> getOneDetail(
            ITeaNekoClient client,
            String groupId,
            Map<String, IPair<GroupActivityData, GroupActivityRule>> map,
            String userId) {
        // 获取指定成员的低活跃度信息
        var pair = map.get(userId);
        var tools = client.getTeaNekoToolbox();
        var msgListBuilder = tools.getMessageSenderTools().getMsgListBuilder();
        if(pair == null) {
            return msgListBuilder
                    .addText("该成员不是低活跃度成员喵。")
                    .build();
        }
        var activityData = pair.first();
        var rule =  pair.second();
        // 使用中国式的时间格式设置上次发言时间和入群时间
        var util = ChinaDateUtil.Instance;
        var lastSpeakTime = util.convertToDateTimeString(activityData.getLastSpeakTime());
        var joinTime = util.convertToDateTimeString(activityData.getJoinTime());
        // 计算距离上次发言的时间
        var lastSpeakDelta = activityData.getSpeak();
        msgListBuilder.addText(String.format("""
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
            msgListBuilder.addImage(avatar);
        }
        msgListBuilder.addText("""
                    
                    /ga 踢出 %s %s
                    /ga 提醒 %s %s"""
                .formatted(groupId, userId,
                        groupId, userId));
        return msgListBuilder.build();
    }

    /**
     * 发送一个群转发消息，内容是低活跃度成员的列表。
     * 2026/11/07：返回说明使用可正确渲染的中文消息分组合同。
     *
     * @param client  用于获取各种工具类
     * @param groupId 指定的群号
     * @param map     指定 scopeID 中的活跃度信息，key 是 userID，value 是 (活跃度数据, 违反的活跃度规则)
     * @return 按转发节点分组的消息内容列表
     */
    public List<List<ITeaNekoContent>> getAllDetail(
            ITeaNekoClient client,
            String groupId,
            Map<String, IPair<GroupActivityData, GroupActivityRule>> map) {
        if(map == null || map.isEmpty()) {
            var tools = client.getTeaNekoToolbox();
            var msgListBuilder = tools.getMessageSenderTools().getMsgListBuilder();
            return List.of(msgListBuilder.addText("该群没有低活跃度成员或者还没有扫描喵。").build());
        }
        List<List<ITeaNekoContent>> messageList = new ArrayList<>();
        for(var key: map.keySet()) {
            messageList.add(getOneDetail(client, groupId, map, key));
        }
        return messageList;
    }
}
