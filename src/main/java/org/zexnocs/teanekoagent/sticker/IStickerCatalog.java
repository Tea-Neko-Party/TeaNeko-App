package org.zexnocs.teanekoagent.sticker;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
/**
 * 实例目录原子事务端口；回调只操作有界状态，不执行文件或网络 I/O。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
public interface IStickerCatalog {
    /**
     * 在实例唯一根行锁下读改写，实际提交后才返回结果。
     * @param settings 固定实例及容量
     * @param action 短事务领域操作
     * @param <T> 非空结果
     * @return 真实提交后的值
     */
    <T> CompletionStage<T> change(StickerSettings settings, Function<Stickers.State,T> action);
}
