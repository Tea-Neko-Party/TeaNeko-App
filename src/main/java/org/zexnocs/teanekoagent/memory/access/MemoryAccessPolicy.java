package org.zexnocs.teanekoagent.memory.access;

/**
 * 独立的记忆操作开关；后台自然学习与普通工具的明确新增、人格管理分别授权。
 * <br>2026/11/03：学习策略已接入持久中期到长期整理，不代替原工具或人格权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param enabled 作用域已真实登记且启用
 * @param readEnabled 允许事实、画像读取及后续注入
 * @param addEnabled 允许用户明确请求新增自己的事实
 * @param learningEnabled 允许可信后台自动整理长期事实，不控制人格观察或授权普通工具写入他人事实
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.6.1-l
 */
public record MemoryAccessPolicy(boolean enabled, boolean readEnabled, boolean addEnabled, boolean learningEnabled) {}
