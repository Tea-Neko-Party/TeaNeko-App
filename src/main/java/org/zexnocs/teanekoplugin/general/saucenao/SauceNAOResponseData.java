package org.zexnocs.teanekoplugin.general.saucenao;

import lombok.Data;
import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;

import java.util.List;
import java.util.Map;

/**
 * SauceNAO API 响应数据结构
 *
 * @author zExNocs
 * @date 2026/03/05
 * @since 4.0.14
 */
@Data
public class SauceNAOResponseData implements IAPIResponseData {
    /// 搜索头，包含基本信息
    private Header header;

    /// 搜索结果列表
    private List<Result> results;

    /**
     * 搜索头，包含基本信息
     *
     * @author zExNocs
     * @date 2026/03/05
     */
    @Data
    public static class Header {
        // 0 标识成功，小于 0 标识失败
        private int status;

        // 查询图片的临时 hash / ID
        private String query_image;

        // 所有结果的最低相似度
        private String minimum_similarity;

        // 查询的图片数量
        private int results_returned;

        // 今日剩余查询次数
        private int long_remaining;
    }

    /**
     * 搜索结果，包含相似度、缩略图、索引信息和具体数据
     *
     * @author zExNocs
     * @date 2026/03/05
     */
    @Data
    public static class Result {
        // 结果头
        private ResultHeader header;

        // 结果的具体数据。根据不同索引类型，数据结构不同
        // 一般都会有 ext_urls 作为图片链接列表
        private Map<String, Object> data;
    }

    /**
     * 搜索结果头，包含相似度、缩略图、索引信息
     *
     * @author zExNocs
     * @date 2026/03/05
     */
    @Data
    public static class ResultHeader {
        // 相似度
        private String similarity;

        // 缩略图地址
        private String thumbnail;

        // 匹配使用的索引编号
        // 5: Pixiv
        // 9: Danbooru
        // 21: Anime
        // 34: deviantArt
        private int index_id;

        // 索引名称
        private String index_name;
    }
}
