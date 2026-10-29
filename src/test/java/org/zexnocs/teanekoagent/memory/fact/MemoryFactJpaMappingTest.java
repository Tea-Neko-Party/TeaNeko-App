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
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 离线验证新增 JPA 映射与旧表实体共存，以及当前 Hibernate 对实际锁查询的解析。
 * 不建立 JDBC 连接，不执行 DDL 或宣称已完成 MySQL 行锁实机验收。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class MemoryFactJpaMappingTest {
    /**
     * 禁用 JDBC 元数据和 DDL，仅构建当前 ORM 元模型并解析实际仓库查询。
     * @throws Exception 当兼容类或仓库查询声明无法读取时
     */
    @Test void mappingsAndQueriesValidateWithoutJdbcConnection() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .applySetting("hibernate.connection.provider_class", "org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
                .build();
        try {
            var metadata = new MetadataSources(registry).addAnnotatedClass(GeneralEasyDataObject.class).addAnnotatedClass(MemoryFactDataObject.class)
                    .addAnnotatedClass(Class.forName("org.zexnocs.teanekoagent_old.database.LLMRelatedEasyDataObject")).buildMetadata();
            try (var factory = metadata.buildSessionFactory(); var session = factory.openSession()) {
                var method = MemoryNamespaceWriteRepository.class.getMethod("lockNamespace", String.class);
                var query = Objects.requireNonNull(method.getAnnotation(Query.class));
                assertEquals(LockModeType.PESSIMISTIC_WRITE, Objects.requireNonNull(method.getAnnotation(Lock.class)).value());
                assertEquals(Propagation.MANDATORY, Objects.requireNonNull(method.getAnnotation(Transactional.class)).propagation());
                assertNotNull(session.createQuery(query.value(), GeneralEasyDataObject.class));
                var snapshotLock = MemoryNamespaceWriteRepository.class.getMethod("lockSnapshot", String.class);
                assertEquals(LockModeType.PESSIMISTIC_READ, Objects.requireNonNull(snapshotLock.getAnnotation(Lock.class)).value());
                assertEquals(Propagation.MANDATORY, Objects.requireNonNull(snapshotLock.getAnnotation(Transactional.class)).propagation());
                assertNotNull(session.createQuery(Objects.requireNonNull(snapshotLock.getAnnotation(Query.class)).value(), GeneralEasyDataObject.class));
                var legacyLock = MemoryFactRepository.class.getMethod("lockLegacyProfile", String.class, String.class);
                assertEquals(LockModeType.PESSIMISTIC_WRITE, Objects.requireNonNull(legacyLock.getAnnotation(Lock.class)).value());
                assertEquals(Propagation.MANDATORY, Objects.requireNonNull(legacyLock.getAnnotation(Transactional.class)).propagation());
                assertNotNull(session.createQuery(Objects.requireNonNull(legacyLock.getAnnotation(Query.class)).value(), MemoryFactDataObject.class));
                var page = MemoryFactRepository.class.getMethod("findByNamespaceAndTargetAndKeyGreaterThanOrderByKeyAsc",
                        String.class, String.class, String.class, Pageable.class);
                assertDoesNotThrow(() -> new PartTree(page.getName(), MemoryFactDataObject.class));
                assertNotNull(factory.getMetamodel().entity(MemoryFactDataObject.class));
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
