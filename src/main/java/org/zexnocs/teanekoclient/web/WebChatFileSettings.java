package org.zexnocs.teanekoclient.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.IFileSettingsAdapter;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.List;

/**
 * 网页聊天容量通过原文件配置表单热应用，不提供认证根设置或任意路径。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
@TeaNekoFileSettings("web.chat")
public final class WebChatFileSettings implements IFileSettingsAdapter {
    /** @return 当前模块中文名 */
    @Override
    public String name() {
        return "网页聊天容量";
    }

    /** @return 固定原文件类型 */
    @Override
    public Class<? extends IFileConfigData> type() {
        return WebChatSettings.class;
    }

    /** @return 当前明确支持的容量字段 */
    @Override
    public List<FileSettings.Rule> fields() {
        return List.of(new FileSettings.Rule("/max-rooms", "聊天数量上限", "范围 1 至 128，不自动删除已有聊天。", "NUMBER", false),
                new FileSettings.Rule("/max-profiles", "网页档案上限", "范围 1 至 32，不自动删除已有档案。", "NUMBER", false),
                new FileSettings.Rule("/max-messages", "每个聊天的消息窗口", "范围 50 至 2000；后续追加按新窗口裁剪，不删除 Agent 记忆。", "NUMBER", false),
                new FileSettings.Rule("/max-media", "网页图片上限", "范围 1 至 128；超过后拒绝新上传，不删除仍被引用的图片。", "NUMBER", false));
    }

    /** @return 下一次原事务读取新容量 */
    @Override
    public String application() {
        return "NEXT_USE";
    }

    /** @param candidate 原类型配置候选 */
    @Override
    public void validate(IFileConfigData candidate) {
        ((WebChatSettings) candidate).limits();
    }

    /**
     * @param published 原已发布候选
     * @return 不进行外部 IO 的应用确认
     */
    @Override
    public String verifyApplied(IFileConfigData published) {
        ((WebChatSettings) published).limits();
        return "后续网页事务使用新容量；已有聊天、档案和图片不自动删除。";
    }
}
