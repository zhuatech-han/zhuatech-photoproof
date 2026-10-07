// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;

/** 已经核实的外部收退款与单次冲正；不转账不覆盖资金历史。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "cash_entry")
public class CashEntry {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "source_id", nullable = true)
  public Long sourceId;

  @Column(name = "amount", nullable = false, precision = 16, scale = 2)
  public BigDecimal amount;

  @Column(name = "reference", nullable = false, length = 120)
  public String reference;

  @Column(name = "note", nullable = false, length = 600)
  public String note;

  @Column(name = "actor", nullable = false, length = 80)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
