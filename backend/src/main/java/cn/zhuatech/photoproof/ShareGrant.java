// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.time.*;

/** 单项目临时客户入口；只存分享令牌散列和访问码散列。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "share_grant")
public class ShareGrant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "token_hash", nullable = false, length = 64)
  public String tokenHash;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "pin_hash", nullable = false, length = 100)
  public String pinHash;

  @Column(name = "expires_at", nullable = false)
  public Instant expiresAt;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;
}
