-- HSQLDB 演示库兼容学习业务实体的 PostgreSQL jsonb 列。
-- 仅用于 rbac-demo profile；存在时由 spring.sql.init.continue-on-error 忽略重复错误。
CREATE DOMAIN jsonb AS VARCHAR(1048576);
