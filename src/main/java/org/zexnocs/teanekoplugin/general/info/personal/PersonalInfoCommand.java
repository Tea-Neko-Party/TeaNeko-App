package org.zexnocs.teanekoplugin.general.info.personal;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 个人简介命令，允许用户设置个人简介信息。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("""
        设置个人简介信息。
        格式：/个人简介 <内容>
        最多 200 个字符。换行会被删掉~""")
@Command(value = {"/personal_info", "/pi", "/个人简介", "/个人介绍"},
        permission = CommandPermission.ALL)
public class PersonalInfoCommand {
    private final PersonalInfoService personalInfoService;

    /**
     * 注入简介业务服务，将持久化与用户反馈组合为同一完成链。
     *
     * @param personalInfoService 由装配方提供的 {@code PersonalInfoService} 协作依赖
     */
    @Autowired
    public PersonalInfoCommand(PersonalInfoService personalInfoService) {
        this.personalInfoService = personalInfoService;
    }

    /**
     * 设置个人简介信息；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param personalInfo 用于本次处理的 {@code personalInfo} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            设置个人简介信息。
            格式：/个人简介 <内容>
            例：/个人简介 我是一个可爱的猫娘~""")
    @DefaultCommand
    public CompletionStage<Void> onSet(CommandData<ITeaNekoMessageData> commandData, List<String> personalInfo) {
        var data = commandData.getRawData();
        var teaUser = data.getUserData().getUuid();
        var infoString = String.join(" ", personalInfo);
        // 预处理一下 personalInfo，删除所有的换行符
        infoString = infoString.replaceAll("\\r?\\n", "");
        return personalInfoService.setPersonInfo(teaUser, infoString)
                .thenCompose(message -> data.getMessageSender().sendTextMessageAsync(message));
    }
}
