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
