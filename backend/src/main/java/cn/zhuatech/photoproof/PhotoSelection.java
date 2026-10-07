// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.time.*;

/** 客户每轮选择与修图备注；确认后的旧轮次不再修改。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "photo_selection")
public class PhotoSelection {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "round_number", nullable = false)
  public int roundNumber;

  @Column(name = "photo_id", nullable = false)
  public Long photoId;

  @Column(name = "selected", nullable = false)
  public boolean selected = true;

  @Column(name = "note", nullable = false, length = 1000)
  public String note = "";

  @Column(name = "actor", nullable = false, length = 80)
  public String actor;

  @Column(name = "updated_at", nullable = false)
  public Instant updatedAt;
}
