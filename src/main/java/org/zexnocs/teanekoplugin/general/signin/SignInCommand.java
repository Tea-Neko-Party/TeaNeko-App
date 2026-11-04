package org.zexnocs.teanekoplugin.general.signin;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * 签到指令。
 *
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.7.6-l
 */
@Description("签到获取猫猫币喵！")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "每日签到", value = {"/sign-in", "/签到", "签到", "签到喵", "/签到喵"},
        permission = CommandPermission.ALL)
@RequiredArgsConstructor
public class SignInCommand {
    /// 签到服务
    private final SignInService signInService;

    /**
     * 使用原签到业务执行当前用户操作，等待合并事务及后续回复结束。
     *
     * @param data 本次处理的输入数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @DefaultCommand
    public CompletionStage<Void> signIn(CommandData<ITeaNekoMessageData> data) {
        var messageData = data.getRawData();
        return signInService.signIn(messageData.getUserData().getUuid(),
                Instant.now(), messageData.getMessageSender());
    }
}
