# RAG 记忆增强实施说明

## 目标

本次实现面向 AI 应用生成场景，不照搬通用企业知识库。核心目标是让模型在修改应用时能够找回：

- 当前应用已经生成的代码事实；
- 用户过去明确提出的需求和约束；
- AI 在历史对话中给出的关键结论；
- 与当前请求最相关的文件，而不是把整个项目无差别塞进上下文。

## 用户开关

RAG 是应用级可选能力，默认关闭。用户可以在首页创建应用时选择是否开启，也可以在应用对话页顶部随时切换。生成任务运行期间禁止切换，以保证单次任务上下文一致。

- 开启：自动索引已有源码，后续对话持续写入长期记忆并在生成前检索；
- 关闭：立即停止索引、写入、检索和 Prompt 注入；
- 重新开启：复用已有知识并在缺少代码索引时自动补建；
- 删除应用：无论开关状态如何，RAG 数据都会随应用一起清理。

## 调用链

```text
用户指令
  -> 检查当前应用是否已有代码索引
  -> Query 分词（英文词 + 中文 2~4 gram）
  -> 关键词召回
  -> 可选 DashScope Embedding 语义召回
  -> RRF 排名融合
  -> 截取 Top 6 相关片段
  -> 以 <project_memory> 注入原始指令末尾
  -> 原有 LangChain4j 代码生成与工具调用
  -> 生成成功后重建代码索引
  -> 用户消息和 AI 结论持续写入长期记忆
```

## 数据模型

- `rag_knowledge_base`：按 `appId` 隔离的应用知识库；
- `rag_document`：代码文件、聊天记录等原始文档及来源元数据；
- `rag_chunk`：结构化切片、内容哈希、可选 Embedding 向量。

Flyway 会在启动时执行 `V3__create_rag_memory_tables.sql`。应用删除时，这三类数据会与代码、版本、聊天记录一起物理清理。

## 索引策略

- 支持 Java、Vue、TypeScript、JavaScript、HTML、CSS、JSON、Markdown、SQL 等文本文件；
- 单文件最大 300 KB，单项目最多索引 300 个文件，避免误读依赖和构建产物；
- 每片约 1800 字符，保留 180 字符重叠，尽量在换行边界切分；
- 通过 SHA-256 去重；
- 生成成功后自动重建 `CODE` 来源索引；
- 老项目首次继续生成时自动补建索引，无需用户操作；
- 新增聊天消息自动写入 `CHAT` 来源，形成长期记忆。

## 检索策略

默认使用本地关键词检索，不依赖额外服务即可运行。启用 Embedding 后，同时执行语义召回，并用 RRF 合并两路排名。Embedding 调用失败时自动退化为关键词检索，不阻断代码生成。

```powershell
$env:RAG_EMBEDDING_ENABLED="true"
$env:RAG_EMBEDDING_API_KEY="你的 DashScope Key"
$env:RAG_EMBEDDING_MODEL="text-embedding-v3"
$env:RAG_EMBEDDING_DIMENSION="1024"
```

密钥只通过环境变量注入，不应提交到仓库。

## 验证接口

接口只允许应用创建者访问：

- `POST /api/rag/reindex?appId={appId}`：手动重建已有项目代码索引；
- `GET /api/rag/search?appId={appId}&query={query}&limit=6`：查看召回片段及融合分数。

正常使用时不需要操作这两个接口。它们主要用于开发调试和检索质量评估。

## 与参考方案的对应关系

已实现：文档 ETL、结构化切片、应用级数据隔离、关键词/向量混合检索、RRF 融合、Prompt 注入、短期 Redis 记忆与长期 RAG 记忆分层、异常降级、自动增量沉淀。

后续可继续增强：独立 Qdrant/Elasticsearch 存储、大模型 Query Planning、专用 Rerank 模型、会话 compact summary、检索效果评测集和命中观测面板。当前接口已经把 Embedding 和知识服务抽象隔离，替换存储后端不需要改代码生成主链路。
