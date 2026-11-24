# RBAC3 分支推送指南

> 目标仓库：<https://github.com/DZDCR/personalized-resource-generator-agent>
> 目标分支：`RBAC`（远程已存在，包含“实时聊天”提交）
> 建议提交：`feat(auth): 新增 RBAC3 身份认证与权限管理模块`

> 注意：远程 `RBAC` 已有历史提交，本地 `RBAC` 已合并 `origin/RBAC`，应使用普通 push；
> **不要 force push**，否则会覆盖远程实时聊天功能。

## 1. 推送前检查

```bash
cd personalized-resource-generator-agent

git status
git diff --stat
```

确认只包含以下内容（`target/`、`node_modules/`、`dist/` 已被 `.gitignore` 忽略）：

```text
backend/pom.xml
backend/src/main/java/com/example/learning/auth/**
backend/src/main/java/com/example/rbacdemo/**
backend/src/main/resources/application.yml
backend/src/main/resources/application-rbac-demo.yml
backend/src/test/java/com/example/learning/auth/**
backend/src/test/resources/**
frontend/index.html
frontend/vite.config.js
frontend/src/App.vue
frontend/src/main.js
frontend/src/router/index.js
frontend/src/api/**
frontend/src/stores/authStore.js
frontend/src/views/auth/**
frontend/src/views/admin/**
frontend/src/styles/global.css
docs/rbac3-design.md
docs/rbac3-branch-push.md
docs/data-dictionary.md
docs/screenshots/rbac/**
README.md
```

## 2. 准备并合并目标分支

```bash
git fetch origin
git switch RBAC                 # 本地已存在则直接切换
git merge origin/RBAC --no-edit # 保留远程已有的实时聊天提交
```

当前本地 `RBAC` 已完成上述合并，直接推送即可。若远程 `RBAC` 在推送前又有新提交，
先 `git pull --rebase origin RBAC`，解决冲突并重新测试后再推送。

## 3. 提交

```bash
git add backend/pom.xml backend/src frontend/src frontend/index.html frontend/vite.config.js \
        docs README.md

git commit -m "feat(auth): 新增 RBAC3 身份认证与权限管理模块" \
           -m "- 4 种登录：账号密码 / 邮箱验证码 / 短信验证码 / OIDC 授权码+PKCE
- PBKDF2 慢哈希 + 每用户盐 + 统一失败提示 + 账号锁定
- RBAC0 授予 + RBAC1 闭包继承 + RBAC2 SSD/DSD/基数/前置角色
- Spring Security JWT + 刷新令牌轮换 + 审计日志
- Vue3 登录/注册/找回密码/权限中心 5 个管理页
- 补充 HSQLDB 文件库独立演示 profile 与 7 个集成测试"
```

按需拆分提交（推荐小步提交）：

```bash
git add backend/pom.xml backend/src/main/java/com/example/learning/auth backend/src/main/resources
git commit -m "feat(auth): 实现 Spring Security 登录与 RBAC3 后端"

git add backend/src/test backend/src/main/resources/application-rbac-demo.yml
git commit -m "test(auth): 覆盖登录、DSD、SSD、刷新令牌与注册找回"

git add frontend docs README.md
git commit -m "feat(auth): 新增登录/注册/权限中心前端与设计文档"
```

## 4. 推送（HTTPS + Personal Access Token）

GitHub 已不再接受账号密码，HTTPS 推送使用 **PAT**（Fine-grained token 或 Classic token）：

```bash
git remote -v
# origin  https://github.com/DZDCR/personalized-resource-generator-agent.git

git push -u origin RBAC
```

首次推送会弹出凭据窗口：

- 用户名：GitHub 用户名 `DZDCR`（或你的账号）
- 密码：粘贴 PAT（不要粘贴 GitHub 登录密码）

也可以把 token 写进远端 URL（注意不要提交到仓库）：

```bash
git remote set-url origin https://<TOKEN>@github.com/DZDCR/personalized-resource-generator-agent.git
git push -u origin RBAC
# 推送后建议改回无 token 的 URL
git remote set-url origin https://github.com/DZDCR/personalized-resource-generator-agent.git
```

## 5. 推送（SSH，推荐长期使用）

```bash
ssh-keygen -t ed25519 -C "your_email@example.com"
# 把 ~/.ssh/id_ed25519.pub 添加到 GitHub → Settings → SSH and GPG keys

git remote set-url origin git@github.com:DZDCR/personalized-resource-generator-agent.git
git push -u origin RBAC
```

## 6. 没有仓库写权限时：Fork + PR

如果 `git push` 返回 `403`，说明你不在该仓库协作者中。改用 Fork：

1. 打开仓库页面，点击 **Fork**，得到 `https://github.com/<你的账号>/personalized-resource-generator-agent`。
2. 添加 fork 远端：

```bash
git remote add fork https://github.com/<你的账号>/personalized-resource-generator-agent.git
git push -u fork RBAC
```

3. 打开 GitHub 页面，从 `<你的账号>:RBAC` 向 `DZDCR:main` 发起 Pull Request。
4. PR 描述中附上：

```text
变更内容：新增 RBAC3 身份认证与权限管理模块
测试方式：
  cd backend && mvn -Dtest=AuthFlowIntegrationTest test
  cd frontend && npm run build
关联要求：登录流程 / 4 种登录方式 / 图形验证码 / 注册与找回密码 / 鉴权授权用框架
```

## 7. 推送后验证

```bash
git log --oneline -3
git show --stat HEAD
git ls-remote --heads origin RBAC
```

打开 GitHub 分支页面：

```text
https://github.com/DZDCR/personalized-resource-generator-agent/tree/RBAC
```

## 8. 常见问题

| 问题 | 处理 |
| --- | --- |
| `Authentication failed` | 使用 PAT，不是 GitHub 登录密码；检查 token 是否勾选 `repo` 权限 |
| `remote: Permission to ... denied` | 当前账号无写权限，走 Fork + PR |
| `Updates were rejected` | `git pull --rebase origin main` 后重新 push；不要 force push 覆盖他人提交 |
| 误提交 `target/` / `node_modules/` | `git rm -r --cached target node_modules dist` 后重新提交，确认 `.gitignore` 生效 |
| 想丢弃本地提交 | `git reset --soft HEAD~1` 后重新组织提交（不要影响远程分支） |
| 分支名写错 | `git branch -m RBAC`，已推送则删除远程旧分支再推新分支 |

## 9. 当前仓库建议

- 分支：`RBAC`
- 基线：`main`
- 合并方式：Squash & Merge
- 合并前必须通过：
  - `cd backend && mvn -Dtest=AuthFlowIntegrationTest test`
  - `cd frontend && npm run build`
- 生产部署前必须覆盖 `AUTH_JWT_SECRET`、`AUTH_SSO_CLIENT_SECRET`，关闭 `AUTH_DEV_MODE`，并设置 `AUTH_SECURE_COOKIE=true`。
