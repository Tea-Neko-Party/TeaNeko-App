package org.zexnocs.teanekoplugin.general.info.personal;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import org.zexnocs.teanekoplugin.general.info.InfoService;

import java.util.UUID;

/**
 * 个人简介服务类，提供获取和设置个人简介信息的功能。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Service
public class PersonalInfoService {
    public static final String DEBUG_NAMESPACE = "个人简介修改";
    private static final String PERSONAL_INFO_KEY = "personal_info";
    private static final int MAXIMUM_LENGTH = 255;

    /**
     * 获取个人介绍信息
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
     * @param teaUser 用户
     * @param personalInfo 个人介绍信息
     * @return 设置结果
     */
    public String setPersonInfo(UUID teaUser, String personalInfo) {
        // 判断个人介绍信息长度
        if (personalInfo.length() > MAXIMUM_LENGTH) {
            return "个人介绍信息过长，最大长度为" + MAXIMUM_LENGTH + "个字符";
        }
        var util = ChinaDateUtil.Instance;
        var dto = GeneralEasyData.of(InfoService.INFO_NAMESPACE).get(teaUser.toString());
        var currentTime = System.currentTimeMillis();
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
        task.push();
        return "个人介绍信息设置成功喵~";
    }
}
