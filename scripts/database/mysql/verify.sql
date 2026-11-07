-- 2026/11/06：不依赖 Hibernate 生成的索引名称，核对真实唯一列顺序。
SELECT 'general_data 缺少三元唯一键或 InnoDB' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='general_data' AND non_unique=0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,target,o_key'
) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='general_data' AND engine='InnoDB'
);
-- @statement
-- 2026/11/06：不依赖 Hibernate 生成的索引名称，核对真实唯一列顺序。
SELECT 'command_data 缺少三元唯一键或 InnoDB' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='command_data' AND non_unique=0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,target,o_key'
) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='command_data' AND engine='InnoDB'
);
-- @statement
-- 2026/11/06：不依赖 Hibernate 生成的索引名称，核对真实唯一列顺序。
SELECT 'debug_data 缺少三元唯一键或 InnoDB' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='debug_data' AND non_unique=0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,target,o_key'
) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='debug_data' AND engine='InnoDB'
);
-- @statement
-- 2026/11/06：不依赖 Hibernate 生成的索引名称，核对真实唯一列顺序。
SELECT 'cleanable_data 缺少三元唯一键或 InnoDB' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='cleanable_data' AND non_unique=0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,target,o_key'
) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='cleanable_data' AND engine='InnoDB'
);
-- @statement
-- 2026/11/06：不依赖 Hibernate 生成的索引名称，核对真实唯一列顺序。
SELECT 'tea_neko_agent_data 缺少三元唯一键或 InnoDB' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND non_unique=0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,target,o_key'
) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND engine='InnoDB'
);
-- @statement
-- 2026/11/06：不依赖 Hibernate 生成的索引名称，核对真实唯一列顺序。
SELECT 'item_data 缺少三元唯一键或 InnoDB' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='item_data' AND non_unique=0
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='owner_id,namespace,type'
) OR NOT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name='item_data' AND engine='InnoDB'
);
-- @statement
SELECT 'idx_agent_easy_recent 缺失或列顺序不符' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND index_name='idx_agent_easy_recent'
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,target,id'
);
-- @statement
SELECT 'idx_agent_easy_maintenance 缺失或列顺序不符' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND index_name='idx_agent_easy_maintenance'
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,id'
);
-- @statement
SELECT 'idx_cleanable_easy_maintenance 缺失或列顺序不符' AS problem
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='cleanable_data' AND index_name='idx_cleanable_easy_maintenance'
    GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='namespace,id'
);

-- @statement
SELECT 'general_data 字段类型、长度或空值约束不符' AS problem
WHERE (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='general_data' AND (
        (column_name='id' AND data_type='bigint' AND is_nullable='NO')
        OR (column_name='namespace' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='target' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_key' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_value' AND data_type='longtext' AND is_nullable='NO')
        OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    )) <> 6;

-- @statement
SELECT 'command_data 字段类型、长度或空值约束不符' AS problem
WHERE (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='command_data' AND (
        (column_name='id' AND data_type='bigint' AND is_nullable='NO')
        OR (column_name='namespace' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='target' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_key' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_value' AND data_type='longtext' AND is_nullable='NO')
        OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    )) <> 6;

-- @statement
SELECT 'debug_data 字段类型、长度或空值约束不符' AS problem
WHERE (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='debug_data' AND (
        (column_name='id' AND data_type='bigint' AND is_nullable='NO')
        OR (column_name='namespace' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='target' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_key' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_value' AND data_type='longtext' AND is_nullable='NO')
        OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    )) <> 6;

-- @statement
SELECT 'cleanable_data 字段类型、长度或空值约束不符' AS problem
WHERE (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='cleanable_data' AND (
        (column_name='id' AND data_type='bigint' AND is_nullable='NO')
        OR (column_name='namespace' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='target' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_key' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_value' AND data_type='longtext' AND is_nullable='NO')
        OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    )) <> 6;

-- @statement
SELECT 'tea_neko_agent_data 字段类型、长度或空值约束不符' AS problem
WHERE (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND (
        (column_name='id' AND data_type='bigint' AND is_nullable='NO')
        OR (column_name='namespace' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='target' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_key' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='o_value' AND data_type='longtext' AND is_nullable='NO')
        OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    )) <> 6;

-- @statement
SELECT 'item_data 字段类型、长度或空值约束不符' AS problem
WHERE (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='item_data' AND (
        (column_name='uuid' AND data_type='binary' AND is_nullable='NO' AND character_maximum_length=16)
        OR (column_name='owner_id' AND data_type='binary' AND is_nullable='NO' AND character_maximum_length=16)
        OR (column_name='namespace' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='type' AND data_type='varchar' AND is_nullable='NO' AND character_maximum_length=255)
        OR (column_name='metadata_type' AND data_type='varchar' AND is_nullable='YES' AND character_maximum_length=255)
        OR (column_name='metadata' AND data_type='mediumtext' AND is_nullable='YES')
        OR (column_name='count' AND data_type='int' AND is_nullable='NO')
        OR (column_name='version' AND data_type='bigint' AND is_nullable='NO')
    )) <> 8;
