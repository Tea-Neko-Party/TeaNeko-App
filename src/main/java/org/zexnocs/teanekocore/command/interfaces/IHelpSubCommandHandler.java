package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandMapData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 帮助指令处理器接口
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IHelpSubCommandHandler {
    /**
     * 帮助指令的 key。一般为 help
     *
     * @return {@link String[] } 帮助指令的 key 数组
     */
    default String[] getHelpCommandKey() {
        return new String[]{"help"};
    }

    /**
     * 处理帮助指令
     *
     * @param commandData 指令数据
     * @param mapData     指令映射数据
     * @param args        剩下的指令参数
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> handleSubHelp(CommandData<?> commandData, CommandMapData mapData, String[] args) {
        return CompletableFuture.completedStage(null);
    }
}
