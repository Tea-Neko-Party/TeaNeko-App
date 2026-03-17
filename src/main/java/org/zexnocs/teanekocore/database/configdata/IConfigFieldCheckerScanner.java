package org.zexnocs.teanekocore.database.configdata;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用于扫描全部的 {@link IConfigFieldChecker}
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
@RequiredArgsConstructor
@Component
public class IConfigFieldCheckerScanner extends AbstractScanner {

    /// 域缓存
    private final Map<Class<? extends IConfigFieldChecker>, IConfigFieldChecker> checkers = new ConcurrentHashMap<>();
    private final IBeanScanner iBeanScanner;

    /**
     * 扫描方法。
     *
     */
    @Override
    protected void _scan() {
        var beans = iBeanScanner.getBeansOfType(IConfigFieldChecker.class);
        for(var bean : beans.values()) {
            checkers.put(bean.getClass(), bean);
        }
    }

    /**
     * 获取 IConfigFieldChecker 实例的方法。
     *
     * @param clazz 类
     * @return IConfigFieldChecker 实例
     * @throws RuntimeException 如果构造失败
     */
    @Nullable
    public IConfigFieldChecker get(Class<? extends IConfigFieldChecker> clazz) throws RuntimeException {
        // 如果 clazz 是抽象或者接口，则返回 null
        if(clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
            return null;
        }

        var checker = checkers.get(clazz);
        if(checker == null) {
            // 尝试利用无参构造器构造一个
            try {
                checker = clazz.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException
                     | InvocationTargetException | NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
        return checker;
    }

    /**
     * 清理原始数据的方法。
     *
     */
    @Override
    protected void _clear() {
        checkers.clear();
    }
}
