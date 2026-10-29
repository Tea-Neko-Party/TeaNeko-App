package org.zexnocs.teanekoagent.memory.profile;

import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallEvidence;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 从当前空间内已接受证据重建的不可变用户画像，不单独持久化一份推断结论。
 * 投影规则版本及各证据的 ID/revision 用于追溯，不冒充全空间的单调版本。
 * @param reference 本次读取的固定引用
 * @param subjectId 唯一画像主体
 * @param projectionVersion 构建规则版本
 * @param observedAt 本次过滤有效期的固定时刻
 * @param evidence 已排序的事实和偏好证据，包含完整来源与时间
 * @param scanned 检查的原候选数，包含其他主体及过期记录
 * @param matched 检查范围内该主体有效证据的命中数
 * @param limits 扫描或输出未覆盖全部的原因
 * @param characters 紧凑证据数组字符数，不含画像外层信封
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record UserProfileView(MemoryNamespaceReference reference, UUID subjectId, int projectionVersion, Instant observedAt,
        List<MemoryRecallEvidence> evidence, int scanned, int matched, Set<MemoryRecallResult.Limit> limits, int characters) {
    public static final int PROJECTION_VERSION = 1;

    /**
     * 复用证据范围和统计合同，并拒绝跨主体、过期或不受支持的画像内容。
     * @param reference 固定引用
     * @param subjectId 主体
     * @param projectionVersion 构建规则版本
     * @param observedAt 过滤时刻
     * @param evidence 有效证据
     * @param scanned 原检查数
     * @param matched 本主体命中数
     * @param limits 覆盖限制
     * @param characters 原证据数组长度
     * @throws NullPointerException 当必需数据为空时
     * @throws IllegalArgumentException 当版本、统计、定位或内容无效时
     */
    public UserProfileView {
        Objects.requireNonNull(subjectId, "画像主体不能为空。");
        if (projectionVersion != PROJECTION_VERSION) { throw new IllegalArgumentException("画像构建规则版本不受支持。"); }
        var checked = new MemoryRecallResult(reference, observedAt, evidence, scanned, matched, limits, characters);
        evidence = checked.evidence(); limits = checked.limits();
        for (var item : evidence) {
            var value = item.value(); var expiry = value.expiresAt();
            if (!item.subjectId().equals(subjectId) || expiry != null && !expiry.isAfter(observedAt)
                    || value.kind() != MemoryFactValue.Kind.FACT && value.kind() != MemoryFactValue.Kind.PREFERENCE) {
                throw new IllegalArgumentException("画像证据的主体、有效期或类型不符合要求。");
            }
        }
    }

    /**
     * 从一次明确主体查询重建，不合并多次读取，也不在坏来源上静默过滤后宣称完整。
     * @param request 本次固定请求
     * @param result 原 Recall 结果
     * @return 保留全部覆盖和追溯信息的画像
     * @throws NullPointerException 当请求或结果为空时
     * @throws IllegalArgumentException 当原引用或显式预算不一致时
     */
    public static UserProfileView from(UserProfileRequest request, MemoryRecallResult result) {
        Objects.requireNonNull(request, "画像请求不能为空。"); Objects.requireNonNull(result, "画像证据结果不能为空。");
        var budget = request.limits();
        if (!request.reference().equals(result.reference()) || budget != null && (result.evidence().size() > budget.maxResults()
                || result.characters() > budget.maxCharacters() || result.scanned() > budget.maxScanned())) {
            throw new IllegalArgumentException("画像来源与固定引用或显式预算不一致。");
        }
        return new UserProfileView(result.reference(), request.subjectId(), PROJECTION_VERSION, result.observedAt(), result.evidence(),
                result.scanned(), result.matched(), result.limits(), result.characters());
    }

    /**
     * 按原证据顺序提供事实部分，不将同标签或相互矛盾的文本改写为单一结论。
     * @return 不可变事实证据列表
     */
    public List<MemoryRecallEvidence> facts() { return evidence.stream().filter(item -> item.value().kind() == MemoryFactValue.Kind.FACT).toList(); }

    /**
     * 按原证据顺序提供偏好部分，不从对话、人格补正或其他主体推断个人偏好。
     * @return 不可变偏好证据列表
     */
    public List<MemoryRecallEvidence> preferences() { return evidence.stream().filter(item -> item.value().kind() == MemoryFactValue.Kind.PREFERENCE).toList(); }

    /**
     * 判断本读取边界的证据是否完整，不保证随后没有追加、过期或 clear。
     * @return 没有扫描或输出限制时为真
     */
    public boolean isComplete() { return limits.isEmpty(); }

    /**
     * 区分完整空画像和未检查全部、条数或字符不足造成的空画像。
     * @return 本读取边界已完整确认该主体没有有效事实或偏好时为真
     */
    public boolean definitivelyEmpty() { return isComplete() && evidence.isEmpty(); }
}
