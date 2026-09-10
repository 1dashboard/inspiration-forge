# 完整建表语句（最终 schema）
# 包含核心业务表、运行时数据表、RBAC 表、RAG 表及所有最终字段

create database if not exists inspiration_forge default character set utf8mb4 collate utf8mb4_unicode_ci;

use inspiration_forge;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;

-- 应用表
create table if not exists app
(
    id           bigint auto_increment comment 'id' primary key,
    appName      varchar(256)                       null comment '应用名称',
    cover        varchar(512)                       null comment '应用封面',
    initPrompt   text                               null comment '应用初始化的 prompt',
    codeGenType  varchar(64)                        null comment '代码生成类型（枚举）',
    deployKey    varchar(64)                        null comment '部署标识',
    deployedTime datetime                           null comment '部署时间',
    currentVersionId bigint                         null comment '当前版本id',
    versionNumber  int default 0                    not null comment '当前版本号',
    generationStatus varchar(32) default 'IDLE'      not null comment '生成状态',
    deployStatus varchar(32) default 'NOT_DEPLOYED' not null comment '部署状态',
    ragEnabled tinyint default 0 not null comment '是否启用 RAG 项目记忆',
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey),
    INDEX idx_appName (appName),
    INDEX idx_userId (userId)
) comment '应用' collate = utf8mb4_unicode_ci;

-- 应用版本表
create table if not exists app_version
(
    id             bigint auto_increment comment 'id' primary key,
    appId          bigint not null comment '应用id',
    versionNumber  int not null comment '版本号',
    codeGenType    varchar(64) not null comment '代码生成类型',
    sourcePath     varchar(1024) null comment '版本文件目录',
    status         varchar(32) default 'GENERATING' not null comment '版本状态',
    changeMessage  varchar(512) null comment '变更说明',
    createTime     datetime default CURRENT_TIMESTAMP not null,
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete       tinyint default 0 not null,
    unique key uk_app_version (appId, versionNumber),
    index idx_app_version_app (appId)
) comment '应用版本';

-- 生成任务表
create table if not exists generation_task
(
    id            bigint auto_increment primary key,
    appId         bigint not null,
    userId        bigint not null,
    prompt        text not null,
    mode          varchar(32) not null,
    taskType      varchar(32) default 'GENERATION' not null,
    attempt       int default 0 not null,
    maxAttempts   int default 0 not null,
    status        varchar(32) not null,
    currentStep   varchar(64) null,
    currentFile   varchar(1024) null,
    progress      int default 0 not null,
    errorMessage  varchar(2000) null,
    buildStatus   varchar(32) null,
    buildOutput   mediumtext null,
    startedAt     datetime null,
    finishedAt    datetime null,
    createTime    datetime default CURRENT_TIMESTAMP not null,
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete      tinyint default 0 not null,
    index idx_generation_task_app (appId, createTime),
    index idx_generation_task_user (userId, createTime),
    index idx_generation_task_status (status)
) comment 'AI generation tasks';

-- 对话历史表
create table if not exists chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),
    INDEX idx_createTime (createTime),
    INDEX idx_appId_createTime (appId, createTime)
) comment '对话历史' collate = utf8mb4_unicode_ci;

-- 生成应用运行面配置
create table if not exists runtime_app
(
    id          bigint                              not null primary key,
    appId       bigint                              not null comment '平台应用 id',
    runtimeKey  varchar(64)                         not null comment '运行面公开标识，不作为密钥使用',
    enabled     tinyint   default 1                 not null comment '运行面是否启用',
    createTime  datetime  default CURRENT_TIMESTAMP not null,
    updateTime  datetime  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    unique key uk_runtime_app_app (appId),
    unique key uk_runtime_app_key (runtimeKey)
) comment '生成应用运行面配置' collate = utf8mb4_unicode_ci;

-- 托管数据模型
create table if not exists runtime_model
(
    id            bigint                              not null primary key,
    appId         bigint                              not null comment '平台应用 id',
    environment   varchar(16)                         not null comment 'PREVIEW / PRODUCTION',
    modelKey      varchar(64)                         not null comment '稳定的数据模型标识',
    displayName   varchar(128)                        not null comment '模型展示名称',
    schemaJson    mediumtext                          not null comment '受控模型定义 JSON',
    schemaVersion int       default 1                 not null comment 'Schema 版本',
    status        varchar(16) default 'DRAFT'         not null comment 'DRAFT / PUBLISHED',
    createTime    datetime  default CURRENT_TIMESTAMP not null,
    updateTime    datetime  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete      tinyint   default 0                 not null,
    unique key uk_runtime_model (appId, environment, modelKey),
    index idx_runtime_model_app_env (appId, environment, status)
) comment '托管数据模型' collate = utf8mb4_unicode_ci;

-- 托管数据记录
create table if not exists runtime_record
(
    id                      bigint                              not null primary key,
    appId                   bigint                              not null comment '平台应用 id',
    environment             varchar(16)                         not null comment 'PREVIEW / PRODUCTION',
    modelKey                varchar(64)                         not null comment '数据模型标识',
    dataJson                mediumtext                          not null comment '通过 Schema 校验的数据 JSON',
    recordVersion           int       default 1                 not null comment '乐观锁版本',
    createdByPlatformUserId bigint                              null comment '第一阶段记录的平台操作者',
    createdByRuntimeUserId  bigint                              null comment '运行时应用用户操作者',
    createTime              datetime  default CURRENT_TIMESTAMP not null,
    updateTime              datetime  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete                tinyint   default 0                 not null,
    index idx_runtime_record_scope (appId, environment, modelKey, createTime),
    index idx_runtime_record_creator (createdByPlatformUserId, createTime),
    index idx_runtime_record_runtime_creator (createdByRuntimeUserId, createTime)
) comment '托管数据记录' collate = utf8mb4_unicode_ci;

