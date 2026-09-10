# 灵感工坊（Inspiration Forge）

灵感工坊是一个面向网页应用创作的 AI 生成平台。用户可以用自然语言描述需求，完成应用创建、流式生成、实时预览、代码编辑、版本管理、部署与下载。

## 核心能力

- 支持原生 HTML、多文件项目和 Vue 项目生成模式
- AI 流式生成，按文件实时展示生成进度
- 生成计划、模型路由、任务恢复与失败重试
- 实时网页预览、可视化元素编辑和 Monaco 代码编辑
- 文件差异比较、版本回滚和单文件恢复
- 应用部署、源码下载与封面截图
- 用户、应用、对话记录及后台管理
- 运行时数据模型、数据记录、会话和角色权限
- RAG 知识库、文档切分与向量检索

## 技术栈

后端：Java 21、Spring Boot、MyBatis-Flex、MySQL、Redis、LangChain4j、LangGraph4j。

前端：Vue 3、TypeScript、Vite、Pinia、Ant Design Vue、Monaco Editor。

## 项目结构

```text
inspiration-forge/
├── src/                            # Spring Boot 后端
├── inspiration-forge-frontend/     # Vue 前端
├── sql/                            # 数据库初始化脚本
├── deploy/                         # Docker、OpenResty 与迁移工具
└── docs/                           # 项目改进记录
```

## 本地运行

### 1. 初始化依赖服务

准备 MySQL 8 和 Redis，并执行：

```sql
source sql/schema_full.sql;
```

默认数据库名为 `inspiration_forge`。

### 2. 配置环境变量

按实际启用的功能设置以下变量：

```dotenv
MYSQL_PASSWORD=
REDIS_PASSWORD=
DEEPSEEK_API_KEY=
DASHSCOPE_API_KEY=
CODE_MODEL_NAME=deepseek-chat
TENCENT_COS_SECRET_ID=
TENCENT_COS_SECRET_KEY=
TENCENT_COS_HOST=
TENCENT_COS_REGION=
TENCENT_COS_BUCKET=
PEXELS_API_KEY=
RAG_EMBEDDING_ENABLED=false
RAG_EMBEDDING_API_KEY=
USER_PASSWORD_SALT=inspiration-forge
```

所有密钥都通过环境变量注入，不应提交真实值。可在被 Git 忽略的 `src/main/resources/application-local.yml` 中保存本机覆盖配置。

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

Windows：

```powershell
.\mvnw.cmd spring-boot:run
```

后端默认地址为 `http://localhost:8123/api`。

### 4. 启动前端

```bash
cd inspiration-forge-frontend
npm install
npm run dev
```

## 构建与测试

```bash
./mvnw -DskipTests package

cd inspiration-forge-frontend
npm run test:covers
npm run test:clipboard
npm run build
```

生产部署可参考 `deploy/DEPLOY.md`。

## 安全说明

- 仓库只保留环境变量占位符，不包含生产数据库密码或第三方 API 密钥。
- `application-local.yml`、`application-prod.yml`、`deploy/.env`、构建产物和运行数据均已加入忽略列表。
- 公开仓库前仍应检查提交历史，避免误提交本地凭据。

## 维护者

[1dashboard](https://github.com/1dashboard)
