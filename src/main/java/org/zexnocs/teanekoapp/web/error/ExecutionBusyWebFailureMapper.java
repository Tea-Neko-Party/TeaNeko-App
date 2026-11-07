package org.zexnocs.teanekoapp.web.error;

import org.springframework.http.ResponseEntity;
import org.zexnocs.teanekocore.actuator.execution.ExecutionBusyException;

import java.util.Map;

/**
 * 容量拒绝使用明确的可恢复 HTTP 状态，避免误报配置不可用。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
@WebFailureMapping(type = ExecutionBusyException.class)
public final class ExecutionBusyWebFailureMapper implements IWebFailureMapper {
    /**
     * @param failure 已分类容量失败
 * @return 不包含请求内容的繁忙响应
     */
    @Override
    public ResponseEntity<Map<String, String>> map(Throwable failure) {
        return ResponseEntity.status(429).body(Map.of("code", "EXECUTION_BUSY",
            "message", "执行容量已满，请稍后重试；已有操作请保留原编号查询。"));
    }
}
