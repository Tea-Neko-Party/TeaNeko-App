package org.zexnocs.teanekocore.file_config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

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
 * <br>2026/11/03：原加载、写入和受限文档操作共用类型锁，记录真实加载版本与发布代次。
 * <br>2026/11/05：配置类型在服务创建时扫描一次，文档访问与重载复用固定目录，避免模型校验反复扫描类路径。
 * <br>2026/11/05：类型发现交给可重扫的专用目录，运行版本查询只访问缓存和原类型锁。
 *
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.2.0
 * @version 4.8.4-l
 */
@Service
public class FileConfigService implements IFileConfigService {
    /// 根目录
    public static final Path ROOT_PATH = Paths.get("config");

    /// resource 文件中的模板目录
    public static final Path TEMPLATE_PATH = Paths.get("templates/config");

    /// 用于防止重复 init
    private final AtomicBoolean isInit = new AtomicBoolean(false);

    /// scanner
    private final FileTypeScanner fileTypeScanner;
    // 2026/11/05：业务查询只读取专用扫描器缓存，类型重扫与配置值重载分别处理。
    private final FileConfigScanner scanner;
    private final ILogger iLogger;
    // 2026/11/03：锁只按已声明类型创建，文件路径不来自远程请求。
    private final Path rootPath;
    private final Map<Class<? extends IFileConfigData>, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    /**
     * @param parsers 原格式注册表
     * @param scanner 已发现的配置类型目录
     * @param logger 原日志
     */
    @Autowired
    public FileConfigService(FileTypeScanner parsers, FileConfigScanner scanner, ILogger logger) {
        this(parsers, scanner, logger, ROOT_PATH);
    }

    /**
     * 为独立宿主和临时目录测试固定根路径，不作为远程配置入口。
     * 2026/11/05：复用专用扫描目录，构造与访问文档均不执行底层发现。
     * @param parsers 原解析器
     * @param scanner 原类型目录
     * @param logger 原日志
     * @param root 固定宿主根目录
     */
    public FileConfigService(FileTypeScanner parsers, FileConfigScanner scanner, ILogger logger, Path root) {
        fileTypeScanner = Objects.requireNonNull(parsers);
        this.scanner = Objects.requireNonNull(scanner);
        iLogger = Objects.requireNonNull(logger);
        rootPath = Objects.requireNonNull(root);
    }

