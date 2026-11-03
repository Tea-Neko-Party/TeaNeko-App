package org.zexnocs.teanekoagent.memory.layered;

/**
 * 已登记作用域的分层读取和压缩开关，不从模型文本取得授权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param enabled 真实登记且启用
 * @param read 允许历史及记忆资料读取
 * @param compress 允许采集及后台摘要
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
public record LayeredMemoryPolicy(boolean enabled, boolean read, boolean compress) { }
