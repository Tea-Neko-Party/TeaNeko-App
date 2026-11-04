package org.zexnocs.teanekoagent.component.sticker;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;

/**
 * 有界素材文件端口；阻塞实现由调用方放在借用的虚拟执行域。
 * <br>2026/11/03：发送租约内读取原始字节，不返回本地文件路径。
 * <br>2026/11/03：预览和重新分析共用已有文件的有界解码。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
public interface IStickerAssetStore {
    /**
     * @param settings 固定配置
     * @return 持久存储卷标识，重启保持不变
     */
    String identity(StickerSettings settings);
    /**
     * @param job 原作业
     * @param settings 固定配置
     * @param control 借用控制
     * @return 下载并校验后的暂存及预览
     */
    Prepared prepare(Stickers.Job job,StickerSettings settings,ExecutionControl control);
    /**
     * @param job 原作业
     * @param settings 配置
     * @param control 控制
     * @return 验证并发布后的原内容元数据
     */
    Stickers.Blob publish(Stickers.Job job,StickerSettings settings,ExecutionControl control);
    /**
     * @param job 原作业
     * @param settings 配置
     */
    void discard(Stickers.Job job,StickerSettings settings);
    /**
     * @param blob 确定已不再有效的文件
     * @param settings 配置
     */
    void delete(Stickers.Blob blob,StickerSettings settings);
    /**
     * @param blob 固定内容地址
     * @param settings 配置
     * @return 实际文件是否完整存在
     */
    boolean exists(Stickers.Blob blob,StickerSettings settings);
    /**
     * 2026/11/03：在有效宿主租约中读取并复核摘要。
     * @param blob 固定内容元数据
     * @param settings 原配置
     * @param control 借用控制
     * @return 有界独立字节数组
     */
    byte[] read(Stickers.Blob blob,StickerSettings settings,ExecutionControl control);
    /**
     * 2026/11/03：从现有受控文件生成采样预览，不发起来源下载。
     * @param blob 原文件
     * @param settings 文件预算
     * @param control 借用控制
     * @return 重新校验过的采样
     */
    Prepared preview(Stickers.Blob blob,StickerSettings settings,ExecutionControl control);
    /**
     * 只在受控任务中存活的视觉输入，不持久保存大块 base64。
     * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param blob 原内容元数据
     * @param previews 同一请求中的采样画面
     * @param note 动画采样说明
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.2-l
     * @version 4.8.0-l
     */
    @JsonIgnoreType record Prepared(Stickers.Blob blob,List<String> previews,String note){ }
}
