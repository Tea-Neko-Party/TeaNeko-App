package org.zexnocs.teanekoagent.memory.access;

/**
 * 独立的记忆操作开关；学习开关不授予普通对话提取个人事实或人格管理的权限。
 * @param enabled 作用域已真实登记且启用
 * @param readEnabled 允许事实、画像读取及后续注入
 * @param addEnabled 允许用户明确请求新增自己的事实
 * @param learningEnabled 独立学习策略开关，当前没有自动学习执行器
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryAccessPolicy(boolean enabled, boolean readEnabled, boolean addEnabled, boolean learningEnabled) {}
