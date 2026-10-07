// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 摄影项目、选片计费约定与完整交付状态；保留每轮历史。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "photo_job")
public class PhotoJob {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "client_id", nullable = false)
  public Long clientId;

  @Column(name = "photographer_id", nullable = false)
  public Long photographerId;

  @Column(name = "editor_id", nullable = false)
  public Long editorId;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "title", nullable = false, length = 160)
  public String title;

  @Column(name = "shoot_type", nullable = false, length = 60)
  public String shootType;

  @Column(name = "shoot_date", nullable = false)
  public LocalDate shootDate;

  @Column(name = "state", nullable = false, length = 30)
  public String state = "DRAFT";

  @Column(name = "min_select", nullable = false)
  public int minSelect = 1;

  @Column(name = "included_count", nullable = false)
  public int includedCount = 1;

  @Column(name = "max_select", nullable = false)
  public int maxSelect = 1;

  @Column(name = "base_amount", nullable = false, precision = 16, scale = 2)
  public BigDecimal baseAmount;

  @Column(name = "extra_price", nullable = false, precision = 16, scale = 2)
  public BigDecimal extraPrice;

  @Column(name = "amount_due", nullable = false, precision = 16, scale = 2)
  public BigDecimal amountDue;

  @Column(name = "currency", nullable = false, length = 3)
  public String currency;

  @Column(name = "round_number", nullable = false)
  public int roundNumber = 1;

  @Column(name = "gallery_until", nullable = true)
  public Instant galleryUntil;

  @Column(name = "released_at", nullable = true)
  public Instant releasedAt;

  @Column(name = "closed_at", nullable = true)
  public Instant closedAt;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
