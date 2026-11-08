package org.zexnocs.teanekoplugin.general.image_search.saucenao;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import lombok.Builder;
import lombok.Data;
import org.zexnocs.teanekocore.api_response.api.APIRequestData;
import org.zexnocs.teanekocore.api_response.api.APIRequestParam;
import org.zexnocs.teanekocore.api_response.api.IAPIRequestData;

/**
 * SauceNAO 请求数据类
 *
 * @see <a href="https://saucenao.com/user.php?page=searchapi">SauceNAO</a>
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.0.14
 * @version 4.9.0-l
 */
@Data
@Builder
@APIRequestData(
        baseUrl = "https://saucenao.com",
        path = "/search.php")
@PluginOwned("general.image-search")
public class SauceNAORequestData implements IAPIRequestData {
    /**
     * 图片的 URL 数据。
     * 务必设置该参数。
     */
    @APIRequestParam("url")
    private String url;

    /**
     * 网站的 API 密钥。
     */
    @APIRequestParam("api_key")
    private String apiKey;

    /**
     * 启用特定网站的搜索。
     */
    @Builder.Default
    @APIRequestParam("dbmask")
    private String dbmask = "251662880";

    /**
     * 返回的结果数量。默认值为 6。
     */
    @Builder.Default
    @APIRequestParam("numres")
    private int numres = 6;

    /**
     * 请固定使用 2。标识使用 json 格式返回结果。
     */
    @Builder.Default
    @APIRequestParam("output_type")
    private final int outputType = 2;

    /**
     * 预览限制级。0-3
     * 0 表示不限制，3 表示只返回安全内容。
     */
    @Builder.Default
    @APIRequestParam("hide")
    private int hide = 3;
}
