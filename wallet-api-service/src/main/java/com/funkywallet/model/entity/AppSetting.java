package com.funkywallet.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
@Getter @Setter
public class AppSetting {
    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "value")
    private String value;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() { updatedAt = Instant.now(); }
}
