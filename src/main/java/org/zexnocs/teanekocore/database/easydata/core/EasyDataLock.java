package org.zexnocs.teanekocore.database.easydata.core;

/**
 * 事务 DTO 的读取方式，锁持有到外层数据库事务结束，不由业务层接触 JPA 锁类型。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public enum EasyDataLock { NONE, READ, WRITE }
