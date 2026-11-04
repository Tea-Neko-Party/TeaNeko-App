package org.zexnocs.teanekoclient.agent.response;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。
/**
 * 表达候选的持久化观察结果，不把重复提交当作新增。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param status 真实操作状态
 * @param added 本次是否新增
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
public record AgentPersonalityCorrectionResponse(String status, boolean added) { }
