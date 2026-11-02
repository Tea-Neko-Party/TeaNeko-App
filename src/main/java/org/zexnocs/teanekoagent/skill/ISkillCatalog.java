package org.zexnocs.teanekoagent.skill;

/**
 * 可替换的可信 Skill 目录来源，加载失败不发布部分候选或清空上一个完整快照。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ISkillCatalog {
    /** @return 当前完整快照，首次使用可惰性加载 */
    SkillCatalogSnapshot snapshot();
    /** @return 可信宿主主动重载后的完整快照，聊天管理须使用带 guard 的入口 */
    default SkillCatalogSnapshot reload() { return reload(() -> {}); }
    /**
     * 全部校验成功后发布，授权或取消失败保留旧快照。
     * @param guard 发布前的当前授权复核
     * @return 原子替换的新快照
     */
    SkillCatalogSnapshot reload(Runnable guard);
}