-- 应用用户
create table if not exists runtime_app_user
(
    id          bigint                              not null primary key,
    appId       bigint                              not null,
    username    varchar(64)                         not null,
    passwordHash varchar(255)                       not null,
    displayName varchar(128)                        null,
    status      varchar(16) default 'ACTIVE'       not null,
    createTime  datetime default CURRENT_TIMESTAMP  not null,
    updateTime  datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP,
    unique key uk_runtime_user_app_username (appId, username),
    index idx_runtime_user_app (appId, status)
) comment '应用用户' collate = utf8mb4_unicode_ci;

-- 应用角色
create table if not exists runtime_app_role
(
    id          bigint                              not null primary key,
    appId       bigint                              not null,
    roleKey     varchar(64)                         not null,
    displayName varchar(128)                        not null,
    createTime  datetime default CURRENT_TIMESTAMP  not null,
    updateTime  datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP,
    unique key uk_runtime_role_app_key (appId, roleKey),
    index idx_runtime_role_app (appId)
) comment '应用角色' collate = utf8mb4_unicode_ci;

-- 应用用户角色关联
create table if not exists runtime_app_user_role
(
    id          bigint                              not null primary key,
    appId       bigint                              not null,
    userId      bigint                              not null,
    roleId      bigint                              not null,
    createTime  datetime default CURRENT_TIMESTAMP  not null,
    unique key uk_runtime_user_role (appId, userId, roleId),
    index idx_runtime_user_role_user (appId, userId),
    index idx_runtime_user_role_role (appId, roleId)
) comment '应用用户角色关联' collate = utf8mb4_unicode_ci;

-- 应用用户会话
create table if not exists runtime_app_session
(
    id          bigint                              not null primary key,
    appId       bigint                              not null,
    userId      bigint                              not null,
    tokenHash   varchar(128)                        not null,
    expiresAt   datetime                            not null,
    createTime  datetime default CURRENT_TIMESTAMP  not null,
    lastSeenAt  datetime default CURRENT_TIMESTAMP  not null,
    unique key uk_runtime_session_token (tokenHash),
    index idx_runtime_session_user (appId, userId),
    index idx_runtime_session_expire (expiresAt)
) comment '应用用户会话' collate = utf8mb4_unicode_ci;

-- 应用 RAG 知识库
create table if not exists rag_knowledge_base
(
    id          bigint                              not null primary key,
    appId       bigint                              not null comment '平台应用 id',
    userId      bigint                              not null comment '应用创建者',
    name        varchar(128)                        not null,
    scope       varchar(32) default 'APP'           not null comment 'APP / USER',
    status      varchar(16) default 'ACTIVE'        not null,
    createTime  datetime default CURRENT_TIMESTAMP  not null,
    updateTime  datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP,
    isDelete    tinyint default 0                   not null,
    unique key uk_rag_kb_app (appId),
    index idx_rag_kb_user (userId, createTime)
) comment '应用 RAG 知识库' collate = utf8mb4_unicode_ci;

-- RAG 原始文档
create table if not exists rag_document
(
    id              bigint                              not null primary key,
    knowledgeBaseId bigint                              not null,
    appId           bigint                              not null,
    sourceType      varchar(32)                         not null comment 'CHAT / CODE / GUIDE / MEMORY',
    sourceRef       varchar(1024)                       null comment '来源标识，例如文件路径或消息 id',
    title           varchar(512)                        null,
    content         mediumtext                          not null,
    contentHash     varchar(64)                         not null,
    metadataJson    text                                null,
    status          varchar(16) default 'READY'         not null,
    createTime      datetime default CURRENT_TIMESTAMP  not null,
    updateTime      datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP,
    isDelete        tinyint default 0                   not null,
    unique key uk_rag_document_hash (appId, contentHash),
    index idx_rag_document_app_source (appId, sourceType, createTime),
    index idx_rag_document_kb (knowledgeBaseId, createTime)
) comment 'RAG 原始文档' collate = utf8mb4_unicode_ci;

-- RAG 文档切片
create table if not exists rag_chunk
(
    id              bigint                              not null primary key,
    knowledgeBaseId bigint                              not null,
    documentId      bigint                              not null,
    appId           bigint                              not null,
    chunkIndex      int                                 not null,
    content         text                                not null,
    tokenCount      int default 0                       not null,
    embeddingJson   mediumtext                          null comment '可选的 Embedding 向量 JSON',
    metadataJson    text                                null,
    contentHash     varchar(64)                         not null,
    createTime      datetime default CURRENT_TIMESTAMP  not null,
    updateTime      datetime default CURRENT_TIMESTAMP  not null on update CURRENT_TIMESTAMP,
    isDelete        tinyint default 0                   not null,
    unique key uk_rag_chunk_hash (appId, contentHash),
    index idx_rag_chunk_app (appId, createTime),
    index idx_rag_chunk_document (documentId, chunkIndex)
) comment 'RAG 文档切片' collate = utf8mb4_unicode_ci;
