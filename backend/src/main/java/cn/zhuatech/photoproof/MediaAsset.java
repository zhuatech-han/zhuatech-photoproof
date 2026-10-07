// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.time.*;

/** 不可覆盖的真实照片与服务器水印预览；磁盘键不返回。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "photo_id", nullable = true)
  public Long photoId;

  @Column(name = "round_number", nullable = false)
  public int roundNumber;

  @Column(name = "revision_number", nullable = false)
  public int revisionNumber;

  @Column(name = "slot_code", nullable = false, length = 180)
  public String slotCode;

  @Column(name = "filename", nullable = false, length = 160)
  public String filename;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "file_key", nullable = false, length = 40)
  public String fileKey;

  @Column(name = "content_type", nullable = false, length = 30)
  public String contentType;

  @Column(name = "sha256", nullable = false, length = 64)
  public String sha256;

  @Column(name = "byte_size", nullable = false)
  public long byteSize;

  @Column(name = "width", nullable = false)
  public int width;

  @Column(name = "height", nullable = false)
  public int height;

  @Column(name = "watermark", nullable = false, length = 40)
  public String watermark;

  @Column(name = "created_by", nullable = false)
  public Long createdBy;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;

  @Column(name = "active", nullable = false)
  public boolean active = true;
}
