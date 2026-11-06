package org.zexnocs.teanekoagent.database.easydata;

import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.ToIntFunction;

/**
 * 专用数据域的循环维护页，进度和变更同事务提交，倒序轮转不被持续新增数据拖住。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/06：维护游标迁入可清理表，丢失后可重建且不改变业务水位。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.8.5-l
 */
public final class EasyDataMaintenancePage {
    /** 禁止实例化无状态维护入口。 */
    private EasyDataMaintenancePage() { }
    /**
     * 每次至多读取八行；处理器必须按业务锁顺序重新锁定待改行。
     * @param data 已固定业务域
     * @param name 固定任务说明
     * @param action 本页事务内处理
     * @return 实际删除或精简数量
     */
    public static CompletionStage<Integer> run(TeaNekoAgentEasyData data, String name, ToIntFunction<List<EasyDataRow>> action) {
        return data.transaction(name, () -> {
            // 2026/11/06：扫描游标可重建，移入可清理表；旧游标在同一事务迁移。
            var progress = data.cleanable().get("maintenance:" + data.getEntityClass().getSimpleName()).atomic();
            var cursor = progress.find("cursor", EasyDataLock.WRITE);
            var legacy = data.get("maintenance").atomic();
            var previous = legacy.find("cursor", EasyDataLock.WRITE);
            if (previous != null) {
                if (cursor == null) {
                    cursor = progress.create("cursor", previous.getValue());
                }
                legacy.delete(previous);
            }
            long before = cursor == null ? Long.MAX_VALUE : Long.parseLong(cursor.getValue());
            var rows = legacy.recentNamespace(before, 8); int changed = action.applyAsInt(rows);
            String next = Long.toString(rows.size() < 8 ? Long.MAX_VALUE : rows.getLast().getId());
            if (cursor == null) { cursor = progress.create("cursor", next); } else { cursor.setValue(next); }
            progress.save(cursor); return changed;
        });
    }
}
