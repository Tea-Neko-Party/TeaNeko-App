package org.zexnocs.teanekoagent.agent.personality;

import org.zexnocs.teanekoagent.agent.AgentHandle;
import org.zexnocs.teanekoagent.agent.AgentRunRequest;
import org.zexnocs.teanekoagent.agent.AgentRunResult;
import java.util.function.BooleanSupplier;

/**
 * 接收邮箱已提交的真实互动；后台候选不改变前台结果或拥有邮箱资源。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public interface IAgentPersonalityLearning {
    /**
     * 在邮箱锁外交接一次成功互动，重放不再次交接。
     * @param handle 原实例拥有者
     * @param request 宿主原始请求及用户原文
     * @param result 已接受的真实结果和人格依据
     * @param current 只读代次检查，允许后台在锁外调用，不执行外部 I/O
     */
    void offer(AgentHandle handle, AgentRunRequest request, AgentRunResult result, BooleanSupplier current);
}
