package org.zexnocs.teanekoagent.personality;

import java.util.List;

/**
 * 有版本且有容量上限的补正集合；写入依赖存储事务的行锁和 Version，不进行无保护覆盖。
 * @param key 补正或备份定位
 * @param revision 集合版本
 * @param corrections 有界来源记录
 * @param backup 是否为不可变备份
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record PersonalityState(String key, long revision, List<PersonalityCorrection> corrections, boolean backup) {
    /**
     * 冻结集合并验证幂等键唯一，备份保留原始证据而非共享可变列表。
     * @param key 逻辑定位
     * @param revision 非负版本
     * @param corrections 来源记录
     * @param backup 备份标记
     * @throws IllegalArgumentException 当容量、定位或版本无效时
     */
    public PersonalityState {
        corrections = List.copyOf(corrections);
        if (key == null || key.isBlank() || key.length() > 512 || revision < 0 || corrections.size() > 64
                || corrections.stream().map(PersonalityCorrection::requestId).distinct().count() != corrections.size()) {
            throw new IllegalArgumentException("人格补正集合定位、容量或版本无效。");
        }
    }
}
