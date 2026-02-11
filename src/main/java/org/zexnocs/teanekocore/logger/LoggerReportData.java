package org.zexnocs.teanekocore.logger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 日志报告数据类，包含日志报告的相关数据。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoggerReportData {
    /// 日志报告的命名空间，通常用于区分不同模块或组件的日志。
    private String namespace;

    /// 日志报告的消息内容，描述了日志事件的具体信息。
    private String message;

    /// 日志报告是否包含异常信息。
    @Builder.Default
    private Throwable throwable = null;

    /// 日志的指定报告接收者；如果为 null 则报告给默认监护人。
    @Builder.Default
    private String reportRecipients = null;

    /// 是否启动报告
    @Builder.Default
    private boolean enableReport = true;
}
