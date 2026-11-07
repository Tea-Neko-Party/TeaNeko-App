-- 2026/11/06：补齐早期数据库可能缺少的维护索引；已存在时不重建。
SET @teaneko_index_ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND index_name='idx_agent_easy_recent'
), 'DO 0', 'ALTER TABLE tea_neko_agent_data ADD INDEX idx_agent_easy_recent (namespace,target,id)');
-- @statement
PREPARE teaneko_index_statement FROM @teaneko_index_ddl;
-- @statement
EXECUTE teaneko_index_statement;
-- @statement
DEALLOCATE PREPARE teaneko_index_statement;
-- @statement
-- 2026/11/06：补齐早期数据库可能缺少的维护索引；已存在时不重建。
SET @teaneko_index_ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='tea_neko_agent_data' AND index_name='idx_agent_easy_maintenance'
), 'DO 0', 'ALTER TABLE tea_neko_agent_data ADD INDEX idx_agent_easy_maintenance (namespace,id)');
-- @statement
PREPARE teaneko_index_statement FROM @teaneko_index_ddl;
-- @statement
EXECUTE teaneko_index_statement;
-- @statement
DEALLOCATE PREPARE teaneko_index_statement;
-- @statement
-- 2026/11/06：补齐早期数据库可能缺少的维护索引；已存在时不重建。
SET @teaneko_index_ddl = IF(EXISTS(
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='cleanable_data' AND index_name='idx_cleanable_easy_maintenance'
), 'DO 0', 'ALTER TABLE cleanable_data ADD INDEX idx_cleanable_easy_maintenance (namespace,id)');
-- @statement
PREPARE teaneko_index_statement FROM @teaneko_index_ddl;
-- @statement
EXECUTE teaneko_index_statement;
-- @statement
DEALLOCATE PREPARE teaneko_index_statement;
