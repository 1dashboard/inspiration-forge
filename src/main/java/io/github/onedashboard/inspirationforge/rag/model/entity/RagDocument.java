package io.github.onedashboard.inspirationforge.rag.model.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Table("rag_document")
public class RagDocument implements Serializable {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Column("knowledgeBaseId")
    private Long knowledgeBaseId;
    @Column("appId")
    private Long appId;
    private String sourceType;
    private String sourceRef;
    private String title;
    private String content;
    @Column("contentHash")
    private String contentHash;
    @Column("metadataJson")
    private String metadataJson;
    private String status;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("updateTime")
    private LocalDateTime updateTime;
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
