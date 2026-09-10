alter table app
    add column ragEnabled tinyint default 0 not null comment '是否启用 RAG 项目记忆' after deployStatus;
