package org.zexnocs.teanekoplugin.general.info.personal;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import org.zexnocs.teanekoplugin.general.info.InfoService;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 个人简介服务类，提供获取和设置个人简介信息的功能。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Service
@PluginOwned("general.info")
@RequiredArgsConstructor
public class PersonalInfoService {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    public static final String DEBUG_NAMESPACE = "个人简介修改";
    private static final String PERSONAL_INFO_KEY = "personal_info";
    private static final int MAXIMUM_LENGTH = 255;

    /**
     * 获取个人介绍信息
     *
     * @param uuid 用于本次处理的 {@code uuid} 数据
     * @return 个人介绍信息
     */
    public String getPersonInfo(UUID uuid) {
        var dto = GeneralEasyData.of(InfoService.INFO_NAMESPACE).get(uuid.toString());
        var personalInfo = dto.get(PERSONAL_INFO_KEY, PersonalInfoData.class);
        if(personalInfo == null || personalInfo.getPersonalInfo().isBlank()) {
            return "无";
        }
        return personalInfo.getPersonalInfo();
    }

    /**
     * 设置个人介绍信息
     *
     * @param teaUser 用户
     * @param personalInfo 个人介绍信息
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    public CompletionStage<String> setPersonInfo(UUID teaUser, String personalInfo) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("general.info");
        // 判断个人介绍信息长度
        if (personalInfo.length() > MAXIMUM_LENGTH) {
            return CompletableFuture.completedStage("个人介绍信息过长，最大长度为" + MAXIMUM_LENGTH + "个字符");
        }
        var util = ChinaDateUtil.Instance;
        var dto = GeneralEasyData.of(InfoService.INFO_NAMESPACE).get(teaUser.toString());
        var currentTime = Instant.now();
        var currentDate = util.convertToChinaDateTime(currentTime);
        // debug记录
        var debugData = DebugEasyData.of(DEBUG_NAMESPACE).get(teaUser.toString());
        // 设置个人介绍信息
        var newData = new PersonalInfoData(personalInfo, currentTime);
        var task = dto.getTaskConfig("设置个人介绍信息");
        task.set(PERSONAL_INFO_KEY, newData);
        var debugTask = debugData.getTaskConfig("记录个人介绍信息");
        debugTask.set(util.convertToString(currentDate), personalInfo);
        task.merge(debugTask);
        return task.addTransactionTask(() -> plugins.check(pluginToken)).pushAsync().thenApply(ignored -> "个人介绍信息设置成功喵~");
    }
}
