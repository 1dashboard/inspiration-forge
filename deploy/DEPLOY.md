# 1Panel 服务器部署说明

## 架构

```text
浏览器
  -> OpenResty (1Panel 网站, 80/443)
       |  /             -> 前端静态文件 (Vite dist)
       |  /api/         -> 反向代理 -> 后端 jar (127.0.0.1:8123)
       |  /dist/        -> 后端部署目录 (data/code_deploy)
  -> 后端 Spring Boot 容器 (network_mode: host)
       -> MySQL (1Panel 应用商店)
       -> Redis (1Panel 应用商店)
       -> DeepSeek / 腾讯云 COS / DashScope / Pexels
```

后端使用 `network_mode: host`，容器内的 `127.0.0.1` 就是宿主机，直接连接 1Panel 应用商店映射到宿主机端口的 MySQL 和 Redis。

## 目录约定

服务器上建议统一放到 `/opt/inspiration-forge`：

```text
/opt/inspiration-forge
  deploy/                     # 本目录
  target/                     # 后端 jar
  frontend/dist/              # 前端构建产物
  data/code_output/           # 生成中的项目（容器自动创建）
  data/code_deploy/           # 已部署作品（容器自动创建）
```

## 一、准备数据库

1. 在 1Panel 应用商店安装 MySQL 8（记录 root 密码）。
2. 打开 1Panel 的“数据库”，新建数据库 `inspiration_forge`（字符集 `utf8mb4`）。
3. 导入 `sql/schema_full.sql`（完整建表，共 15 张表）。可以直接用 1Panel 数据库管理界面执行，或在服务器上运行：

```bash
mysql -uroot -p < /opt/inspiration-forge/sql/schema_full.sql
```

该脚本一次性建好核心业务表、运行时数据表、RBAC 表和 RAG 表。后端容器已通过 `SPRING_FLYWAY_ENABLED=false` 关闭 Flyway 自动迁移，避免重复建表和加列冲突。

## 二、准备 Redis

在 1Panel 应用商店安装 Redis，记录密码。若未设置密码，`REDIS_PASSWORD` 留空即可。

## 三、部署后端

1. 把 `deploy/` 和 `target/inspiration-forge-0.0.1-SNAPSHOT.jar` 上传到 `/opt/inspiration-forge/`。
2. 准备环境变量：

```bash
cd /opt/inspiration-forge/deploy
cp .env.example .env
```

编辑 `.env`，填 MySQL 密码、Redis 密码和作品访问域名：

```dotenv
MYSQL_PASSWORD=change-me
REDIS_PASSWORD=change-me
DEPLOY_HOST=http://你的服务器IP或域名/dist
```

3. 构建并启动：

```bash
cd /opt/inspiration-forge/deploy
docker compose up -d --build
```

4. 查看日志确认启动成功：

```bash
docker compose logs -f backend
```

看到 Spring Boot 启动完成、Flyway 迁移成功、端口 8123 监听即可。

## 四、部署前端

1. 把本地 `inspiration-forge-frontend/dist/` 上传到 `/opt/inspiration-forge/frontend/dist/`。
2. 在 1Panel 创建网站（静态网站），站点目录指向 `/opt/inspiration-forge/frontend/dist`。
3. 为该网站添加反向代理，或在 OpenResty 配置里应用 `deploy/nginx.conf` 的规则：

   - `/` 指向前端 dist
   - `/api/` 反向代理到 `127.0.0.1:8123`
   - `/dist/` 指向 `/opt/inspiration-forge/data/code_deploy/`

关键点是 `/api/` 必须关闭缓冲并设置长超时，否则 AI 流式输出会卡住。

## 五、验证

- 首页：`http://你的IP/`
- 健康检查：`http://你的IP/api/health/`
- 后端文档：`http://你的IP/api/doc.html`

## 六、2 核 2G 内存优化

- JVM 已在 Dockerfile 里限制为 `-Xmx512m`，不要随意调大。
- MySQL 建议把 `innodb_buffer_pool_size` 调到 128M 左右。
- 生成封面截图会临时启动无头浏览器，内存紧张时可能失败，但不影响主流程。

## 七、密钥说明

数据库、Redis、AI 模型、对象存储和图片搜索配置均通过环境变量注入。复制 `deploy/.env.example` 为 `deploy/.env` 后填写真实值；`.env` 已被 Git 忽略，不要把生产密钥提交到仓库。

`USER_PASSWORD_SALT` 在首次部署后必须保持不变。更改该值会导致已有用户的密码摘要无法匹配。
