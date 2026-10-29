package org.zexnocs.teanekoagent.memory.fact;

import java.io.Serial;

/**
 * 区分空间缺失与数据代次失效，不把旧引用当作确定没有相关记忆。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class MemoryFactGenerationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final boolean missingNamespace;
    /**
     * 构造固定中文原因，不保留存储正文或异常链。
     * @param missingNamespace 空间缺失为 true，数据代次失效为 false
     */
    public MemoryFactGenerationException(boolean missingNamespace) {
        super(missingNamespace ? "事实所属记忆空间尚未登记。" : "事实操作的记忆空间数据代次已失效。");
        this.missingNamespace = missingNamespace;
    }
    /**
     * 提供类型化区别，不解析异常文本。
     * @return 空间尚未登记时为 true
     */
    public boolean missingNamespace() { return missingNamespace; }
}
