package org.zexnocs.teanekocore.database.easydata.core.interfaces;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;

import java.util.List;

/**
 * 现有数据库事务内的单目标 DTO，提供无缓存读、当前读、版本写入和有界扫描。
 * 不自行开启事务；调用者通过 EasyData 任务组合多个目标，提交前不发布缓存。
 * <br>2026/11/03：维护可按固定域分页，跨目标结果须经各自 DTO 重新锁定后修改。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.1-l
 */
public interface IEasyDataTransactionDto {
    /**
     * 按精确键读取，READ/WRITE 为数据库当前读。
     * @param key 固定键
     * @param lock 当前事务锁方式
     * @return 当前版本 DTO，不存在时为空
     */
    @Nullable EasyDataRow find(String key, EasyDataLock lock);
    /**
     * 构造未保存的 DTO，数据库唯一键仲裁首次并发创建。
     * @param key 固定键
     * @param value 正文
     * @return 未保存行
     */
    EasyDataRow create(String key, String value);
    /**
     * 保留原实体版本保存并刷新，只有实际事务提交后才失效普通缓存。
     * @param row 同边界的原行或新行
     */
    void save(EasyDataRow row);
    /**
     * 按原版本删除一行，保留外层事务回滚语义。
     * @param row 当前事务取得的行
     */
    void delete(EasyDataRow row);
    /**
     * 稳定键分页，不读取全表；上界为空表示无上界。
     * @param after 排除的下界，首屏为空字符串
     * @param through 包含的上界
     * @param limit 一至一千的页大小
     * @param lock 当前读锁方式
     * @return 键升序的不可变页面
     */
    List<EasyDataRow> scan(String after, @Nullable String through, int limit, EasyDataLock lock);
    /**
     * 按递增行序号逆序分页，允许业务层建立独立的历史游标。
     * @param before 排除的原序号，首次为 Long.MAX_VALUE
     * @param limit 一至一千的页面大小
     * @return 序号倒序页面
     */
    List<EasyDataRow> recent(long before, int limit);
    /**
     * 2026/11/03：按固定域读取倒序页，发现旧目标也不需要全表加载或访问仓库。
     * 只读扫描不能代替业务锁；跨目标保存、删除须重新取得对应目标的事务 DTO。
     * @param before 排除序号，首次使用 Long.MAX_VALUE
     * @param limit 一至一千的页面大小
     * @return 当前域的有限页
     * @throws IllegalArgumentException 当序号或页大小无效时
     * @throws IllegalStateException 当缺少活动数据库事务时
     * @throws UnsupportedOperationException 当替换实现没有域分页能力时
     */
    default List<EasyDataRow> recentNamespace(long before, int limit) { throw new UnsupportedOperationException("当前 EasyData 不支持域维护分页。"); }
}
