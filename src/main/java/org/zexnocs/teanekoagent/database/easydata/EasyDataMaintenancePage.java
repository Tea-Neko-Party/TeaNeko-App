package org.zexnocs.teanekoagent.database.easydata;

import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.ToIntFunction;

/**
 * 专用数据域的循环维护页，进度和变更同事务提交，倒序轮转不被持续新增数据拖住。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
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
            var progress = data.get("maintenance").atomic(); var cursor = progress.find("cursor", EasyDataLock.WRITE);
            long before = cursor == null ? Long.MAX_VALUE : Long.parseLong(cursor.getValue());
            var rows = progress.recentNamespace(before, 8); int changed = action.applyAsInt(rows);
            String next = Long.toString(rows.size() < 8 ? Long.MAX_VALUE : rows.getLast().getId());
            if (cursor == null) { cursor = progress.create("cursor", next); } else { cursor.setValue(next); }
            progress.save(cursor); return changed;
        });
    }
}
