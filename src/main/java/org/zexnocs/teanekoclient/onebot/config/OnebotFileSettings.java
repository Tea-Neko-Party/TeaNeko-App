package org.zexnocs.teanekoclient.onebot.config;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.IFileSettingsAdapter;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.List;

/**
 * OneBot 固定启动状态和调试身份的受限表单，不把保存解释成连接或运行状态已经切换。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
@TeaNekoFileSettings("onebot.main")
public final class OnebotFileSettings implements IFileSettingsAdapter {
    /** @return 平台配置中文名 */
    @Override
    public String name() {
        return "OneBot 启动与调试";
    }
    /** @return 固定原文件类型 */
    @Override
    public Class<? extends IFileConfigData> type() {
        return OnebotMainFileConfig.class;
    }
    /** @return 仅明确启动与调试字段 */
    @Override
    public List<FileSettings.Rule> fields() {
        return List.of(new FileSettings.Rule("/debugger/debugger-id", "调试用户号", "平台用户号，-1 表示未设置；重启后生效。", "NUMBER", false),
                new FileSettings.Rule("/state/initial-state", "初始运行状态", "使用原 OneBotState 枚举值，重启后生效。", "TEXT", false));
    }
    /** @return 平台启动对象需要重启 */
    @Override
    public String application() {
        return "RESTART";
    }
    /** @param candidate 原类型候选 */
    @Override
    public void validate(IFileConfigData candidate) {
        var file = (OnebotMainFileConfig) candidate;
        if (file.getDebugger() == null || file.getState() == null || file.getState().getInitialState() == null) {
            throw new IllegalArgumentException("OneBot 启动状态或调试设置无效。");
        }
    }
    /**
     * @param published 原候选
     * @return 本模块不提供即时应用
     */
    @Override
    public String verifyApplied(IFileConfigData published) {
        throw new UnsupportedOperationException("OneBot 固定启动设置需要重启。");
    }
}
