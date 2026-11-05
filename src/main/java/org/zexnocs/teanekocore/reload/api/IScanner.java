package org.zexnocs.teanekocore.reload.api;

/**
 * 扫描器接口。
 * 用于扫描指定路径下的类，并根据注解或其他标识进行处理
 * <br>2026/11/05：业务扫描器负责缓存发现结果；查询不扫描，初始化与显式重扫负责重新构建目录。
 *
 * @author zExNocs
 * @date 2026/02/11
 * @since 4.0.0
 * @version 4.8.2-l
 */
public interface IScanner extends IReloadable {
}