    /**
     * 2026/11/03：新文档 API 不把未注册类、任意路径或不支持的格式当作编辑入口。
     * 2026/11/05：仅查询当前容器的注册目录，保留类型锁、路径和解析器校验。
     * @param type 原注册类型
     * @param action 锁内短操作
     * @param <T> 返回类型
     * @return 实际结果
     */
    @Override
    public <T> T withDocument(Class<? extends IFileConfigData> type,
            java.util.function.Function<FileConfigDocument, T> action) {
        var annotation = scanner.declarations().get(type);
        if (annotation == null || Path.of(annotation.path()).isAbsolute()
                || !annotation.value().matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("文件配置类型未注册或声明路径无效。");
        }
        return locked(type, () -> {
            var parser = fileTypeScanner.get(annotation.type());
            if (!(parser instanceof IFileDocumentParser documentParser)) {
                throw new UnsupportedOperationException("该格式尚未提供原文档编辑能力。");
            }
            Path path = buildConfigFilePath(annotation, parser).toAbsolutePath().normalize();
            if (!path.startsWith(rootPath.toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("配置路径越过固定根目录。");
            }
            byte[] fallback;
            var resource = new ClassPathResource(TEMPLATE_PATH.resolve(annotation.path())
                    .resolve(annotation.value() + "." + parser.getSuffix()).toString().replace('\\', '/'));
            try {
                if (resource.exists()) {
                    try (var input = resource.getInputStream()) {
                        fallback = input.readNBytes(FileConfigDocument.MAX_BYTES + 1);
                    }
                    if (fallback.length > FileConfigDocument.MAX_BYTES) {
                        throw new IllegalArgumentException("配置模板超过文档大小上限。");
                    }
                } else {
                    fallback = documentParser.writeDocument(documentParser.defaultDocument(type));
                }
            } catch (IOException failure) {
                throw new IllegalStateException("无法读取本地配置模板。");
            }
            var document = new FileConfigDocument(rootPath, path, type, documentParser, fallback,
                    (data, revision) -> publish(type, data, revision), () -> stamp(type));
            return action.apply(document);
        });
    }

    /**
     * @param type 固定配置类型
     * @param action 短同步动作
     * @param <T> 返回类型
     * @return 原同步动作结果
     */
    private <T> T locked(Class<? extends IFileConfigData> type, Supplier<T> action) {
        var lock = locks.computeIfAbsent(type, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 2026/11/05：运行查询借用原类型锁，避免仅查询发布代次却创建文档、读取模板或发现类型。
     *
     * @param type 已注册配置类型
     * @param action 同一发布版本内的短同步查询，不等待其他线程或网络
     * @param <T> 返回类型
     * @return 实际查询结果
     * @throws IllegalArgumentException 当类型未注册时
     */
    @Override
    public <T> T withRuntime(Class<? extends IFileConfigData> type,
            java.util.function.Function<FileConfigDocument.Stamp, T> action) {
        if (!scanner.declarations().containsKey(type)) {
            throw new IllegalArgumentException("文件配置类型未注册。");
        }
        return locked(type, () -> action.apply(stamp(type)));
    }

    /**
     * @param type 固定类型
     * @param data 已完成加载的候选
     * @param revision 同一原字节版本
     * @return 实际发布代次
     */
    private FileConfigDocument.Stamp publish(Class<? extends IFileConfigData> type, IFileConfigData data, String revision) {
        var entry = new ConfigEntry(data, true, revision, generation.incrementAndGet());
        configCache.put(type, entry);
        return stamp(type);
    }

    /**
     * @param type 固定类型
     * @return 最近加载状态，不读取或导出配置值
     */
    private FileConfigDocument.Stamp stamp(Class<? extends IFileConfigData> type) {
        var entry = configCache.get(type);
        return entry == null ? new FileConfigDocument.Stamp("unloaded", 0, false)
                : new FileConfigDocument.Stamp(entry.revision(), entry.generation(), !rootUnavailable && entry.current());
    }

    /// config 缓存，支持热加载
    private final Map<Class<? extends IFileConfigData>, ConfigEntry> configCache = new ConcurrentHashMap<>();
    private volatile boolean rootUnavailable;

    /**
     * 获取最后一次成功的数据，最近一次加载失败时仍保留兼容读法。
     *
     * @param clazz 需要返回的 config class
     * @param <T> 配置数据类型
     * @return config 实例
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data
     */
    @Override
    public @NonNull <T extends IFileConfigData> T get(Class<T> clazz) throws FileConfigDataNotFoundException {
        return clazz.cast(
                Optional.ofNullable(configCache.get(clazz))
                        .map(ConfigEntry::data)
                        .orElseThrow(() -> new FileConfigDataNotFoundException(
                                "未找到 config data: " + clazz.getName()))
        );
    }

    /**
     * 以同一条原子缓存记录检查数据和最近加载状态，不把残留旧值当作当前成功配置。
     * @param clazz 配置类型
     * @param <T> 配置数据类型
     * @return 当前加载成功的数据
     * @throws FileConfigDataNotFoundException 当目录不可用、数据缺失或最近加载失败时
     */
    @Override
    public <T extends IFileConfigData> T requireCurrent(Class<T> clazz) throws FileConfigDataNotFoundException {
        var entry = configCache.get(Objects.requireNonNull(clazz, "配置类型不能为空。"));
        if (rootUnavailable || entry == null || !entry.current() || entry.data() == null) {
            throw new FileConfigDataNotFoundException("当前文件配置不可用，请修复并重载：" + clazz.getName());
        }
        return clazz.cast(entry.data());
    }

    /**
     * 写入一个 config 文件
     *
     * @param data 需要写入的 config data
     * @throws FileConfigDataNotFoundException 因为异常原因未找到该 config data 的信息
     * @throws IOException 当目录无法创建或配置文件无法写入时
     */
    @Override
    public void write(IFileConfigData data) throws FileConfigDataNotFoundException, IOException {
        // 2026/11/03：原写入与文档版本编辑互斥，不允许旧缓存覆盖尚未应用的磁盘版本。
        var lock = locks.computeIfAbsent(data.getClass(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            var annotation = data.getClass().getAnnotation(FileConfig.class);
            if (annotation == null) {
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

            var previous = configCache.get(data.getClass());
            if (previous != null && previous.data() == data && !previous.revision().equals(FileConfigDocument.snapshot(file).revision())) {
                throw new java.util.ConcurrentModificationException("磁盘配置已变化，请重新加载后再写入缓存对象。");
            }
            // 2026/11/03：原入口同样原子替换，只有完整写完的文件才成为可见配置。
            var temporary = Files.createTempFile(file.getParent(), ".teaneko-config-", ".tmp");
            try {
                org.zexnocs.teanekocore.utils.PrivateFiles.protect(temporary, false);
                parser.fromDataToWrite(temporary, data);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }

            // 更新缓存
            publish(data.getClass(), data, FileConfigDocument.snapshot(file).revision());
        } finally {
            lock.unlock();
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

    /**
     * 热重载方法。
     * 2026/11/05：只重载目录中已发现类型的配置值；类型变化通过专用扫描器显式重扫。
     * 失败保留最后有效值供 get 使用，requireCurrent 明确拒绝。
     */
    @Override
    public void reload() {
        // 确保目录存在
        try {
            Files.createDirectories(rootPath);
            rootUnavailable = false;
        } catch (IOException e) {
            rootUnavailable = true;
            configCache.keySet().forEach(this::markUnavailable);
            iLogger.error(getClass().getSimpleName(),
                    "无法创建配置目录: " + ROOT_PATH, e);
            return;
        }

        for (var pair : scanner.declarations().entrySet()) {
            var clazz = pair.getKey();
            // 2026/11/03：每个类型单独持锁，不把全局重载锁带入模块回调。
            var lock = locks.computeIfAbsent(clazz, ignored -> new ReentrantLock());
            lock.lock();
            try {
                var annotation = pair.getValue();
                // 获取解析器
                var parser = fileTypeScanner.get(annotation.type());
                if (parser == null) {
                    markUnavailable(clazz);
                    iLogger.error(getClass().getSimpleName(),
                            "未找到解析器: " + annotation.type());
                    continue;
                }
                // 获取准确文件
                var file = buildConfigFilePath(annotation, parser);

                // 开始构造 data
                try {
                    final Loaded<? extends IFileConfigData> loaded;
                    if (Files.isRegularFile(file)) {
                        // 存在则直接读取
                        loaded = loadFromFile(file, clazz, parser);
                    } else {
                        // 否则从 template 中复制读取
                        loaded = loadFromTemplate(file, clazz, parser);
                    }
                    publish(clazz, loaded.data(), loaded.revision());
                } catch (Exception e) {
                    markUnavailable(clazz);
                    if (annotation.sensitive()) {
                        iLogger.error(getClass().getSimpleName(), "敏感配置加载失败: " + file + "，" + org.zexnocs.teanekocore.logger.FailureDiagnostic.from(e));
                    } else {
                        iLogger.error(getClass().getSimpleName(), "加载配置失败: " + file, e);
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 原子标记该类型最近一次加载失败，保留旧数据兼容其他模块而不暴露原始解析异常。
     * @param type 本次加载失败的配置类型
     */
    private void markUnavailable(Class<? extends IFileConfigData> type) {
        // 2026/11/03：失败也发布新代次，原应用确认不能跨重载继续有效。
        locked(type, () -> {
            configCache.compute(type, (ignored, previous) -> new ConfigEntry(previous == null ? null : previous.data(),
                    false, previous == null ? "unloaded" : previous.revision(), generation.incrementAndGet()));
            return true;
        });
    }

    /**
     * 从 file 中加载 config data
     *
     * @param file   文件
     * @param clazz  需要返回的 config class
     * @param parser 解析器
     * @param <T> 配置数据类型
     * @return {@link T } 加载出的数据
     * @throws IOException IO 异常
     */
    private <T extends IFileConfigData> Loaded<T> loadFromFile(Path file,
                                                       Class<T> clazz,
                                                       IFileTypeParser parser) throws IOException {
        // 2026/11/03：解析和版本来自同一字节快照，避免把后来的磁盘内容冒充本次加载。
        var snapshot = FileConfigDocument.snapshot(file);
        try (var is = new ByteArrayInputStream(snapshot.content())) {
            return new Loaded<>(Objects.requireNonNull(parser.fromFileToData(is, clazz), "配置解析结果不能为空。"), snapshot.revision());
        }
    }

    /**
     * 从模板中读取。
     *
     * @param file       file 路径
     * @param clazz      数据类型
     * @param parser     数据解析器
     * @param <T> 配置数据类型
     * @return {@link T } 数据类型
     * @throws IOException IO 异常
     */
    private <T extends IFileConfigData> Loaded<T> loadFromTemplate(Path file,
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
                return new Loaded<>(data, FileConfigDocument.snapshot(file).revision());
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
        // 2026/11/03：独立宿主可使用固定临时根目录，生产默认仍为 config。
        return rootPath.resolve(annotation.path())
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
        Path relative = rootPath.relativize(file);
        return TEMPLATE_PATH.resolve(relative)
                .toString()
                .replace('\\', '/');
    }

    /**
     * 最近一次加载结果与最后有效值的原子记录，失败状态不缓存原始异常或文件内容。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param data 最后有效数据，尚未成功时为空
     * @param current 最近一次已完成加载是否成功
     * @param revision 同一已加载原文版本
     * @param generation 当前进程发布代次
     * <br>2026/11/03：记录原文身份与加载代次。
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.7.0-l
     */
    private record ConfigEntry(@org.jspecify.annotations.Nullable IFileConfigData data,
                               boolean current, String revision, long generation) {}

    /**
     * 同一字节快照绑定的加载结果。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param data 原类型候选
     * @param revision 原文件版本
     * @param <T> 配置类型
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Loaded<T extends IFileConfigData>(T data, String revision) {}
}
