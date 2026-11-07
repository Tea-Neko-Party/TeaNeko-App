-- 2026/11/06：创建 general_data 基线；既有表保持原数据与自增序号。
CREATE TABLE IF NOT EXISTS general_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    namespace VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    o_key VARCHAR(255) NOT NULL,
    o_value LONGTEXT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_general_data_identity (namespace, target, o_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- @statement
-- 2026/11/06：创建 command_data 基线；既有表保持原数据与自增序号。
CREATE TABLE IF NOT EXISTS command_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    namespace VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    o_key VARCHAR(255) NOT NULL,
    o_value LONGTEXT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_command_data_identity (namespace, target, o_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- @statement
-- 2026/11/06：创建 debug_data 基线；既有表保持原数据与自增序号。
CREATE TABLE IF NOT EXISTS debug_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    namespace VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    o_key VARCHAR(255) NOT NULL,
    o_value LONGTEXT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_debug_data_identity (namespace, target, o_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- @statement
-- 2026/11/06：创建 cleanable_data 基线；既有表保持原数据与自增序号。
CREATE TABLE IF NOT EXISTS cleanable_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    namespace VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    o_key VARCHAR(255) NOT NULL,
    o_value LONGTEXT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cleanable_data_identity (namespace, target, o_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- @statement
-- 2026/11/06：创建 tea_neko_agent_data 基线；既有表保持原数据与自增序号。
CREATE TABLE IF NOT EXISTS tea_neko_agent_data (
    id BIGINT NOT NULL AUTO_INCREMENT,
    namespace VARCHAR(255) NOT NULL,
    target VARCHAR(255) NOT NULL,
    o_key VARCHAR(255) NOT NULL,
    o_value LONGTEXT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tea_neko_agent_data_identity (namespace, target, o_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- @statement
-- 2026/11/06：物品 UUID 使用 Hibernate MySQL 的 BINARY(16) 表达。
CREATE TABLE IF NOT EXISTS item_data (
    uuid BINARY(16) NOT NULL,
    owner_id BINARY(16) NOT NULL,
    namespace VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    metadata_type VARCHAR(255),
    metadata MEDIUMTEXT,
    count INT NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (uuid),
    UNIQUE KEY uk_item_data_identity (owner_id, namespace, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
