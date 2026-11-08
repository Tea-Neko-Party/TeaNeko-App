package org.zexnocs.teanekoplugin.agent.expression.integration.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionRuntime;
import org.zexnocs.teanekoplugin.agent.expression.expression.file_config.AgentExpressionFileConfig;
import org.zexnocs.teanekoplugin.agent.expression.expression.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoplugin.agent.expression.sticker.StickerLibrary;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoapp.web.files.FileSettings;
import org.zexnocs.teanekoapp.web.files.TeaNekoFileSettings;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Objects;
import org.zexnocs.teanekoagent.integration.web.AgentFileSettings.Base;
import static org.zexnocs.teanekoagent.integration.web.AgentFileSettings.field;

/**
 * 表达插件的本地配置贡献，停用后由统一目录隐藏。
 * <br>2026/11/07：按真实插件归属拆分原文件设置。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class ExpressionFileSettings {
    /** 禁止实例化配置贡献命名空间。 */
    private ExpressionFileSettings() {}

    /**
     * 拟人分段和节奏设置，由实际表达编译端口确认。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    @Component
    @PluginOwned("agent.expression")
    @TeaNekoFileSettings("agent.expression")
    public static final class Expression extends Base {
        private final ExpressionRuntime runtime;
        /**
         * @param files 原文件入口
         * @param runtime 原表达消费端口
         */
        public Expression(IFileConfigService files, ExpressionRuntime runtime) {
            super(files, AgentExpressionFileConfig.class, "拟人分段与发送节奏", List.of(
                    field("/enabled", "启用表达协议", "BOOLEAN"), field("/max-messages", "每轮最多发送条数", "NUMBER"),
                    field("/max-step-delay", "每段最大等待毫秒", "NUMBER"), field("/max-total-delay", "总等待上限毫秒", "NUMBER"),
                    field("/default-delay", "默认间隔毫秒", "NUMBER"), field("/timeout-seconds", "发送总超时秒", "NUMBER"),
                    field("/max-queued", "排队轮数上限", "NUMBER")));
            this.runtime = runtime;
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param candidate 原候选
         */
        @Override
        public void validate(IFileConfigData candidate) {
            ((AgentExpressionFileConfig) candidate).snapshot();
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param published 原候选
         * @return 原表达运行时读取结果
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            if (!runtime.settings().equals(((AgentExpressionFileConfig) published).snapshot())) {
                throw new IllegalStateException("表达编译端口没有读取到新设置。");
            }
            return "表达运行时已确认新设置，后续编译与准入使用新值；在途发送遵循原计划及守卫。";
        }
    }

    /**
     * 图库安全额度字段，后端身份和存储目录仍由本地管理。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    @Component
    @PluginOwned("agent.expression")
    @TeaNekoFileSettings("agent.stickers")
    public static final class Stickers extends Base {
        private final StickerLibrary library;
        /**
         * 2026/11/07：提供自动解析模式及两套概率字段说明。
         * @param files 原文件入口
         * @param library 原图库端口
         */
        public Stickers(IFileConfigService files, StickerLibrary library) {
            // 2026/11/07：两种概率模式及参数都可编辑，实际模型调用只读取所选模式。
            super(files, AgentStickerFileConfig.class, "表情包采集与容量", List.of(
                    field("/enabled", "启用图库", "BOOLEAN"), field("/collect-enabled", "启用采集", "BOOLEAN"),
                    field("/passive-enabled", "自动采集", "BOOLEAN"), field("/max-active", "有效表情包上限", "NUMBER"),
                    new FileSettings.Rule("/analysis-probability-mode", "自动解析模式", "DYNAMIC：随有效素材数量降低；STATIC：固定概率。显式收藏及重新分析不抽样。", "TEXT", false),
                    new FileSettings.Rule("/analysis-probability-min", "动态最低概率", "0 至 1，默认 0.10（10%）。", "NUMBER", false),
                    new FileSettings.Rule("/analysis-probability-max", "动态最高概率", "0 至 1，不低于最低概率；默认 1（100%）。", "NUMBER", false),
                    new FileSettings.Rule("/analysis-probability-decay", "动态变化率", "有限正数，默认 0.01。p=min+(max-min)×exp(-(变化率×有效数量)²)。默认 100 张时约 43%。", "NUMBER", false),
                    new FileSettings.Rule("/analysis-probability-static", "静态解析概率", "0 至 1，默认 0.20（20%）；仅 STATIC 模式使用。", "NUMBER", false),
                    field("/max-per-category", "每类上限", "NUMBER"), field("/max-pending", "待处理上限", "NUMBER"),
                    field("/max-storage-bytes", "磁盘预算字节", "NUMBER"), field("/max-pending-bytes", "待处理预算字节", "NUMBER"),
                    field("/max-file-bytes", "单文件字节上限", "NUMBER"), field("/downloads-per-hour", "每小时下载数", "NUMBER"),
                    field("/scope-downloads-per-hour", "单聊天每小时下载数", "NUMBER"), field("/analyses-per-hour", "每小时分析数", "NUMBER"),
                    field("/scope-analyses-per-hour", "单聊天每小时分析数", "NUMBER"), field("/timeout-seconds", "分析超时秒", "NUMBER"),
                    field("/min-confidence", "最低表情置信度", "NUMBER"), field("/min-quality", "最低质量", "NUMBER"),
                    field("/probation-days", "保护天数", "NUMBER"), field("/replacement-margin", "替换分差", "NUMBER"),
                    field("/allowed-hosts", "允许来源主机", "JSON"), field("/categories", "类别描述", "JSON"),
                    field("/score-weights", "排序权重", "JSON")));
            this.library = library;
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param candidate 原候选
         */
        @Override
        public void validate(IFileConfigData candidate) {
            ((AgentStickerFileConfig) candidate).snapshot();
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param candidate 原候选
         * @return 固定后端或定时器改变时要求重启，普通容量调整可后续使用
         */
        @Override
        public String applicationFor(IFileConfigData candidate) {
            var old = files.get(AgentStickerFileConfig.class);
            var next = (AgentStickerFileConfig) candidate;
            return Objects.equals(old.getStorageRoot(), next.getStorageRoot()) && Objects.equals(old.getCatalogId(), next.getCatalogId())
                    && Objects.equals(old.getAssets(), next.getAssets()) && Objects.equals(old.getCatalog(), next.getCatalog())
                    && old.getMaintenanceSeconds() == next.getMaintenanceSeconds() ? "NEXT_USE" : "RESTART";
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param published 原候选
         * @return 原图库读取确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            super.verifyApplied(published);
            if (!library.settings().equals(((AgentStickerFileConfig) published).snapshot())) {
                throw new IllegalStateException("图库没有读取到新设置。");
            }
            return "图库已确认新额度与类别，后续工作使用新快照；当前文件和作业不因降低额度而立即删除。";
        }
    }

    /**
     * 媒体选图和客户端表情规则，原调用点逐次读取快照。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.9.0-l
     */
    @Component
    @PluginOwned("agent.expression")
    @TeaNekoFileSettings("agent.media")
    public static final class Media extends Base {
        /** @param files 原文件入口 */
        public Media(IFileConfigService files) {
            super(files, AgentMediaReplyFileConfig.class, "选图与客户端表情", List.of(field("/enabled", "启用媒体表达", "BOOLEAN"),
                    field("/max-per-reply", "每轮表情包上限", "NUMBER"), field("/max-categories", "类别选择上限", "NUMBER"),
                    field("/cooldown-turns", "表情包冷却轮数", "NUMBER"), field("/max-category-characters", "类别说明字符上限", "NUMBER"),
                    field("/native-faces", "客户端表情映射", "JSON")));
        }
        /**
         * 2026/11/05：用显式类型转换及类型校验替代静态反射 cast，保留原模块语义检查。
         * @param candidate 原候选
         */
        @Override
        public void validate(IFileConfigData candidate) {
            ((AgentMediaReplyFileConfig) candidate).snapshot();
        }
    }
}
