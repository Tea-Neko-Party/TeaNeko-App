package org.zexnocs.teanekoplugin.general.info.messageboard;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 留言信息服务类。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Service("messageInfoService")
@PluginOwned("general.info")
@RequiredArgsConstructor
public class MessageBoardInfoService {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
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
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    public CompletionStage<String> setMessage(UUID sender, UUID target, String message) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("general.info");
        // 判断留言信息长度
        if (message.length() > MAXIMUM_LENGTH) {
            return CompletableFuture.completedStage("留言信息过长，最大长度为" + MAXIMUM_LENGTH + "个字符");
        }
        var util = ChinaDateUtil.Instance;
        var currentTime = Instant.now();
        var timeInfo = util.convertToDateTimeString(currentTime);
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(target.toString());
        var debugDto = DebugEasyData.of(DEBUG_NAMESPACE).get(target.toString());
        // 设置留言信息
        var newData = new MessageBoardInfoData(message, sender, currentTime);
        var task = dto.getTaskConfig("设置留言信息");
        var debugTask = debugDto.getTaskConfig("记录留言信息");
        task.set(sender.toString(), newData);
        debugTask.set(sender + "_" + timeInfo, message);
        task.merge(debugTask);
        return task.addTransactionTask(() -> plugins.check(pluginToken)).pushAsync().thenApply(ignored -> "给予留言成功喵~");
    }
}
