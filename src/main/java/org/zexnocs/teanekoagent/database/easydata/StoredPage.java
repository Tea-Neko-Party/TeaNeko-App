package org.zexnocs.teanekoagent.database.easydata;

import java.util.List;

/**
 * 持久目录的有界页；空游标表示已到末页，不把当前页当作完整目录。
 * <br>2026/11/06：供空间与主体选择复用，游标仅是定位，不代表授权。
 *
 * @param items 本页项目
 * @param next 下一页游标，结束时为空
 * @param <T> 明确的目录项类型
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
public record StoredPage<T>(List<T> items, String next) {
    /**
     * 冻结结果，防止异步返回后被调用方改写。
     * @param items 本页结果
     * @param next 后继游标
     */
    public StoredPage {
        items = List.copyOf(items);
        java.util.Objects.requireNonNull(next);
    }
}
