package org.zexnocs.teanekoplugin.general.signin;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 签到指令。
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.1.0
 */
@Description("签到获取猫猫币喵！")
@Command(value = {"/sign-in", "/签到", "签到", "签到喵", "/签到喵"},
        permission = CommandPermission.ALL)
@RequiredArgsConstructor
public class SignInCommand {
    /// 签到服务
    private final SignInService signInService;

    @DefaultCommand
    public void signIn(CommandData<ITeaNekoMessageData> data) {
        var messageData = data.getRawData();
        messageData.getMessageSender()
                .sendAtReplyMessage(signInService
                        .signIn(messageData.getUserData().getUuid(),
                                System.currentTimeMillis()));
    }
}
