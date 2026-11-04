package org.zexnocs.teanekoclient.web;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

/**
 * 网页客户端的本地容量上限，不保存认证凭据。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：为网页容量设置提供用途、范围与裁剪解释。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.6-l
 */
@Getter
@Setter
@FileConfig(value = "chat", path = "web", type = FileConfigType.YAML)
// 2026/11/04：控制台展示类型及字段注解，不读取运行秘密。
@org.zexnocs.teanekocore.framework.description.Description("网页聊天的房间、档案、消息窗口及图片容量；下调上限不主动删除已有房间或档案。")
public final class WebChatSettings implements IFileConfigData {
    @org.zexnocs.teanekocore.framework.description.Description("最多保存的网页房间数量，范围 1～128。")
    private int maxRooms = 32;
    @org.zexnocs.teanekocore.framework.description.Description("最多保存的网页档案数量，范围 1～32。")
    private int maxProfiles = 16;
    @org.zexnocs.teanekocore.framework.description.Description("每个房间的消息窗口，范围 50～2000；后续追加时裁剪。")
    private int maxMessages = 500;
    @org.zexnocs.teanekocore.framework.description.Description("媒体容量，范围 1～128；满时拒绝上传，不删除已引用图片。")
    private int maxMedia = 64;

    /**
     * @return 校验后的独立容量快照
     * @throws IllegalArgumentException 当本地上限超出可控边界时
     */
    public Limits limits() {
        if (maxRooms < 1 || maxRooms > 128 || maxProfiles < 1 || maxProfiles > 32
                || maxMessages < 50 || maxMessages > 2000 || maxMedia < 1 || maxMedia > 128) {
            throw new IllegalArgumentException("网页聊天容量配置无效。");
        }
        return new Limits(maxRooms, maxProfiles, maxMessages, maxMedia);
    }

    /**
     * 每次事务重新读取容量，不把热加载对象直接交给后台执行。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param rooms 聊天上限
     * @param profiles 档案上限
     * @param messages 每个聊天的消息窗口
     * @param media 全实例媒体上限
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Limits(int rooms, int profiles, int messages, int media) {}
}
