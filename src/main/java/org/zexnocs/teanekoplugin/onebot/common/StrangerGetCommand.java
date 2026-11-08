package org.zexnocs.teanekoplugin.onebot.common;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekoclient.onebot.utils.OnebotContentListBuilder;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.concurrent.CompletionStage;

/**
 * OneBot 中获取陌生人信息的命令。格式：{@code /sg <QQ号>}。
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
@Description("获取陌生人信息。规格：/sg <QQ号>")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "陌生人资料", value = {"/sg", "/陌生人"},
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
@PluginOwned("onebot.common")
public class StrangerGetCommand {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;

    private final StrangerInfoGetSender strangerInfoGetSender;

    /**
     * 注入平台资料查询端口，查询完成后继续原消息发送链。
     *
     * @param strangerInfoGetSender 由装配方提供的 {@code StrangerInfoGetSender} 协作依赖
     * @param plugins 统一插件代次访问入口
     */
    public StrangerGetCommand(StrangerInfoGetSender strangerInfoGetSender, PluginAccess plugins) {
        this.plugins = plugins;
        this.strangerInfoGetSender = strangerInfoGetSender;
    }

    /**
     * 获取陌生人信息。规格：/sg &lt;QQ号&gt;；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("获取陌生人信息。规格：/sg <QQ号>")
    @DefaultCommand
    public CompletionStage<Void> execute(CommandData<OnebotMessageData> commandData, long userId) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        var data = commandData.getRawData();
        return strangerInfoGetSender.getPlatformUserInfo(String.valueOf(userId))
                        .thenCompose(strangerInfo -> {
                            plugins.check(pluginToken);
                            if(strangerInfo == null) {
                                return data.getMessageSender()
                                        .sendAtReplyMessageAsync("无法获取该用户的信息！");
                            }
                            var date = ChinaDateUtil.Instance.convertToChinaDateTime(strangerInfo.getRegTime() * 1000);
                            var regTimeStr = ChinaDateUtil.Instance.convertToString(date);
                            var messageList = OnebotContentListBuilder.builder()
                                    .addReply(data.getMessageId())
                                    .addImage(AvatarUtils.Instance.getUrl(userId))
                                    .addText(String.format("""
                                            QQ号: %d
                                            QID: %s
                                            昵称: %s
                                            性别: %s
                                            年龄: %d
                                            等级: %d
                                            个性签名: %s
                                            定义: %s
                                            大学: %s
                                            国家: %s
                                            省份: %s
                                            城市: %s
                                            地址: %s
                                            注册时间: %s
                                            生日: %d-%d-%d""",
                                            userId,
                                            strangerInfo.getQid(),
                                            strangerInfo.getNickname(),
                                            strangerInfo.getSex(),
                                            strangerInfo.getAge(),
                                            strangerInfo.getLevel(),
                                            strangerInfo.getLongNick(),
                                            strangerInfo.getPos(),
                                            strangerInfo.getCollege(),
                                            strangerInfo.getCountry(),
                                            strangerInfo.getProvince(),
                                            strangerInfo.getCity(),
                                            strangerInfo.getAddress(),
                                            regTimeStr,
                                            strangerInfo.getBirthdayYear(),
                                            strangerInfo.getBirthdayMonth(),
                                            strangerInfo.getBirthdayDay()))
                                    .build();
                            return data.getMessageSender()
                                    .addMessages(messageList)
                                    .sendAsync();
                        }).asStage();
    }
}
