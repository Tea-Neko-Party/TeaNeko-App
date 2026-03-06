package org.zexnocs.teanekoplugin.general.saucenao;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Mask;

/**
 * SauceNAO 规则配置数据。
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
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
