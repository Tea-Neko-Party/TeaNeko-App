package org.zexnocs.teanekoagent.component.llm.framework.structured;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.reload.AbstractScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 输出类型目录，初始化或显式重扫时编译完整候选后原子发布。
 * <br>2026/11/06：请求只读缓存，首次失败不懒扫描，重扫失败保留旧目录。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
@Component
public final class LLMJsonSchemas extends AbstractScanner {
    private final IClassScanner scanner;
    private volatile Map<Class<?>, Definition> definitions = Map.of();

    /** @param scanner 仅在初始化和显式重扫借用的类发现入口 */
    public LLMJsonSchemas(IClassScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * 只读取已发布的类型，不按请求发现字段或注解。
     * @param type 预先声明的输出类型
     * @return 当前固定合同
     * @throws IllegalStateException 当类型尚未注册或初始化失败时
     */
    public Definition require(Class<?> type) {
        var result = definitions.get(type);
        if (result == null) {
            throw new IllegalStateException("JSON 输出类型尚未注册，请声明 @LLMJsonType 并完成扫描初始化：" + type.getName());
        }
        return result;
    }

    /** 完整编译后发布，不在在线目录中逐项写入。 */
    @Override
    protected synchronized void _scan() {
        var candidates = new LinkedHashMap<Class<?>, Definition>();
        scanner.getClassesWithAnnotation(LLMJsonType.class).forEach((type, annotation) -> {
            int maximum = annotation.maxCharacters();
            if (maximum < 2 || maximum > 262144) {
                throw new IllegalArgumentException("JSON 输出容量须在 2 至 262144 字符之间：" + type.getName());
            }
            candidates.put(type, new Definition(LLMJsonSchema.fromType(type), maximum));
        });
        definitions = Map.copyOf(candidates);
    }

    /** 重扫期间保留在线快照，新目录由扫描成功后替换。 */
    @Override
    protected void _clear() { }

    /**
     * 不含反射成员或运行资源的固定输出声明。
     * @param schema 类型结构
     * @param maxCharacters 解码容量
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public record Definition(LLMJsonSchema schema, int maxCharacters) { }
}
