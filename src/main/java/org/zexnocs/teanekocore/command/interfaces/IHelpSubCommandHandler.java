package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandMapData;

/**
 * 帮助指令处理器接口
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
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
     */
    void handleSubHelp(CommandData<?> commandData, CommandMapData mapData, String[] args);
}
