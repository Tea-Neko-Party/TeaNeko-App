package org.zexnocs.teanekocore.file_config;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件配置服务类
 *
 * <p>配置加载顺序：
 * <pre>
 * config/xxx 存在
 *     → 直接读取
 *
 * config/xxx 不存在
 *     → resources/templates/xxx
 *          存在 → copy → config → 读取
 *          不存在 → warn → 使用默认构造器生成 config
 * </pre>
 * <br>4.2.1: 支持从模板中读取。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 * @version 4.2.1
 */
@Service
@RequiredArgsConstructor
public class FileConfigService implements IFileConfigService {
    /// 根目录
    public static final Path ROOT_PATH = Paths.get("config");

    /// resource 文件中的模板目录
    public static final Path TEMPLATE_PATH = Paths.get("templates/config");

    /// 用于防止重复 init
    private final AtomicBoolean isInit = new AtomicBoolean(false);

    /// scanner
    private final FileTypeScanner fileTypeScanner;
    private final IClassScanner iClassScanner;
    private final ILogger iLogger;

    /// config 缓存，支持热加载
    private final Map<Class<? extends IFileConfigData>, IFileConfigData> configCache = new ConcurrentHashMap<>();

    /**
     * 获取 config 实例
     *
     * @param clazz 需要返回的 config class
     * @return config 实例
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data
     */
    @Override
    public @NonNull <T extends IFileConfigData> T get(Class<T> clazz) throws FileConfigDataNotFoundException {
        return clazz.cast(
                Optional.ofNullable(configCache.get(clazz))
                        .orElseThrow(() -> new FileConfigDataNotFoundException(
                                "未找到 config data: " + clazz.getName()))
        );
    }

    /**
     * 写入一个 config 文件
     *
     * @param data 需要写入的 config data
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data 的信息
     */
    @Override
    public void write(IFileConfigData data) throws FileConfigDataNotFoundException, IOException {
        var annotation = data.getClass().getAnnotation(FileConfig.class);
        if(annotation == null) {
            throw new FileConfigDataNotFoundException("未找到 config data 的注解: " + data.getClass().getName());
        }

        // 获取解析器
        var parser = fileTypeScanner.get(annotation.type());
        if (parser == null) {
            throw new FileConfigDataNotFoundException("未找到解析器: " + annotation.type());
        }

        // 获取准确文件
        Path file = buildConfigFilePath(annotation, parser);

        // 确保父目录存在
        Files.createDirectories(file.getParent());

        // 写入文件
        parser.fromDataToWrite(file, data);

        // 更新缓存
        configCache.put(data.getClass(), data);
    }

    /**
     * 初始化方法。
     * 用于防止第一次重复加载。
     */
    @Override
    public void init() {
        if (isInit.compareAndSet(false, true)) {
            reload();
        }
    }

    /**
     * 热重载方法。
     * 扫描所有的类，并尝试读取配置。
     */
    @Override
    public void reload() {
        // 确保目录存在
        try {
            Files.createDirectories(ROOT_PATH);
        } catch (IOException e) {
            iLogger.error(getClass().getSimpleName(),
                    "无法创建配置目录: " + ROOT_PATH, e);
            return;
        }

        var pairs = iClassScanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class);
        for(var pair: pairs.entrySet()) {
            var clazz = pair.getKey();
            var annotation = pair.getValue();
            // 获取解析器
            var parser = fileTypeScanner.get(annotation.type());
            if(parser == null) {
                iLogger.error(getClass().getSimpleName(),
                        "未找到解析器: " + annotation.type());
                continue;
            }
            // 获取准确文件
            var file = buildConfigFilePath(annotation, parser);

            // 开始构造 data
            try {
                final IFileConfigData data;
                if (Files.isRegularFile(file)) {
                    // 存在则直接读取
                    data = loadFromFile(file, clazz, parser);
                } else {
                    // 否则从 template 中复制读取
                    data = loadFromTemplate(file, clazz, parser);
                }
                configCache.put(clazz, data);
            } catch (Exception e) {
                iLogger.error(getClass().getSimpleName(),
                        "加载配置失败: " + file, e);
            }
        }
    }

    /**
     * 从 file 中加载 config data
     *
     * @param file   文件
     * @param clazz  需要返回的 config class
     * @param parser 解析器
     * @return {@link T } 加载出的数据
     * @throws IOException IO 异常
     */
    private <T extends IFileConfigData> T loadFromFile(Path file,
                                                       Class<T> clazz,
                                                       IFileTypeParser parser) throws IOException {
        try (var is = Files.newInputStream(file)) {
            return parser.fromFileToData(is, clazz);
        }
    }

    /**
     * 从模板中读取。
     *
     * @param file       file 路径
     * @param clazz      数据类型
     * @param parser     数据解析器
     * @return {@link T } 数据类型
     * @throws IOException IO 异常
     */
    private <T extends IFileConfigData> T loadFromTemplate(Path file,
                                                           Class<T> clazz,
                                                           IFileTypeParser parser) throws IOException {
        // 获取到 template 的 classpath 路径
        String resourcePath = buildResourcePath(file);
        ClassPathResource resource = new ClassPathResource(resourcePath);
        Files.createDirectories(file.getParent());
        if (resource.exists()) {
            // 如果存在模板，则直接复制。
            try (var is = resource.getInputStream()) {
                // 复制一份
                Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return loadFromFile(file, clazz, parser);
        } else {
            // 如果不存在，则使用默认构造器构造一个。
            // 警告说该配置没有模板类。
            iLogger.warn(
                    getClass().getSimpleName(),
                    "未找到配置模板: %s ，将使用默认配置生成: %s".formatted(resourcePath, file)
            );
            try {
                var data = clazz.getDeclaredConstructor().newInstance();
                parser.fromDataToWrite(file, data);
                return data;
            } catch (InstantiationException |
                     IllegalAccessException |
                     InvocationTargetException |
                     NoSuchMethodException e) {
                throw new RuntimeException(
                        "无法创建默认配置，请确保存在无参构造器: " + clazz.getName(), e);
            }
        }
    }

    /**
     * 根据 注解、解析器 构造出文件路径
     *
     * @param annotation 注解，获取文件名
     * @param parser     解析器，获取文件类型
     * @return {@link Path } 文件路径
     */
    private Path buildConfigFilePath(FileConfig annotation, IFileTypeParser parser) {
        return ROOT_PATH.resolve(annotation.path())
                .resolve(annotation.value() + "." + parser.getSuffix());
    }

    /**
     * 根据 Path 构造出 classpath 资源路径
     *
     * @param file 文件路径
     * @return {@link String } resource 路径
     */
    private String buildResourcePath(Path file) {
        // Path -> classpath string
        Path relative = ROOT_PATH.relativize(file);
        return TEMPLATE_PATH.resolve(relative)
                .toString()
                .replace('\\', '/');
    }
}
