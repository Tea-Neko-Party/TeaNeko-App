package org.zexnocs.teanekoclient.onebot.data.send;

/**
 * 发送参数数据接口。
 *
 * @param <R> 响应数据类型。
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
public interface ISendParamsData<R> {
    /**
     * 获取发送参数数据的动作。
     *
     * @return 发送参数数据的动作。
     */
    String getAction();

    /**
     * 获取反应数据的类型。
     *
     * @return 反应数据的类型。
     */
    Class<R> getResponseDataType();
}
