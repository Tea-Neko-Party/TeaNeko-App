package org.zexnocs.teanekoagent.memory.profile;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallLimits;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallRequest;

import java.util.Objects;
import java.util.UUID;

/**
 * 可信宿主明确指定的画像主体及空间；用途和预算只控制读取，不授予管理或学习权限。
 * @param reference 本次固定空间引用
 * @param subjectId 画像主体的 TeaNeko UUID，与调用操作者独立
 * @param use 用于选择本地默认条数的用途
 * @param limits 可选单次预算，受 Recall 本地硬上限约束
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record UserProfileRequest(MemoryNamespaceReference reference, UUID subjectId,
        MemoryRecallRequest.Use use, @Nullable MemoryRecallLimits limits) {
    /**
     * 固定主语和引用，不从平台字符串推断 UUID 或从来源身份推断画像主体。
     * @param reference 已授权固定引用
     * @param subjectId 真实画像主体
     * @param use 用途
     * @param limits 可选预算
     * @throws NullPointerException 当必需数据为空时
     */
    public UserProfileRequest {
        Objects.requireNonNull(reference, "画像空间引用不能为空。");
        Objects.requireNonNull(subjectId, "画像主体 UUID 不能为空。");
        Objects.requireNonNull(use, "画像读取用途不能为空。");
    }
}
