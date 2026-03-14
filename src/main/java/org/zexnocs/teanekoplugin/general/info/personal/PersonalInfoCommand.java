package org.zexnocs.teanekoplugin.general.info.personal;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;

/**
 * 个人简介命令，允许用户设置个人简介信息。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Description("""
        设置个人简介信息。
        格式：/个人简介 <内容>
        最多 200 个字符。换行会被删掉~""")
@Command(value = {"/personal_info", "/pi", "/个人简介", "/个人介绍"},
        permission = CommandPermission.ALL)
public class PersonalInfoCommand {
    private final PersonalInfoService personalInfoService;

    @Autowired
    public PersonalInfoCommand(PersonalInfoService personalInfoService) {
        this.personalInfoService = personalInfoService;
    }

    @Description("""
            设置个人简介信息。
            格式：/个人简介 <内容>
            例：/个人简介 我是一个可爱的猫娘~""")
    @DefaultCommand
    public void onSet(CommandData<ITeaNekoMessageData> commandData, List<String> personalInfo) {
        var data = commandData.getRawData();
        var teaUser = data.getUserData().getUuid();
        var infoString = String.join(" ", personalInfo);
        // 预处理一下 personalInfo，删除所有的换行符
        infoString = infoString.replaceAll("\\r?\\n", "");
        data.getMessageSender()
                .sendTextMessage(personalInfoService.setPersonInfo(teaUser, infoString));
    }
}
