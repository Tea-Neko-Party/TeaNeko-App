package org.zexnocs.teanekoplugin.general.info.personal;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

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
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Description("""
        设置个人简介信息。
        格式：/个人简介 <内容>
        最多 200 个字符。换行会被删掉~""")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "个人简介", value = {"/personal_info", "/pi", "/个人简介", "/个人介绍"},
        permission = CommandPermission.ALL)
@PluginOwned("general.info")
public class PersonalInfoCommand {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    private final PersonalInfoService personalInfoService;

    /**
     * 注入简介业务服务，将持久化与用户反馈组合为同一完成链。
     *
     * @param personalInfoService 由装配方提供的 {@code PersonalInfoService} 协作依赖
     * @param plugins 统一插件代次访问入口
     */
    @Autowired
    public PersonalInfoCommand(PersonalInfoService personalInfoService, PluginAccess plugins) {
        this.plugins = plugins;
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
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("general.info");
        var data = commandData.getRawData();
        var teaUser = data.getUserData().getUuid();
        var infoString = String.join(" ", personalInfo);
        // 预处理一下 personalInfo，删除所有的换行符
        infoString = infoString.replaceAll("\\r?\\n", "");
        return personalInfoService.setPersonInfo(teaUser, infoString)
                .thenCompose(message -> {
                    plugins.check(pluginToken);
                    return data.getMessageSender().sendTextMessageAsync(message);
                });
    }
}
