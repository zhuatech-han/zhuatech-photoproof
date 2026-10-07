// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.time.*;

/** 客户对指定照片版本的认可或修改请求，追加历史而非覆盖。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "photo_review")
public class PhotoReview {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "media_id", nullable = false)
  public Long mediaId;

  @Column(name = "decision", nullable = false, length = 20)
  public String decision;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "actor", nullable = false, length = 80)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
