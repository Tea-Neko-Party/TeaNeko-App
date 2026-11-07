import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;

/**
 * 停机部署时显式执行带校验值的 MySQL 脚本；应用运行账号不承担 DDL。
 * <br>2026/11/06：支持空库基线、既有表接管、重复执行和只读结构核验。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
public final class SchemaMigration {
    /**
     * 不创建迁移工具实例。
     */
    private SchemaMigration() {
    }

    /**
     * 凭据仅从环境读取；JDBC 驱动由命令行 classpath 提供。
     *
     * @param args apply 或 verify，之后可指定脚本目录
     * @throws Exception 连接、校验或迁移失败，已完成的 MySQL DDL 不声称自动回滚
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2 || !List.of("apply", "verify").contains(args[0])) {
            throw new IllegalArgumentException("用法：SchemaMigration.java apply|verify [脚本目录]");
        }
        var directory = Path.of(args.length == 2 ? args[1] : "scripts/database/mysql");
        try (var connection = DriverManager.getConnection(required("TEANEKO_MIGRATION_URL"),
            required("TEANEKO_MIGRATION_USER"), required("TEANEKO_MIGRATION_PASSWORD"))) {
            if (connection.getCatalog() == null || connection.getCatalog().isBlank()
                || !connection.getMetaData().getDatabaseProductName().equals("MySQL")) {
                throw new IllegalArgumentException("迁移连接必须明确选择 MySQL 目标库。");
            }
            migrate(connection, directory, args[0].equals("apply"));
        }
    }

    /**
     * 同库部署互斥；失败不登记成功，修正原因后可重跑幂等脚本。
     *
     * @param connection 借用的专用迁移连接
     * @param directory  受版本控制的脚本目录
     * @param apply      是否允许执行待迁移 DDL
     * @throws Exception 脚本、结构或既有校验值不符
     */
    static void migrate(Connection connection, Path directory, boolean apply) throws Exception {
        String lock = "teaneko.schema." + digest(connection.getCatalog()).substring(0, 32);
        try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 30)")) {
            statement.setString(1, lock);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("目标库已有迁移执行中，未取得部署锁。");
                }
            }
        }
        try {
            if (apply) {
                try (var statement = connection.createStatement()) {
                    statement.execute("""
                        CREATE TABLE IF NOT EXISTS teaneko_schema_history (
                          version VARCHAR(120) PRIMARY KEY,
                          checksum CHAR(64) NOT NULL,
                          installed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
                }
            }
            List<Path> migrations;
            try (var files = Files.list(directory)) {
                migrations = files.filter(path -> path.getFileName().toString().matches("V[0-9]+__[a-z_]+\\.sql"))
                    .sorted().toList();
            }
            if (migrations.isEmpty()) {
                throw new IllegalArgumentException("迁移目录没有版本脚本。");
            }
            // 2026/11/07：旧部署脚本不能把更新的数据库误判为兼容，回退须使用配套备份。
            var known = migrations.stream().map(path -> path.getFileName().toString()).toList();
            try (var statement = connection.createStatement();
                 var versions = statement.executeQuery("SELECT version FROM teaneko_schema_history")) {
                while (versions.next()) {
                    if (!known.contains(versions.getString(1))) {
                        throw new IllegalStateException("数据库包含当前部署不认识的迁移版本，拒绝继续。");
                    }
                }
            }
            for (var migration : migrations) {
                String version = migration.getFileName().toString();
                String sql = Files.readString(migration, StandardCharsets.UTF_8).replace("\r\n", "\n");
                String checksum = digest(sql);
                try (var select = connection.prepareStatement(
                    "SELECT checksum FROM teaneko_schema_history WHERE version=?")) {
                    select.setString(1, version);
                    try (var result = select.executeQuery()) {
                        if (result.next()) {
                            if (!checksum.equals(result.getString(1))) {
                                throw new IllegalStateException("已执行迁移的内容发生改变：" + version);
                            }
                            continue;
                        }
                    }
                }
                if (!apply) {
                    throw new IllegalStateException("数据库缺少迁移：" + version);
                }
                for (var sqlStatement : statements(sql)) {
                    try (var statement = connection.createStatement()) {
                        statement.execute(sqlStatement);
                    }
                }
                try (var insert = connection.prepareStatement(
                    "INSERT INTO teaneko_schema_history(version,checksum) VALUES (?,?)")) {
                    insert.setString(1, version);
                    insert.setString(2, checksum);
                    insert.executeUpdate();
                }
                System.out.println("已应用：" + version);
            }
            for (var sql : statements(Files.readString(directory.resolve("verify.sql")))) {
                try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                    if (result.next()) {
                        throw new IllegalStateException("结构核验失败：" + result.getString(1));
                    }
                }
            }
            System.out.println("迁移校验值与数据库结构核验通过。");
        } finally {
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, lock);
                statement.execute();
            }
        }
    }

    /**
     * 显式分隔每条语句，避免按字符串内的分号切割；脚本不使用客户端 DELIMITER。
     *
     * @param script 脚本内容
     * @return 可逐条执行的语句
     */
    private static List<String> statements(String script) {
        return java.util.Arrays.stream(script.split("(?m)^-- @statement\\s*$"))
            .map(String::strip).filter(value -> !value.isEmpty()).toList();
    }

    /**
     * @param text 已统一换行的脚本或锁名称
     * @return SHA-256 十六进制校验值
     * @throws Exception 当前 JDK 无 SHA-256 实现时
     */
    private static String digest(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * @param name 必需环境变量名
     * @return 未打印的配置值
     * @throws IllegalArgumentException 当配置未提供时
     */
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少迁移环境变量：" + name);
        }
        return value;
    }
}
