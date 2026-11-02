package org.zexnocs.teanekoapp.config;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 为配置命令提供由独立业务服务持有的字段，不将外部权威值复制进 ConfigData。
 * 原命令仍负责 OWNER 权限，适配器须复核真实来源、注册状态及业务提交条件。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface IConfigCommandFields {
    /**
     * 声明所属配置管理器。
     * @return 已注册管理器名称
     */
    String managerName();

    /**
     * 声明可发现的业务字段，不得覆盖 DTO 的真实字段。
     * @return 字段名及中文说明
     */
    Map<String, String> fields();

    /**
     * 从业务权威来源读取补充说明，不初始化默认数据。
     * @param command 含实际来源的配置命令
     * @return 未注册时为空文本，否则为当前字段值
     */
    CompletionStage<String> read(CommandData<ITeaNekoMessageData> command);

    /**
     * 等待独立业务事务及必要同步，反馈必须反映真实提交结果。
     * @param command 实际来源及借用父控制
     * @param field 已声明字段
     * @param value 用户提交的值
     * @return 可交付给原调用者的业务反馈
     */
    CompletionStage<String> set(CommandData<ITeaNekoMessageData> command, String field, String value);
}
