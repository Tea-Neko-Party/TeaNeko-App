package org.zexnocs.teanekoagent.expression;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * 素材选择与客户端表情的独立预算，不承载下载或模型凭据。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param enabled 总开关
 * @param maxPerReply 单轮表情包数量
 * @param maxCategories 单轮展开类别数
 * @param cooldownTurns 同区域回避轮数
 * @param maxCategoryCharacters 完整类别回执字符预算
 * @param nativeFaces 按客户端配置的原生表情标识及描述
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public record MediaReplySettings(boolean enabled,int maxPerReply,int maxCategories,int cooldownTurns,int maxCategoryCharacters,Map<String,Map<String,String>> nativeFaces){
    /**
     * 校验并深层固定目录，禁止过量 Prompt 或可变配置泄漏。
     * @param enabled 开关
     * @param maxPerReply 单轮上限
     * @param maxCategories 类别上限
     * @param cooldownTurns 回避轮数
     * @param maxCategoryCharacters 结果预算
     * @param nativeFaces 客户端目录
     */
    public MediaReplySettings{
        if(maxPerReply<1||maxPerReply>4||maxCategories<1||maxCategories>2||cooldownTurns<0||cooldownTurns>16||maxCategoryCharacters<1024||maxCategoryCharacters>7800||nativeFaces.size()>32){throw new IllegalArgumentException("媒体回复数量或目录预算无效。");}
        var copy=new TreeMap<String,Map<String,String>>();nativeFaces.forEach((client,faces)->{
            if(client.isBlank()||client.length()>64||faces.size()>64||faces.entrySet().stream().anyMatch(e->!e.getKey().matches("[a-zA-Z0-9_.:-]{1,64}")||e.getValue().isBlank()||e.getValue().length()>80)){throw new IllegalArgumentException("原生表情目录标识或描述无效。");}
            copy.put(client,Collections.unmodifiableMap(new TreeMap<>(faces)));
        });nativeFaces=Collections.unmodifiableMap(copy);
    }
}
