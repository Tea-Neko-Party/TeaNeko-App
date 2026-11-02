package org.zexnocs.teanekoagent.memory.fact;

import jakarta.persistence.LockModeType;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 离线验证 Agent 专用 JPA 映射与其他入口实体共存，以及当前 Hibernate 对实际锁查询的解析。
 * 不建立 JDBC 连接，不执行 DDL 或宣称已完成 MySQL 行锁实机验收。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryFactJpaMappingTest {
    /**
     * 禁用 JDBC 元数据和 DDL，仅构建当前 ORM 元模型并解析实际仓库查询。
     * @throws Exception 当仓库查询声明无法读取时
     */
    @Test void mappingsAndQueriesValidateWithoutJdbcConnection() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(GeneralEasyDataObject.class).addAnnotatedClass(TeaNekoAgentEasyDataObject.class).buildMetadata();
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                for (var method : BaseEasyDataRepository.class.getDeclaredMethods()) {
                    var query = method.getAnnotation(Query.class);
                    if (query == null) { continue; }
                    assertEquals(Propagation.MANDATORY, Objects.requireNonNull(method.getAnnotation(Transactional.class)).propagation());
                    assertNotNull(session.createQuery(query.value().replace("#{#entityName}", "TeaNekoAgentEasyDataObject"), TeaNekoAgentEasyDataObject.class));
                    if (method.getName().contains("ForRead")) { assertEquals(LockModeType.PESSIMISTIC_READ, Objects.requireNonNull(method.getAnnotation(Lock.class)).value()); }
                    if (method.getName().contains("ForWrite")) { assertEquals(LockModeType.PESSIMISTIC_WRITE, Objects.requireNonNull(method.getAnnotation(Lock.class)).value()); }
                }
                var page = BaseEasyDataRepository.class.getMethod("findByNamespaceAndTargetAndIdLessThanOrderByIdDesc", String.class, String.class, long.class, Pageable.class);
                assertDoesNotThrow(() -> new PartTree(page.getName(), TeaNekoAgentEasyDataObject.class));
                assertNotNull(factory.getMetamodel().entity(TeaNekoAgentEasyDataObject.class));
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
