package org.zexnocs.teanekoapp.web.files;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;

import java.util.List;

/**
 * 运行观测配置的明确热更新合同；采样和日志在后续读取时采用新限制。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@Component
@TeaNekoFileSettings("core.runtime")
public final class RuntimeFileSettingsAdapter implements IFileSettingsAdapter {
    private final FileTypeScanner parsers;

    /** @param parsers 原文件格式目录，字段请求时使用其实际解析器 */
    public RuntimeFileSettingsAdapter(FileTypeScanner parsers) {
        this.parsers = parsers;
    }

    /** @return 面向人的配置名称 */
    @Override
    public String name() {
        return "任务与日志观测";
    }

    /** @return 固定的原运行观测类型 */
    @Override
    public Class<? extends IFileConfigData> type() {
        return RuntimeSettings.class;
    }

    /** @return 原字段与实际范围，不开放任意文件路径 */
    @Override
    public List<FileSettings.Rule> fields() {
        if (!(parsers.get(FileConfigType.YAML) instanceof IFileDocumentParser parser)) {
            throw new IllegalStateException("运行观测文件解析器没有提供文档能力。");
        }
        return GenericFileSettingsAdapter.rules(java.util.Objects.requireNonNull(parser.schema(RuntimeSettings.class),
                "运行观测解析器缺少字段契约。"));
    }

    /** @return 后续观测读取采用新配置 */
    @Override
    public String application() {
        return "NEXT_USE";
    }

    /**
     * @param candidate 原解析器绑定的候选
     */
    @Override
    public void validate(IFileConfigData candidate) {
        ((RuntimeSettings) candidate).snapshot();
    }

    /**
     * @param published 实际发布的同一配置
     * @return 不承诺恢复先前未采集日志的生效说明
     */
    @Override
    public String verifyApplied(IFileConfigData published) {
        validate(published);
        return "运行观测配置已发布，后续采样和日志使用新设置；既有条目仍按窗口规则淘汰。";
    }
}
