package org.zexnocs.teanekocore.api_response.interfaces;

import org.zexnocs.teanekocore.api_response.api.IAPIResponseData;

/**
 * 单次响应流的有界聚合器，由协议实现验证容量和完整性，不在接收分片时执行业务副作用。
 * <br>2026/11/10：只有传输正常结束才允许生成完整结果；取消或断流直接丢弃当前聚合。
 *
 * @param <DATA> 单个事件的数据类型
 * @param <RES> 完整响应类型
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public interface IAPIResponseAccumulator<DATA extends IAPIResponseData, RES extends IAPIResponseData> {
    /**
     * 接收一个完整解码的事件，调用在单个订阅内串行发生。
     * @param data 当前事件
     * @throws IllegalArgumentException 当协议、容量或序列不合法时
     */
    void accept(DATA data);

    /**
     * 在流正常结束后验证协议终态，缺失终态不能冒充成功。
     * @return 完整且可交付的响应
     * @throws IllegalArgumentException 当流不完整时
     */
    RES finish();
}
