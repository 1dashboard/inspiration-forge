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
