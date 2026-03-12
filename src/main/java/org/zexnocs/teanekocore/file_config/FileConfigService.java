package org.zexnocs.teanekocore.file_config;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件配置服务类。
 *
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.0
 */
@Service
@RequiredArgsConstructor
public class FileConfigService implements IFileConfigService {
    /// 根目录
    public static final Path ROOT_PATH = Paths.get("config");

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
                        .orElseThrow(() -> new FileConfigDataNotFoundException("未找到 config data: " + clazz.getName()))
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
        // 确保存在 root path 中的目录
        if (!Files.exists(ROOT_PATH)) {
            Files.createDirectories(ROOT_PATH);
        }

        // 获取解析器
        var parser = fileTypeScanner.get(annotation.type());
        if (parser == null) {
            throw new FileConfigDataNotFoundException("未找到解析器: " + annotation.type());
        }

        // 获取准确文件
        Path file = ROOT_PATH.resolve(annotation.path())
                .resolve(annotation.value() + "." + parser.getSuffix());

        // 写入文件
        parser.fromDataToWrite(file, data);

        // 更新缓存
        configCache.put(data.getClass(), data);
    }

    /**
     * 热重载方法。
     * 扫描所有的类，并尝试读取配置。
     */
    @Override
    public void reload() {
        // 确保目录存在
        if (!Files.exists(ROOT_PATH)) {
            try {
                Files.createDirectories(ROOT_PATH);
            } catch (IOException e) {
                iLogger.error(getClass().getSimpleName(),
                        "无法创建配置目录: " + ROOT_PATH, e);
                return;
            }
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
            var root = ROOT_PATH.resolve(annotation.path());
            Path file = root.resolve(annotation.value() + "." + parser.getSuffix());

            // 开始构造 data
            final IFileConfigData data;

            if (Files.exists(file)) {
                // 如果存在文件，则尝试读取
                try (var is = Files.newInputStream(file)) {
                    data = parser.fromFileToData(is, clazz);
                } catch (IOException e) {
                    iLogger.error(getClass().getSimpleName(),
                            "获取数据流出现了问题: " + file, e);
                    continue;
                } catch (Exception e) {
                    iLogger.error(getClass().getSimpleName(),
                            "解析配置文件失败，建议备份并删除旧配置重试: " + file, e);
                    continue;
                }
            } else {
                // 否则尝试以无参构造器构造一个默认配置并写入
                try {
                    // 构造 data
                    data = clazz.getDeclaredConstructor().newInstance();
                    // 确保根目录存在
                    if(!Files.exists(root)) {
                        Files.createDirectories(root);
                    }
                    // 保存默认文件
                    parser.fromDataToWrite(file, data);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    iLogger.error(getClass().getSimpleName(),
                            "无法通过无参构造器创建默认配置，请确保类 " + clazz.getName() + " 有一个公共的无参构造器", e);
                    continue;
                } catch (Exception e) {
                    iLogger.error(getClass().getSimpleName(),
                            "创建或写入默认配置文件失败: " + file, e);
                    continue;
                }
            }
            configCache.put(clazz, data);
        }
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
}
