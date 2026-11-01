package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.AgentActorContext;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * 供可信迁移程序和后续 DEBUG 管理使用的显式旧人格预览/导入端口。
 * 普通解析不调用本端口，调用方必须先取得真实管理授权，不向 cfg 或模型注册。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ILegacyPersonalityStore {
    /**
     * 只读一个明确旧行，缺失与损坏分开，不创建默认来源。
     * @param location 明确旧定位
     * @param fallbackTime 缺失记录时间时使用的固定估算
     * @return 有界原文和候选预览
     */
    CompletionStage<Optional<Preview>> preview(Location location, Instant fallbackTime);
    /**
     * 核对源版本、完整摘要、原绑定、来源映射和共同字段后原子导入并保存审计。
     * @param command 可信管理方确认的完整映射
     * @return 持久化完成的导入及跳过回执
     */
    CompletionStage<Result> importSnapshot(Import command);

    /**
     * 旧 memory 域中的人格列表定位，不解释成新记忆 namespace。
     * @param scopeId 原作用域
     * @param agentId 原基础人格
     * @param conflicts 是否为只用于审计的冲突列表
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Location(String scopeId, String agentId, boolean conflicts) {
        /**
         * 拒绝不明确或超长的旧物理定位。
         * @param scopeId 原作用域
         * @param agentId 原人格
         * @param conflicts 冲突列表标记
         * @throws IllegalArgumentException 当定位无效时
         */
        public Location {
            if (scopeId == null || scopeId.isBlank() || !scopeId.equals(scopeId.strip()) || agentId == null || agentId.isBlank()
                    || !agentId.equals(agentId.strip()) || ("scope:" + scopeId + ":agent:" + agentId).length() > 255) { throw new IllegalArgumentException("旧人格定位无效。"); }
        }
        /**
         * 复现原 target 布局。
         * @return 原物理目标
         */
        public String target() { return "scope:" + scopeId + ":agent:" + agentId; }
        /**
         * 仅访问两种已知人格列表。
         * @return 原物理键
         */
        public String key() { return conflicts ? "personality.conflict" : "personality.delta"; }
    }
    /**
     * 原行不可变预览，所有未知元数据保留在原文，不生成随机来源或 ID。
     * @param location 实际定位
     * @param version 原行版本
     * @param digest 完整原文摘要
     * @param json 完整有界原文
     * @param fallbackTime 固定缺失时间估算
     * @param entries 有界字段预览
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Preview(Location location, long version, String digest, String json, Instant fallbackTime, List<Entry> entries) {
        /**
         * 冻结原文和条目，摘要与原文必须一致。
         * @param location 原定位
         * @param version 原版本
         * @param digest 完整摘要
         * @param json 原文
         * @param fallbackTime 固定时间估算
         * @param entries 预览条目
         * @throws IllegalArgumentException 当版本、容量或摘要不一致时
         */
        public Preview {
            Objects.requireNonNull(location); Objects.requireNonNull(fallbackTime); entries = List.copyOf(entries);
            if (version < 0 || json == null || json.length() > 262144 || !PersonalityKeys.digest(json).equals(digest) || entries.size() > 128) { throw new IllegalArgumentException("旧人格预览版本、摘要或容量无效。"); }
        }
    }
    /**
     * 原字段只作数据展示；其是否可应用仍由目标基础人格及真实来源决定。
     * @param id 原标识或明确行内位置
     * @param field 原字段
     * @param text 原内容
     * @param source 原不透明来源字符串
     * @param reason 冲突原因，不能因为空而激活冲突项
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Entry(String id, String field, String text, String source, String reason) {}
    /**
     * 管理方固定原依据及来源映射；不将旧平台字符串自动解释为 UUID。
     * @param preview 已确认原行
     * @param expected 原目标绑定
     * @param base 当前可信基础人格
     * @param sources 原来源字符串到真实用户上下文的明确映射
     * @param operator 已经入口授权的真实管理操作者
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Import(Preview preview, PersonalityBinding expected, BasePersonality base, Map<String, AgentActorContext> sources, AgentActorContext operator) {
        /**
         * 固定完整导入动作，来源定位是全局幂等键，不提供改目标重导旁路。
         * @param preview 原行预览
         * @param expected 固定绑定
         * @param base 原目标人格
         * @param sources 明确来源映射
         * @param operator 真实管理来源
         * @throws IllegalArgumentException 当基础、作用域或来源映射不一致时
         */
        public Import {
            Objects.requireNonNull(preview); Objects.requireNonNull(expected); Objects.requireNonNull(base); Objects.requireNonNull(operator); sources = Map.copyOf(sources);
            if (!base.fingerprint().equals(expected.baseFingerprint()) || !base.id().equals(preview.location().agentId())
                    || !expected.scope().scopeId().equals(preview.location().scopeId()) || operator.origin() != AgentActorContext.Origin.USER
                    || sources.values().stream().anyMatch(actor -> actor.origin() != AgentActorContext.Origin.USER || !actor.scope().equals(expected.scope()))) {
                throw new IllegalArgumentException("旧人格目标、基础或来源映射不一致。");
            }
        }
    }
    /**
     * 已提交的导入结果，冲突及不兼容项只保留审计，不进入 Prompt。
     * @param imported 实际新增候选标识
     * @param skipped 未应用的原条目及固定原因
     * @param auditKey 不可变迁移回执定位
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    record Result(List<UUID> imported, Map<String, String> skipped, String auditKey) {
        /**
         * 冻结持久化结果。
         * @param imported 新增候选
         * @param skipped 未应用条目
         * @param auditKey 审计定位
         */
        public Result { imported = List.copyOf(imported); skipped = Map.copyOf(skipped); }
    }
}
