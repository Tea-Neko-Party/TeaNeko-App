package org.zexnocs.teanekoplugin.agent.expression.sticker;

/**
 * 素材采集的实时资格，区分明确撤销与客户端暂时未就绪。
 * <br>2026/11/10：后台队列按资格等待或终止，不将启动时未连接误报为处理故障。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public enum StickerAccess {
    ALLOWED("允许采集。"),
    COLLECTION_DISABLED("图库或素材采集已关闭。"),
    PASSIVE_DISABLED("被动素材采集已关闭。"),
    SOURCE_DISABLED("来源聊天未启用表情包组件或素材采集。"),
    CLIENT_UNAVAILABLE("来源客户端尚未就绪或当前账号与原作业不匹配。"),
    CONFIG_CHANGED("图库配置已变化，本次作业停止以防止使用旧配置产生副作用。");

    private final String description;

    /** @param description 不含图片地址、凭据或聊天内容的固定说明 */
    StickerAccess(String description) {
        this.description = description;
    }

    /** @return 可以写入日志的固定资格说明 */
    public String description() {
        return description;
    }
}
