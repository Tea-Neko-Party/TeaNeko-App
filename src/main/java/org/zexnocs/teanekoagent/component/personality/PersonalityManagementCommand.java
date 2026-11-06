package org.zexnocs.teanekoagent.component.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;

import java.util.Objects;
import java.util.UUID;

/**
 * 可信管理入口固定的补正操作，身份来自原生或独立管理入口，版本来自显式查询结果。
 * <br>2026/11/03：接受显式授权的管理主体，不继承原生用户身份。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/06：区分实例空间与聊天绑定版本，固定基础人格指纹。
 *
 * @param operationId 原消息稳定操作标识
 * @param actor 真实 DEBUG 操作者，由调用边界校验权限
 * @param action 管理动作
 * @param key 目标或源补正 key
 * @param bindingRevision 当前 scope 的原绑定版本
 * @param stateRevision 目标集合原版本，绑定新空 key 时为 -1
 * @param correctionId 编辑、锁定或撤销的原记录
 * @param field 新增字段
 * @param text 新增或编辑正文
 * @param locked 明确锁定值
 * @param baseFingerprint 独立空间操作固定的基础人格内容指纹
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.4-l
 */
public record PersonalityManagementCommand(UUID operationId, AgentActorContext actor, Action action, String key,
        long bindingRevision, long stateRevision, @Nullable UUID correctionId, @Nullable String field,
        @Nullable String text, @Nullable Boolean locked, @Nullable String baseFingerprint) {
    /**
     * 保留聊天管理命令，基础身份仍由真实绑定仲裁。
     * @param operationId 操作编号
     * @param actor 管理来源
     * @param action 动作
     * @param key 空间
     * @param bindingRevision 绑定版本
     * @param stateRevision 内容版本
     * @param correctionId 补正编号
     * @param field 新增字段
     * @param text 正文
     * @param locked 锁定值
     */
    public PersonalityManagementCommand(UUID operationId, AgentActorContext actor, Action action, String key,
            long bindingRevision, long stateRevision, @Nullable UUID correctionId, @Nullable String field,
            @Nullable String text, @Nullable Boolean locked) {
        this(operationId, actor, action, key, bindingRevision, stateRevision, correctionId, field, text, locked, null);
    }

    /**
     * 拒绝不符合动作的数据，不能由候选正文声明主体或伪造管理字段。
     * 各动作明确限定必需及禁止的参数，其余内容边界统一校验。
     * @param operationId 原操作
     * @param actor 真实来源
     * @param action 动作
     * @param key 补正定位
     * @param bindingRevision 绑定版本
     * @param stateRevision 内容版本
     * @param correctionId 原记录
     * @param field 新增字段
     * @param text 正文
     * @param locked 锁定状态
     * @param baseFingerprint 可选的基础人格内容指纹
     * @throws IllegalArgumentException 当身份、版本或动作参数无效时
     * @throws NullPointerException 当操作标识、来源或动作为空时
     */
    public PersonalityManagementCommand {
        Objects.requireNonNull(operationId); Objects.requireNonNull(actor); Objects.requireNonNull(action);
        boolean validArguments = switch (action) {
            case ADD -> correctionId == null && field != null && text != null && locked == null;
            case EDIT -> correctionId != null && field == null && text != null && locked == null;
            case LOCK -> correctionId != null && field == null && text == null && locked != null;
            case REMOVE -> correctionId != null && field == null && text == null && locked == null;
            case BIND, CLONE, BACKUP -> correctionId == null && field == null && text == null && locked == null;
        };
        // 2026/11/03：系统学习与管理员补正保持不同来源。
        if (actor.origin() == AgentActorContext.Origin.SYSTEM || actor.userId().isEmpty() || key == null || key.isBlank() || key.length() > 256
                || bindingRevision < 0
                || bindingRevision == 0 && (actor.origin() != AgentActorContext.Origin.MANAGEMENT || action == Action.BIND)
                || baseFingerprint != null && (baseFingerprint.isBlank() || baseFingerprint.length() > 128)
                || stateRevision < (action == Action.BIND ? -1 : 0)
                || !validArguments
                || text != null && (text.isBlank() || text.length() > 512)
                || field != null && !BasePersonality.STYLE_FIELDS.contains(field)) {
            throw new IllegalArgumentException("人格管理来源、版本或动作参数无效。");
        }
    }

    /**
     * 补正管理的独立动作，不作为模型自动学习协议。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Action { BIND, CLONE, BACKUP, ADD, EDIT, LOCK, REMOVE }
}
