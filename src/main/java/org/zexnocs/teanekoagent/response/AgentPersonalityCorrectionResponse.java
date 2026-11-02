package org.zexnocs.teanekoagent.response;
/**
 * 表达候选的持久化观察结果，不把重复提交当作新增。
 * @param status 真实操作状态
 * @param added 本次是否新增
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
public record AgentPersonalityCorrectionResponse(String status, boolean added) { }
