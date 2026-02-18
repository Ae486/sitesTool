package com.rpacloud.flow.entity;

import java.time.LocalDateTime;

import com.rpacloud.site.entity.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "automation_flows")
@EntityListeners(AuditingEntityListener.class)
public class AutomationFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_id", insertable = false, updatable = false)
    private Long siteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String dsl;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", nullable = false, length = 20)
    @Builder.Default
    private FlowStatus lastStatus = FlowStatus.idle;

    @Column(nullable = false)
    @Builder.Default
    private Boolean headless = true;

    @Column(name = "browser_type", nullable = false, length = 50)
    @Builder.Default
    private String browserType = "chromium";

    @Column(name = "browser_path", length = 500)
    private String browserPath;

    @Column(name = "use_cdp_mode", nullable = false)
    @Builder.Default
    private Boolean useCdpMode = false;

    @Column(name = "cdp_port", nullable = false)
    @Builder.Default
    private Integer cdpPort = 9222;

    @Column(name = "cdp_user_data_dir", length = 500)
    private String cdpUserDataDir;

    @Column(name = "use_proxy", nullable = false)
    @Builder.Default
    private Boolean useProxy = false;

    @Column(name = "proxy_id")
    private Long proxyId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
