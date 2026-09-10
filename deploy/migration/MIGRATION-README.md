# 本地数据整体迁移到现有 1Panel 应用

此包只包含数据库记录及生成网站的数据文件，不包含后端 JAR、平台前端或生产配置。
目标数据库是已有的 `pyhpyh`。用户已选择不保留线上现有数据，但必须先备份。
账号和密码哈希按原样迁移；软删除记录保持原状态，不会自动恢复已删除应用。

## 安全提示

- `01-database.sql` 包含用户信息、密码哈希、聊天内容和业务数据，属于敏感备份。
- 将整个迁移包上传到非网站目录，例如 `/home/project/migration/`，不要上传到前端 `index/`、已发布作品目录或公开下载位置。
- SQL 会删除并重建包中同名表。必须在 1Panel 数据库管理工具里明确选中 `pyhpyh` 后执行，绝不能选中 `mysql` 系统库或其他业务库。
- 包内 SQL 不包含 `CREATE DATABASE` / `DROP DATABASE` / `USE`，避免强制访问本地库名。
- 本地导出不会更改线上数据。本包已通过本机临时数据库实际导入测试；临时验证库在测试后移除，原本地库保持不变。

## 包内文件

| 文件 | 用途 |
| --- | --- |
| `01-database.sql` | 全量结构与业务数据，含 Flyway 记录；应用登录会话表只保留空表结构 |
| `02-fix-version-paths.sql` | 修正 `app_version.sourcePath` 的 Windows 绝对路径 |
| `03-verify.sql` | 对照导出时的各表条数、检查版本路径和主要关联 |
| `tmp/code_output/` | 生成的网站源码和已有构建产物 |
| `tmp/code_deploy/` | 已部署作品，保留原部署标识目录 |
| `tmp/code_versions/` | 代码历史版本 |
| `manifest.json` | 数据条数、文件哈希、本地缺失文件警告及验证结果 |
| `SHA256SUMS` | 可在解压目录执行 `sha256sum -c SHA256SUMS` 校验 |

不包含浏览器配置、临时截图、Redis、API 配置或 Windows `node_modules`。
已有 Vue `dist` 已保留。后续继续修改、重新构建时由服务器重新安装 Linux 依赖，需要可用的 Node/npm。

## 第一步：核实线上后端实际工作目录（还不要停止后端）

JAR 放在哪里不等于进程的工作目录。先在服务器执行：

```bash
pgrep -af 'java.*inspiration-forge'
```

找到实际后端 PID，执行 `readlink -f /proc/实际PID/cwd`。
若 Java 命令包含 `-Duser.dir=...`，还要以该配置实际生效的目录为准；不要直接沿用本地 Windows 路径。
容器部署需核对容器内 `user.dir` 以及宿主机数据卷映射，不能套用以下直接 JAR 示例。

当前包中路径修正暂按用户提供的目录填写：

```text
/opt/1panel/www/sites/inspiration-forge
```

只有核对一致后才可直接用 `02-fix-version-paths.sql`；如不一致，先把其中 `@new_backend_root` 改成真实目录。
同时检查 OpenResty `/dist/` 指向的目录（或其后端代理）与该后端的 `tmp/code_deploy` 一致。
1Panel 的 OpenResty 容器路径和宿主机路径可能不同，不要把它们混用。

## 第二步：停写和备份

1. 先记录当前后端启动方式、工作目录、生产参数和进程管理方式，确保能按原方式启动。
2. 在停机窗口暂停对外写入并停止正确的后端实例（含自动重启管理器），确认没有网站生成任务在运行。
3. 用 1Panel 备份整个线上 `pyhpyh` 数据库，下载或确认备份成功可读取。
4. 备份后端真实工作目录下的 `tmp/code_output`、`tmp/code_deploy`、`tmp/code_versions`。放在非公开备份位置，不要只备份 JAR。
5. 保留备份直到迁移验收通过。任何一步备份失败都不要继续。

## 第三步：导入和复制

1. 先在非公开目录解压本迁移包并检查 `SHA256SUMS`。
2. 在数据库管理工具明确选中 `pyhpyh`，导入 `01-database.sql`。发生错误立即停止，不要勾选“忽略所有错误”。
3. 核对实际工作目录后，在同一数据库执行 `02-fix-version-paths.sql`；最终 Windows 路径计数应为 0。
4. 将已备份的线上旧 `code_output`、`code_deploy`、`code_versions` 移到非公开备份目录，再把包内对应三个文件夹放入后端的 `tmp/` 中。不能仅合并覆盖而留下旧站点文件。
5. 只替换这三个目录；不要替换整个 `tmp`，不要动 `index/`、JAR、`proxy/`、`ssl/` 或生产配置。确保后端运行账号可读写新数据目录。
6. Redis 不恢复本地登录态/缓存。启动时使用一个新的、仅属于本应用的 Spring Session 命名空间（例如通过启动参数 `--spring.session.redis.namespace=inspiration-forge:migrated-20260903`），避免旧线上登录态被继续使用；该参数应保留在后续启动配置中。不要对共用 Redis 执行 `FLUSHALL` / `FLUSHDB`。
7. 按原方式、从原工作目录启动生产后端，保持原数据库、模型和对象存储生产配置；如需在启动命令加命名空间参数，只添加该参数，不要丢失已有生产参数。

本地聊天历史在 MySQL 中，当前代码会从聊天历史加载 AI 上下文；不需要整体复制 Redis。
生成应用自己的登录令牌已通过清空 `runtime_app_session` 失效，所有用户应重新登录。

## 第四步：验收与回滚

- 在 `pyhpyh` 执行 `03-verify.sql`，核对实际条数与期望条数（重启前核对最准确）。
- 健康检查：`curl http://127.0.0.1:8123/api/health/`，应返回 `code: 0`。
- 退出当前线上登录，用原本地账号和密码重新登录，检查应用归属、聊天历史、预览、部署链接、版本查看和恢复。
- 如有 runtime 托管数据，分别核对 PREVIEW / PRODUCTION；本次保持原环境标记，不会把预览数据自动变成生产数据。
- COS 图片保留原 URL，当前本地和生产配置使用同一 COS host；未迁移云桶本体，也未验证每张旧图片是否仍存在。
- 个别生成站点若把数据存在浏览器 localStorage/IndexedDB，或代码硬编码 `localhost`，不能靠此包自动迁移，需逐站点处理。本包迁移的是数据库和服务器文件。
- 先查看 `manifest.json` 的 `warnings`：本地本来就不存在的文件，迁移不能凭空恢复。
- 若失败，停止后端，同时恢复备份的数据库和三个数据目录；不要只回滚其中一部分。恢复原启动参数后再启动。

## 再次导出

先停止本地测试后端，避免同时生成网站或更改数据库，再在项目根目录运行：

```powershell
node deploy/migration/export-local-data.cjs
```

导出脚本只连接 localhost；它会创建一个唯一的本地临时数据库验证 SQL 和路径修正，验证结束后仅删除该临时库。
