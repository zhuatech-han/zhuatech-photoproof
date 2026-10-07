// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.persistence.*;
import java.time.*;

/** 登录账号、实时角色与不可转换的客户绑定；密码散列不返回。官网 https://www.zhuatech.cn/；商业咨询微信 zhuatech / zhuatech2。 */
@Entity
@Table(name = "account")
public class Account {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(name = "username", nullable = false, length = 60)
  public String username;

  @Column(name = "display_name", nullable = false, length = 120)
  public String displayName;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "password_hash", nullable = false, length = 100)
  public String passwordHash;

  @Column(name = "role_id", nullable = false)
  public Long roleId;

  @Column(name = "department_id", nullable = false)
  public Long departmentId;

  @Column(name = "kind", nullable = false, length = 20)
  public String kind;

  @Column(name = "client_id", nullable = true)
  public Long clientId;

  @Column(name = "enabled", nullable = false)
  public boolean enabled = true;

  @Column(name = "version", nullable = false)
  public long version = 1;
}
