package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.memory.layered.MemoryLayers;
import org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceReference;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

/**
 * 保存无正文的空间及分区定位，清理原目录后仍能判定旧代次作业与索引的归属。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
public final class EasyDataMemoryLifecycle {
    private final TeaNekoAgentEasyData data;
    private final EasyDataMemoryNamespaceStore namespaces;
    private final ObjectMapper mapper;
    /**
     * 借用原专用入口和空间锁，不在构造阶段访问数据库。
     * @param data 原入口
     * @param namespaces 空间元数据
     * @param mapper 原转换器
     */
    public EasyDataMemoryLifecycle(TeaNekoAgentEasyData data, EasyDataMemoryNamespaceStore namespaces, ObjectMapper mapper) {
        this.data = data.inNamespace("agent.memory.lifecycle.v1"); this.namespaces = namespaces; this.mapper = mapper;
    }
    /**
     * 在当前消息或维护事务登记稳定定位，不保存消息正文或凭据。
     * @param stream 原分区
     */
    public void registerInTransaction(MemoryLayers.Stream stream) {
        var spaces = data.get("spaces").atomic(); String space = space(stream.reference());
        if (spaces.find(space, EasyDataLock.NONE) == null) { spaces.save(spaces.create(space, mapper.writeValueAsString(stream.reference()))); }
        var streams = data.get("streams").atomic();
        if (streams.find(stream.id(), EasyDataLock.NONE) == null) { streams.save(streams.create(stream.id(), mapper.writeValueAsString(stream.reference()))); }
    }
    /**
     * 解析共享空间定位，未登记时返回空值，不能猜测归属并删除。
     * @param space 共享空间摘要
     * @return 原固定引用
     */
    public @Nullable MemoryNamespaceReference spaceReference(String space) { return reference("spaces", space); }
    /**
     * 原目录回收后解析作业定位。
     * @param stream 分区摘要
     * @return 原固定引用
     */
    public @Nullable MemoryNamespaceReference streamReference(String stream) { return reference("streams", stream); }
    /**
     * 先锁空间再进入业务行锁，返回旧代次判定；缺失元数据时保守保留。
     * @param reference 原定位
     * @return 确认属于旧代次时为真
     */
    public boolean retired(MemoryNamespaceReference reference) {
        var row = namespaces.lockNamespace(reference.namespaceId(), EasyDataLock.WRITE);
        return row != null && namespaces.namespace(row, reference.namespaceId()).generation() > reference.generation();
    }
    /**
     * 共享空间不包含来源 scope。
     * @param reference 固定空间
     * @return 共享空间摘要
     */
    public static String space(MemoryNamespaceReference reference) { return StableDigest.sha256(reference.namespaceId().value(), Long.toString(reference.generation())); }
    /**
     * 在当前事务读取无正文定位。
     * @param target 定位类型
     * @param key 稳定摘要
     * @return 已登记引用或空值
     */
    private @Nullable MemoryNamespaceReference reference(String target, String key) {
        var row = data.get(target).atomic().find(key, EasyDataLock.NONE);
        return row == null ? null : mapper.readValue(row.getValue(), MemoryNamespaceReference.class);
    }
}
