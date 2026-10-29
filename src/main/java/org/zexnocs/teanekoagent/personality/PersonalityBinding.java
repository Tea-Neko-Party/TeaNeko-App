package org.zexnocs.teanekoagent.personality;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.agent.AgentScope;

import java.util.Objects;

/**
 * 作用域的人格持久化绑定，独立于记忆空间；revision 变化要求短期会话换代。
 * @param scope 真实作用域
 * @param baseFingerprint 基础内容标识
 * @param correctionKey 当前补正空间
 * @param revision 绑定版本
 * @param backupKey 最近基础人格切换保存的不可变备份，首次初始化时为空
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record PersonalityBinding(AgentScope scope, String baseFingerprint, String correctionKey, long revision, @Nullable String backupKey) {
    /**
     * 拒绝错误定位和非法版本。
     * @param scope 作用域
     * @param baseFingerprint 基础内容标识
     * @param correctionKey 补正定位
     * @param revision 绑定版本
     * @param backupKey 最近切换的备份 key
     * @throws IllegalArgumentException 当版本或定位无效时
     */
    public PersonalityBinding {
        Objects.requireNonNull(scope, "人格作用域不能为空。");
        if (baseFingerprint == null || !baseFingerprint.matches("[a-f0-9]{64}") || correctionKey == null || correctionKey.isBlank()
                || correctionKey.length() > 256 || revision < 1) { throw new IllegalArgumentException("人格绑定定位或版本无效。"); }
    }
    /**
     * 应用开关进入上下文身份，补正内容 revision 不改变同轮已固定快照。
     * @param correctionsEnabled 当前补正应用开关
     * @return 会话/重放复核的固定身份
     */
    public String identity(boolean correctionsEnabled) {
        return PersonalityKeys.digest(baseFingerprint, correctionKey, Long.toString(revision), Boolean.toString(correctionsEnabled));
    }
}
