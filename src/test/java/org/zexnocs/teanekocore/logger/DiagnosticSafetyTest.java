package org.zexnocs.teanekocore.logger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.zexnocs.teanekocore.actuator.execution.BudgetExceededException;
import org.zexnocs.teanekocore.actuator.execution.BudgetKey;
import org.zexnocs.teanekocore.utils.ExceptionUtils;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.URI;
import java.sql.SQLSyntaxErrorException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安全诊断保留根因定位，而不是复制第三方原文。
 * <br>2026/11/06：覆盖数据库子类、脱敏快照和资源关闭异常。
 * <br>2026/11/10：覆盖 HTTP 请求包装的连接重置与连接拒绝，不泄露凭据或异常原文。
 * <br>2026/11/10：预算诊断跨异步包装保留数字，但隐藏非标准资源标识和包装异常原文。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.9.1-l
 */
class DiagnosticSafetyTest {
    /** 2026/11/10：可公开预算差值与原维度仍可程序化读取，任意键内容不得进入诊断。 */
    @Test
    void budgetDiagnosticsReportNumbersWithoutLeakingArbitraryIdentifiers() {
        var key = new BudgetKey("requests");
        var failure = new BudgetExceededException(key, 3, 3, 1);
        var diagnostic = FailureDiagnostic.from(new CompletionException("private-wrapper", failure));
        assertTrue(diagnostic.description().contains("维度=requests，上限=3，已占用=3，剩余=0，本次需要=1"));
        assertEquals(key, failure.key());
        assertFalse(diagnostic.toString().contains("private-wrapper"));
        var unsafe = new BudgetKey("https://private.test/?token=private-key\n伪造日志");
        var rejected = new BudgetExceededException(unsafe, 10, 12, 1);
        assertEquals(unsafe, rejected.key());
        var safe = FailureDiagnostic.from(rejected);
        assertTrue(safe.description().contains("非标准标识（已隐藏）"));
        assertTrue(safe.description().contains("上限=10，已占用=12，剩余=0，本次需要=1"));
        assertFalse(safe.toString().contains("private-key"));
        assertFalse(safe.toString().contains("伪造日志"));
    }

    /** 2026/11/10：相同请求包装下区分传输结果未知与连接建立失败，并保留安全快照。 */
    @Test
    void distinguishesInterruptedTransportFromConnectionFailureWithoutLeakingSource() {
        var headers = new HttpHeaders();
        headers.setBearerAuth("private-key");
        var uri = URI.create("https://example.test/private-path?credential=private-key");
        var interrupted = new WebClientRequestException(new SocketException("private-key Connection reset"),
                HttpMethod.POST, uri, headers);
        var diagnostic = FailureDiagnostic.from(interrupted);
        assertTrue(diagnostic.description().contains("请求传输中断"));
        assertTrue(diagnostic.description().contains("远端处理结果未知"));
        assertNull(diagnostic.httpStatus());
        assertFalse(diagnostic.toString().contains("private-key"));
        assertFalse(diagnostic.toString().contains("private-path"));
        var safe = new DiagnosticException("请求传输中断。", interrupted);
        assertTrue(FailureDiagnostic.from(safe).description().contains(diagnostic.description()));
        var refused = new WebClientRequestException(new ConnectException("private-key Connection refused"),
                HttpMethod.POST, uri, headers);
        var refusedDiagnostic = FailureDiagnostic.from(refused);
        assertTrue(refusedDiagnostic.description().startsWith("无法连接远端服务"));
        assertFalse(refusedDiagnostic.toString().contains("private-key"));
    }

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
