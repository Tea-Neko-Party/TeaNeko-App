package org.zexnocs.teanekocore.file_config;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 配置占位符无法解析的安全诊断，只保存合法变量名，不持有原模板、值或异常链。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
public final class FileConfigEnvironmentException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 将解析器的缺失状态转换为可公开说明；复杂或非法名称仅使用通用提示。
     * @param variable 直接占位符的变量名，复杂模板时为空
     * @param missing 当前属性来源中确实不存在该变量
     */
    public FileConfigEnvironmentException(@Nullable String variable, boolean missing) {
        super(explanation(variable, missing));
    }

    /**
     * 对变量标识执行有界白名单检查，不从失败的模板中摘取任意内容。
     * @param variable 可选变量名
     * @param missing 是否确认缺少变量
     * @return 固定原因及可选安全变量名
     */
    private static String explanation(@Nullable String variable, boolean missing) {
        if (variable != null && variable.matches("[A-Za-z_][A-Za-z0-9_]{0,127}") && missing) {
            return "当前后端进程未读取到环境变量 " + variable
                    + "。请在后端运行配置中设置它；若刚修改系统环境变量，请重启 IDE 和后端。";
        }
        return "配置中的环境占位符无法解析。请检查必需变量及循环引用，原值不会显示。";
    }

    /** @return 仅含安全变量名和固定处理建议的原因 */
    @Override
    public String failureReason() {
        return getMessage();
    }
}
