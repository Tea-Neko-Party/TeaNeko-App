package org.zexnocs.teanekocore.database.configdata;

import java.io.Serial;

/**
 * 当前配置已换代，调用者必须重新读取，不能自动覆盖其他写入。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public final class ConfigEditConflictException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 构造不包含配置正文的冲突说明。 */
    public ConfigEditConflictException() {
        super("配置已被其他操作修改，请刷新后重新确认。");
    }
}
