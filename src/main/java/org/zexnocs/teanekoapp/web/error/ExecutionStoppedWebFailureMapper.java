package org.zexnocs.teanekoapp.web.error;

import org.springframework.http.ResponseEntity;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;

import java.util.Map;

/**
 * 等待截止不能被解释为业务写入回滚，公开响应保留查询原编号的恢复方式。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
@WebFailureMapping(type = ExecutionStoppedException.class)
public final class ExecutionStoppedWebFailureMapper implements IWebFailureMapper {
    /**
     * @param failure 执行控制终止
 * @return 结果尚未知的固定安全响应
     */
    @Override
    public ResponseEntity<Map<String, String>> map(Throwable failure) {
        return ResponseEntity.status(504).body(Map.of("code", "EXECUTION_WAIT_ENDED",
            "message", "等待已截止或执行已关闭，已开始的操作可能仍会完成。请保留原操作编号查询，不要换号重发。"));
    }
}
