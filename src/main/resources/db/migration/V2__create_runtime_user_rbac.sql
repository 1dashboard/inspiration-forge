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

alter table runtime_record
    add column createdByRuntimeUserId bigint null after createdByPlatformUserId;

alter table runtime_record
    add index idx_runtime_record_runtime_creator (createdByRuntimeUserId, createTime);
