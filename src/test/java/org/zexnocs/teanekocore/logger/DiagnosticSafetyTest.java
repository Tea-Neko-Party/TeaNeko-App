package org.zexnocs.teanekocore.logger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.zexnocs.teanekocore.utils.ExceptionUtils;
import java.sql.SQLSyntaxErrorException;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 安全诊断保留根因定位，而不是复制第三方原文。
 * <br>2026/11/06：覆盖数据库子类、脱敏快照和资源关闭异常。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
class DiagnosticSafetyTest {
    /** SQL 子类保留错误码，异常正文和 suppressed 中的密钥不能进入日志或报告。 */
    @Test
    void preservesDatabaseCodesWithoutSourceMessages() {
        var sql = new SQLSyntaxErrorException("secret-password and private SQL", "42000", 1064);
        var wrapped = new DataIntegrityViolationException("secret-password", sql);
        wrapped.addSuppressed(new IllegalStateException("secret-password"));
        var diagnostic = FailureDiagnostic.from(wrapped);
        assertTrue(diagnostic.description().contains("42000"));
        assertTrue(diagnostic.description().contains("1064"));
        var report = ExceptionUtils.instance.buildExceptionMessage(wrapped);
        assertTrue(report.contains(SQLSyntaxErrorException.class.getName()));
        assertTrue(report.contains("\n    at "));
        assertFalse(report.contains("secret-password"));
        assertFalse(report.contains("private SQL"));
        var safe = new DiagnosticException("目录读取失败。", wrapped);
        assertNull(safe.getCause());
        assertEquals(diagnostic.location(), FailureDiagnostic.from(safe).location());
        assertTrue(FailureDiagnostic.from(safe).description().contains("42000"));
    }
}
