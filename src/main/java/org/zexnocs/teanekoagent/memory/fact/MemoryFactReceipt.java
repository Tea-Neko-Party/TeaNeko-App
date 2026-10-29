package org.zexnocs.teanekoagent.memory.fact;

import org.jspecify.annotations.Nullable;
import java.util.Objects;

/**
 * 提交后才公开的追加结果，重复不覆盖原记录或遗失新增来源审计。
 * @param status 已存储、完全重复或操作冲突
 * @param record 已接受的原记录，冲突时为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactReceipt(Status status, @Nullable MemoryFactRecord record) {
    /**
     * 保持结果与记录一致，不能把无记录声明为追加成功。
     * @param status 结果类别
     * @param record 已接受记录
     * @throws NullPointerException 当状态为空时
     * @throws IllegalArgumentException 当状态和记录不一致时
     */
    public MemoryFactReceipt {
        Objects.requireNonNull(status, "事实追加状态不能为空。");
        if ((status == Status.CONFLICT) != (record == null)) { throw new IllegalArgumentException("事实追加状态与记录不一致。"); }
    }

    /**
     * 追加结果类别，基础设施失败或旧代次通过异常阶段表达。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Status { STORED, DUPLICATE, CONFLICT }
}
