package org.zexnocs.teanekocore.command.api;

/**
 * 命令的可信宿主来源，不由命令文本或参数决定。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
public enum CommandOrigin {
    NATIVE, MANAGEMENT
}
