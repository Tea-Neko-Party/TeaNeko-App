package org.zexnocs.teanekocore.database.configdata;

import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigManagerNamespaceMismatchException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataQueryService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.description.Mask;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;

/**
 * 配置数据查询服务抽象类
 *
 * @param <T> 配置数据的键类型
 * @author zExNocs
 * @date 2026/02/16
 */
public abstract class AbstractConfigDataQueryService<T> implements IConfigDataQueryService<T> {
    private final ConfigDataGetService configDataGetService;
    private final ConfigManagerScanner configManagerScanner;
    private final ILogger logger;
    private final ObjectMapper objectMapper;

    public AbstractConfigDataQueryService(
            ConfigDataGetService configDataGetService,
            ConfigManagerScanner configManagerScanner,
            ObjectMapper objectMapper,
            ILogger logger) {
        this.configManagerScanner = configManagerScanner;
        this.logger = logger;
        this.objectMapper = objectMapper;
        this.configDataGetService = configDataGetService;
    }

    /**
     * 查询一个配置管理器的详细信息，包括配置描述和配置数据描述。
     *
     * @param configManager 要查询的配置管理器
     * @return {@link String } 配置管理器的详细信息字符串
     * @throws ConfigManagerNamespaceMismatchException 当ConfigManager的命名空间与ConfigData的命名空间不匹配时抛出此异常。
     */
    @Override
    public String queryOneConfigManagerDetail(ConfigManager configManager) throws ConfigManagerNamespaceMismatchException {
        var sb = new StringBuilder();
        sb.append("=> 配置名称：").append(configManager.value()).append("\n");
        sb.append("=> 配置描述：").append(configManager.description()).append("\n");
        sb.append("=> 配置数据描述：").append("\n");
        for(var filed: configManager.configType().getDeclaredFields()) {
            var description = filed.getAnnotation(Description.class);
            var clazz = filed.getType();
            sb.append("- ").append(filed.getName()).append("(").append(clazz.getSimpleName());
            // 判断是不是 list
            if(List.class.isAssignableFrom(clazz)) {
                var genericType = filed.getGenericType();
                if(genericType instanceof java.lang.reflect.ParameterizedType) {
                    var typeArgs = ((java.lang.reflect.ParameterizedType) genericType).getActualTypeArguments();
                    if(typeArgs.length > 0) {
                        var typeName = typeArgs[0].getTypeName();
                        if(typeName.contains(".")) {
                            typeName = typeName.substring(typeName.lastIndexOf('.') + 1);
                        }
                        sb.append("<").append(typeName).append(">");
                    }
                }
            }
            sb.append(")");
            if(description != null) {
                sb.append("：").append(description.value());
            }
            sb.append("\n");
        }
        // 删除最后一个换行符
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * 查询所有配置管理器的详细信息，包括配置描述和配置数据描述。
     *
     * @return {@link List }<{@link String }> 所有配置管理器的详细信息字符串列表
     */
    @Override
    public List<String> queryAllConfigManagerDetails() {
        var configManagers = new HashSet<ConfigManager>();
        for(var namespace: getNamespaces()) {
            configManagers.addAll(configManagerScanner.getConfigManagersByNamespace(namespace));
        }
        return configManagers.stream()
                .map(this::queryOneConfigManagerDetail)
                .toList();
    }

    /**
     * 查询一个配置管理器的详细信息，包括配置描述和配置数据描述，并将配置数据转化成 JSON 字符串。
     *
     * @param configManager 要查询的配置管理器
     * @param key           配置数据的键
     * @return {@link String }
     * @throws IllegalAccessException              当无法访问配置数据的字段时抛出此异常。
     * @throws ConfigDataNotFoundException             当未找到指定的ConfigData时抛出此异常。
     * @throws ConfigManagerNamespaceMismatchException 当ConfigManager的命名空间与ConfigData的命名空间不匹配时抛出此异常。
     */
    @Override
    public String queryOneConfigManagerDetailInObject(ConfigManager configManager, String key)
            throws IllegalAccessException,
            ConfigDataNotFoundException,
            ConfigManagerNamespaceMismatchException {
        try {
            var sb = new StringBuilder();
            var config = configDataGetService.getConfigData(configManager, key).orElse(null);
            if(config == null) {
                throw new ConfigDataNotFoundException("未找到对应的配置数据，key: " + key);
            }
            sb.append("=> 配置名称：").append(configManager.value()).append("\n");
            sb.append("=> 配置描述：").append(configManager.description()).append("\n");
            sb.append("=> 配置数据：").append("\n");
            for(var field: configManager.configType().getDeclaredFields()) {
                field.setAccessible(true);
                var mask = field.getAnnotation(Mask.class);
                sb.append("- ").append(field.getName()).append("：");
                if(mask == null) {
                    sb.append(objectMapper.writeValueAsString(field.get(config)));
                } else {
                    sb.append(mask.value());
                }
                sb.append("\n");
            }
            // 删除最后一个换行符
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
                sb.deleteCharAt(sb.length() - 1);
            }
            return sb.toString();
        } catch (IllegalArgumentException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "获取配置失败", e);
            return "";
        } catch (JacksonException e) {
            logger.errorWithReport(this.getClass().getName(),
                    "将配置转化成 JSON 字符串失败", e);
            return "";
        }
    }

    /**
     * 查询所有配置管理器的详细信息，包括配置描述和配置数据描述，并将配置数据转化成 JSON 字符串。
     *
     * @param key 配置数据的键
     * @return {@link List }<{@link String }>
     */
    @Override
    public List<String> queryAllConfigManagerDetailsInObject(String key) {
        var configManagers = new HashSet<ConfigManager>();
        for(var namespace: getNamespaces()) {
            configManagers.addAll(configManagerScanner.getConfigManagersByNamespace(namespace));
        }
        return configManagers.stream()
                .map(configManager -> {
                    try {
                        return queryOneConfigManagerDetailInObject(configManager, key);
                    } catch (IllegalAccessException | ConfigManagerNamespaceMismatchException e) {
                        logger.errorWithReport(this.getClass().getName(),
                                "获取配置失败", e);
                        return "";
                    } catch (ConfigDataNotFoundException e) {
                        // 说明没有注册该 key 的配置，忽略
                        return "";
                    }
                })
                .filter(s -> !s.isBlank())
                .toList();
    }
}
