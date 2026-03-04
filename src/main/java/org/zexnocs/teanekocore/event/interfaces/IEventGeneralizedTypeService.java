package org.zexnocs.teanekocore.event.interfaces;

/**
 * 获取 {@link IEvent} 的泛化类型的服务类。
 *
 * @author zExNocs
 * @date 2026/03/04
 * @since 4.0.12
 */
public interface IEventGeneralizedTypeService {
    /**
     * 获取泛化实际类型。
     *
     * @param eventClass 事件类
     * @return 泛化实际类型
     */
    <T> Class<T> getGeneralizedActualType(Class<? extends IEvent<T>> eventClass);
}
