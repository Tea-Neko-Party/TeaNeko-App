package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.integration.web.AgentFileSettings;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;

/**
 * 全局备用视觉模型的局部文件编辑声明，秘密沿用保留及替换协议。
 * <br>2026/11/10：只开放模型参数，不向宿主配置增加插件字段。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Component
@TeaNekoFileSettings("agent.sticker-model")
@PluginOwned("agent.expression")
public final class StickerModelFileSettings extends AgentFileSettings.Base {
    private final StickerModelConfiguration models;

    /**
     * @param files 原文件入口
     * @param models 模型参数校验
     */
    public StickerModelFileSettings(IFileConfigService files, StickerModelConfiguration models) {
        super(files, StickerModelFileConfig.class, "表情包全局备用模型", List.of(
                field("source", "备用模型来源", "TEXT"), field("route", "已记录的模型路由", "TEXT"),
                field("configuration/adapter", "协议适配器", "TEXT"),
                field("configuration/model", "模型名称", "TEXT"),
                field("configuration/base-url", "服务地址", "TEXT"),
                field("configuration/api-path", "接口路径", "TEXT"),
                new FileSettings.Rule("/fallback/configuration/api-key", "API 密钥", "保留原值或明确替换，不回显。", "TEXT", true),
                field("configuration/max-tokens", "最大输出 token", "NUMBER"),
                field("configuration/context-window-tokens", "上下文容量", "NUMBER"),
                field("configuration/context-input-ratio", "输入上下文占比", "NUMBER"),
                field("configuration/temperature", "温度", "NUMBER"),
                field("configuration/thinking", "思考模式", "BOOLEAN"),
                field("configuration/image-input", "图片输入声明", "BOOLEAN"),
                field("configuration/metadata", "扩展参数", "JSON")));
        this.models = models;
    }

    /**
     * @param path 模型字段
     * @param label 中文名称
     * @param kind 编辑类型
     * @return 精确编辑规则
     */
    private static FileSettings.Rule field(String path, String label, String kind) {
        return new FileSettings.Rule("/fallback/" + path, label, "用于表情包主模型不可用时的唯一一次备用请求。", kind, false);
    }

    /** @param candidate 待发布的受保护配置 */
    @Override public void validate(IFileConfigData candidate) {
        models.validate(((StickerModelFileConfig) candidate).getFallback(), "none");
    }
}
