# 数据库初始化
# @author 1dashboard
-- 创建库
create database if not exists inspiration_forge;

-- 切换库
use inspiration_forge;

-- 用户表
-- 以下是建表语句

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
create table app
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
    priority     int      default 0                 not null comment '优先级',
    userId       bigint                             not null comment '创建用户id',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    UNIQUE KEY uk_deployKey (deployKey), -- 确保部署标识唯一
    INDEX idx_appName (appName),         -- 提升基于应用名称的查询性能
    INDEX idx_userId (userId)            -- 提升基于用户 ID 的查询性能
) comment '应用' collate = utf8mb4_unicode_ci;

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

-- 对话历史表
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

create table if not exists runtime_record
(
    id                      bigint                              not null primary key,
    appId                   bigint                              not null comment '平台应用 id',
    environment             varchar(16)                         not null comment 'PREVIEW / PRODUCTION',
    modelKey                varchar(64)                         not null comment '数据模型标识',
    dataJson                mediumtext                          not null comment '通过 Schema 校验的数据 JSON',
    recordVersion           int       default 1                 not null comment '乐观锁版本',
    createdByPlatformUserId bigint                              null comment '第一阶段记录的平台操作者',
    createTime              datetime  default CURRENT_TIMESTAMP not null,
    updateTime              datetime  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    isDelete                tinyint   default 0                 not null,
    index idx_runtime_record_scope (appId, environment, modelKey, createTime),
    index idx_runtime_record_creator (createdByPlatformUserId, createTime)
) comment '托管数据记录' collate = utf8mb4_unicode_ci;

create table chat_history
(
    id          bigint auto_increment comment 'id' primary key,
    message     text                               not null comment '消息',
    messageType varchar(32)                        not null comment 'user/ai',
    appId       bigint                             not null comment '应用id',
    userId      bigint                             not null comment '创建用户id',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_appId (appId),                       -- 提升基于应用的查询性能
    INDEX idx_createTime (createTime),             -- 提升基于时间的查询性能
    INDEX idx_appId_createTime (appId, createTime) -- 游标查询核心索引
) comment '对话历史' collate = utf8mb4_unicode_ci;
