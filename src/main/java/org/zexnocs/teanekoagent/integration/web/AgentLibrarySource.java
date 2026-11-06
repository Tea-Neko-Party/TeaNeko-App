package org.zexnocs.teanekoagent.integration.web;

import org.zexnocs.teanekoagent.database.easydata.StoredPage;
import org.zexnocs.teanekoapp.teauser.TeaUserDirectory;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 独立持久目录扩展点，Spring 实现发布自己拥有的空间或主体目录。
 * <br>2026/11/06：查询有界，发现目录不授予对应写能力。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
public interface AgentLibrarySource {
    /** @return 唯一目录标识。 */
    String id();
    /**
     * @param context 原管理能力
     * @param query 原分页与空间
     * @return 实际已持久目录页
     */
    CompletionStage<StoredPage<Item>> page(ManagementContext context, Query query);

    /**
     * 明确的有界查询，不接受任意类名或存储路径。
     * @param kind 已注册目录
     * @param after 原游标
     * @param namespace 画像所属空间，其他目录为空
     * @param generation 固定空间代次，首次为 -1
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    record Query(String kind, String after, String namespace, long generation) {
        /**
         * @param kind 目录
         * @param after 游标
         * @param namespace 空间
         * @param generation 代次
         */
        public Query {
            if (kind == null || kind.length() > 64 || after == null || after.length() > 256
                    || namespace == null || namespace.length() > 128 || generation < -1) {
                throw new IllegalArgumentException("持久目录查询参数无效。");
            }
        }
    }

    /**
     * 目录卡片只含选择所需元数据，不把完整人格或画像正文放入列表。
     * @param id 逻辑定位
     * @param name 显示名称
     * @param detail 简短说明
     * @param generation 当前代次，不适用时为 -1
     * @param revision 当前版本
     * @param readOnly 是否为只读备份
     * @param mappings 已有客户端映射
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.4-l
     * @version 4.8.4-l
     */
    record Item(String id, String name, String detail, long generation, long revision,
                boolean readOnly, List<TeaUserDirectory.Mapping> mappings) {}
}
