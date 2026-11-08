package org.zexnocs.teanekoplugin.general.image_search.saucenao;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Mask;

/**
 * SauceNAO 规则配置数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 * @version 4.9.0-l
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@PluginOwned("general.image-search")
public class SauceNAORuleConfig implements IConfigData {
    @Mask
    @Description("SauceNAO API Key")
    private String api = "";

    @Description("使用 index 的启用列表。999 标识全部启用。")
    private long enableIndex = 251662848L;

    @Description("是否开启预览。")
    private boolean preview = false;

    @Description("预览限制级。")
    private int hide = 3;

    @Description("隐藏低于该值的匹配度，百分比中整数。")
    private int minSimilarity = 40;

    @Description("搜索的图片的最大数量。")
    private int maxImageCount = 10;
}
