# Git 协作规范

## 1. 分支模型

采用 **主干开发 + 短特征分支**（轻量 Trunk-Based）：

```
main（受保护，禁止直接 push）
  └─ feat/xxx      功能分支：从 main 切出，合并用 PR
  └─ fix/xxx       修复分支
  └─ docs/xxx      文档分支
```

- 分支命名：`type/描述`，type ∈ feat/fix/docs/refactor/chore。
- 生命周期：分支应在 1~3 个工作日内合并回 main，长期分支需同步 main。
- 红线：**绝不直接提交到 main**。

## 2. 提交信息规范

采用 Conventional Commits：

```
<type>(<scope>): <subject>

<body（可选，说明为什么）>
```

| type | 含义 |
|------|------|
| feat | 新功能 |
| fix | 修复 bug |
| docs | 仅文档 |
| refactor | 重构（不改行为） |
| test | 测试 |
| chore | 构建/配置/依赖 |

示例：

```
feat(agent): 新增 CoderAgent 代码案例生成

- 支持 python/java 双语言
- 失败时返回空 Optional 由编排层兜底
- 关联资源表新增 code 类型枚举

close #12
```

## 3. 提交准则

- 单次提交只做一件事，粒度小、可回滚。
- 禁止提交：`target/`、`node_modules/`、`dist/`、日志、`.env`、密钥。（已由 .gitignore 兜底）
- 提交前必须 `git diff` 自查，禁止 `commit -a` 盲提交。
- 已完成可编译的任务才允许提交；半成品放新分支。
- 不提交无 `git status` 确认的更改。

## 4. Pull Request 规范

- PR 标题遵循 Conventional Commits。
- PR 描述包含：变更内容、测试方式、关联 issue。
- 分支合入 main 需至少 1 名成员 Review 通过。
- 合并方式：Squash & Merge（保持 main 历史线性）。

## 5. 常用工作流

```bash
# 新功能
git checkout main && git pull
git checkout -b feat/resource-generate
# ...coding & 小步提交...
git push origin feat/resource-generate
# 开 PR → Review → Squash Merge → 删除远程分支
```

## 6. 标签与发布

- 版本号：`v0.1.0`（语义化版本）。
- 达到里程碑打 tag：`git tag -a v0.1.0 -m "多智能体资源生成可用"`。
- 发布日志在 `docs/changelog.md` 汇总。

## 7. 冲突处理

1. 优先 `git pull --rebase` 保持线性。
2. 冲突解决后 `git rebase --continue`，**禁止使用 force push 覆盖他人提交**。
3. 多次 rebase 困难时改为 merge，并向 reviewer 说明理由。