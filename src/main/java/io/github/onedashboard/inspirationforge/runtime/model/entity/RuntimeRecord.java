package io.github.onedashboard.inspirationforge.runtime.model.entity;

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
@Table("runtime_record")
public class RuntimeRecord implements Serializable {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Column("appId")
    private Long appId;
    private String environment;
    @Column("modelKey")
    private String modelKey;
    @Column("dataJson")
    private String dataJson;
    @Column("recordVersion")
    private Integer recordVersion;
    @Column("createdByPlatformUserId")
    private Long createdByPlatformUserId;
    @Column("createdByRuntimeUserId")
    private Long createdByRuntimeUserId;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("updateTime")
    private LocalDateTime updateTime;
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
