package io.github.onedashboard.inspirationforge.runtime.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Table("runtime_app_session")
public class RuntimeAppSession implements Serializable {
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;
    @Column("appId")
    private Long appId;
    @Column("userId")
    private Long userId;
    @Column("tokenHash")
    private String tokenHash;
    @Column("expiresAt")
    private LocalDateTime expiresAt;
    @Column("createTime")
    private LocalDateTime createTime;
    @Column("lastSeenAt")
    private LocalDateTime lastSeenAt;
}
