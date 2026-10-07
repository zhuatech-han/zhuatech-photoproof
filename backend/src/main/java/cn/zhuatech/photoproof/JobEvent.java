// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.time.*;

/** 不可覆盖的项目业务历史；只记录动作、原因和状态，不写分享秘密。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "job_event")
public class JobEvent {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "job_id", nullable = false)
  public Long jobId;

  @Column(name = "action", nullable = false, length = 60)
  public String action;

  @Column(name = "from_state", nullable = false, length = 30)
  public String fromState;

  @Column(name = "to_state", nullable = false, length = 30)
  public String toState;

  @Column(name = "round_number", nullable = false)
  public int roundNumber;

  @Column(name = "note", nullable = false, length = 1000)
  public String note;

  @Column(name = "actor", nullable = false, length = 80)
  public String actor;

  @Column(name = "created_at", nullable = false)
  public Instant createdAt;
}
