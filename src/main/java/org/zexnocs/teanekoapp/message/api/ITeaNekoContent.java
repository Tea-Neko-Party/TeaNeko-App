package org.zexnocs.teanekoapp.message.api;

/**
 * Tea Neko 消息内容接口。
 *
 * @author zExNocs
 * @date 2026/02/21
 */
public interface ITeaNekoContent {
    /**
     * 转化成原始字符串，用于命令解析。
     *
     * @return 消息内容的原始字符串表示
     */
    String toRawString();
}
