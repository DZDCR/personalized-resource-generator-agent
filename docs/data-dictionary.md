# 数据字典

> 与 `backend/.../entity/*.java` 一一对应。改表结构必须同步本文件 + Entity。

## 通用约定

- 所有表主键 `id BIGSERIAL`。
- 时间字段：`created_at TIMESTAMPTZ`（UTC 存储，展示时转本地）。
- 扩展数据：JSON 用 `jsonb` 列。
- 外键在 Java 侧以逻辑关联（`studentId`）表达，不强制 DB 外键约束（便于分库）。

## 1. students 学生表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | 自增主键 |
| student_no | varchar(64) | Y | 学号，唯一 |
| name | varchar(64) | N | 姓名 |
| major | varchar(128) | N | 专业 |
| grade | varchar(32) | N | 年级 |
| created_at | timestamptz | Y | 创建时间 |
| updated_at | timestamptz | Y | 更新时间 |

## 2. student_profiles 学生画像表（版本化）

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| student_id | bigint | Y | 关联 students.id |
| profile_json | jsonb | Y | 画像全文（6+ 维度，见架构-5） |
| version | int | Y | 版本号，每次更新 +1 |
| confidence | numeric | Y | 置信度 0~1 |
| created_at | timestamptz | Y | |

查询约定：取 `max(version)` 行作为当前画像。

## 3. resources 资源表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| type | varchar(32) | Y | DOC/MINDMAP/QUIZ/READING/VIDEO_SCRIPT/CODE |
| topic | varchar(128) | Y | 主题 |
| difficulty | varchar(16) | N | easy/medium/hard |
| student_id | bigint | N | 归属学生；NULL 为公共资源 |
| content_json | jsonb | Y | 各类 Agent 结构化产物 |
| vector_id | varchar(64) | N | Qdrant 向量 id |
| meta_json | jsonb | N | 知识点清单/预估时长等 |
| task_id | varchar(64) | N | 生成任务号（幂等去重） |
| created_at | timestamptz | Y | |

## 4. learning_paths 学习路径表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| student_id | bigint | Y | |
| target_topic | varchar(128) | N | 目标知识点 |
| plan_json | jsonb | Y | 路径全量（节点数组） |
| plan_version | int | N | 方案版本 |
| status | varchar(32) | N | IN_PROGRESS/COMPLETED/ARCHIVED |
| created_at / updated_at | timestamptz | Y | |

## 5. path_nodes 路径节点表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| path_id | bigint | Y | 关联 learning_paths.id |
| order_index | int | N | 顺序号 |
| topic | varchar(128) | N | 知识点 |
| resource_ids | jsonb | N | 推荐资源 id 数组 |
| status | varchar(16) | N | LOCKED/AVAILABLE/DONE |
| score | numeric | N | 检查点得分 |
| started_at / finished_at | timestamptz | N | |

节点流转：`LOCKED → AVAILABLE（前序 DONE 且达标）→ DONE（检查点 ≥0.8）`。

## 6. quiz_records 测试记录表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| student_id | bigint | Y | |
| resource_id | bigint | N | 关联 resources.id |
| topic | varchar(128) | N | 冗余主题，便于统计 |
| score | numeric | N | 0~100 |
| answers_json | jsonb | N | 学生答案 |
| duration_sec | int | N | 用时 |
| created_at | timestamptz | Y | |

## 7. learning_events 学习行为日志表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| student_id | bigint | Y | |
| event_type | varchar(64) | N | 见 API 规范-9 |
| payload_json | jsonb | N | 事件附属数据（时长、资源id等） |
| created_at | timestamptz | Y | |

数据量策略：按月分表（`learning_events_202609`），评估仅查近 30 天。

## 8. tutor_sessions 辅导会话表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| student_id | bigint | Y | |
| messages_json | jsonb | Y | 对话消息数组 |
| resolved | boolean | N | 是否已解决 |
| created_at / updated_at | timestamptz | Y | |

## 9. assessments 评估记录表

| 列 | 类型 | 必填 | 说明 |
|----|------|------|------|
| id | bigint | PK | |
| student_id | bigint | Y | |
| period | varchar(16) | N | 周号，如 2026-W37 |
| score_total | numeric | N | 综合得分 |
| report_json | jsonb | N | 维度得分 + LLM 报告 + 调整指令 |
| created_at | timestamptz | Y | |

## 10. 索引与约束清单（建议）

| 表 | 索引/约束 | 用途 |
|----|----------|------|
| students | UNIQUE(student_no) | 学号唯一 |
| student_profiles | idx(student_id, version desc) | 取最新画像 |
| resources | idx(student_id, type) | 学生资源列表 |
| path_nodes | idx(path_id, order_index) | 路径顺序读取 |
| learning_events | idx(student_id, created_at) | 行为统计 |
| quiz_records | idx(student_id, created_at) | 评分趋势 |