package io.github.onedashboard.inspirationforge.model.entity;

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
@Table("generation_task")
public class GenerationTask implements Serializable {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Column("appId")
    private Long appId;
    @Column("userId")
    private Long userId;
    private String prompt;
    private String mode;
    @Column("taskType")
    private String taskType;
    private Integer attempt;
    @Column("maxAttempts")
    private Integer maxAttempts;
    private String status;
    @Column("currentStep")
    private String currentStep;
    @Column("currentFile")
    private String currentFile;
    private Integer progress;
    @Column("errorMessage")
    private String errorMessage;
    @Column("buildStatus")
    private String buildStatus;
    @Column("buildOutput")
    private String buildOutput;
    @Column("startedAt")
    private LocalDateTime startedAt;
    @Column("finishedAt")
    private LocalDateTime finishedAt;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("updateTime")
    private LocalDateTime updateTime;
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
