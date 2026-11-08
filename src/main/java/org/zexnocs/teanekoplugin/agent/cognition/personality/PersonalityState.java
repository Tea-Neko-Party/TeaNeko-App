package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.util.List;

/**
 * 有版本且有容量上限的补正集合；写入依赖存储事务的行锁和 Version，不进行无保护覆盖。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 * @param key 补正或备份定位
 * @param revision 集合版本
 * @param corrections 有界来源记录
 * @param backup 是否为不可变备份
 * @param managementRevision 管理换代，普通候选追加保持不变
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record PersonalityState(String key, long revision, List<PersonalityCorrection> corrections, boolean backup, long managementRevision) {
    /**
     * 冻结集合并验证幂等键唯一，备份保留原始证据而非共享可变列表。
     * @param key 逻辑定位
     * @param revision 非负版本
     * @param corrections 来源记录
     * @param backup 备份标记
     * @param managementRevision 管理换代
     * @throws IllegalArgumentException 当容量、定位或版本无效时
     */
    public PersonalityState {
        corrections = List.copyOf(corrections);
        if (key == null || key.isBlank() || key.length() > 512 || managementRevision < 0 || managementRevision > revision || corrections.size() > 64
                || corrections.stream().map(PersonalityCorrection::requestId).distinct().count() != corrections.size()) {
            throw new IllegalArgumentException("人格补正集合定位、容量或版本无效。");
        }
    }
    /**
     * 新建普通集合时使用零管理代次，保留既有构造方式。
     * @param key 逻辑定位
     * @param revision 内容版本
     * @param corrections 来源记录
     * @param backup 是否只读
     */
    public PersonalityState(String key, long revision, List<PersonalityCorrection> corrections, boolean backup) {
        this(key, revision, corrections, backup, 0);
    }
}
