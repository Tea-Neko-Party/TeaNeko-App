package org.zexnocs.teanekoplugin.general.info.messageboard;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 留言信息服务类。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Service("messageInfoService")
public class MessageBoardInfoService {
    public static final String DEBUG_NAMESPACE = "留言修改";
    private static final String DATABASE_NAMESPACE = "message_info";
    private static final int MAXIMUM_LENGTH = 255;

    /**
     * 获取用户的留言信息
     *
     * @param uuid 用户的 uuid
     * @return 用户的留言信息列表
     */
    public List<MessageBoardInfoData> getMessageList(UUID uuid) {
        var list = new ArrayList<MessageBoardInfoData>();
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(uuid.toString());
        var keySet = dto.keySet();
        for(var key: keySet) {
            var data = dto.get(key, MessageBoardInfoData.class);
            if(data != null && !data.getMessage().isBlank()) {
                list.add(data);
            }
        }
        return list;
    }

    /**
     * 设置用户的留言信息
     *
     * @param sender 留言发送者
     * @param target 留言接收者
     * @param message 留言信息
     * @return 设置结果
     */
    public String setMessage(UUID sender, UUID target, String message) {
        // 判断留言信息长度
        if (message.length() > MAXIMUM_LENGTH) {
            return "留言信息过长，最大长度为" + MAXIMUM_LENGTH + "个字符";
        }
        var util = ChinaDateUtil.Instance;
        var currentTime = System.currentTimeMillis();
        var timeInfo = util.convertToString(util.convertToChinaDateTime(currentTime));
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(target.toString());
        var debugDto = DebugEasyData.of(DEBUG_NAMESPACE).get(target.toString());
        // 设置留言信息
        var newData = new MessageBoardInfoData(message, sender, currentTime);
        var task = dto.getTaskConfig("设置留言信息");
        var debugTask = debugDto.getTaskConfig("记录留言信息");
        task.set(sender.toString(), newData);
        debugTask.set(sender + "_" + timeInfo, message);
        task.merge(debugTask);
        task.push();
        return "给予留言成功喵~";
    }
}
