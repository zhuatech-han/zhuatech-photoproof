// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 空库只建立系统目录与私有初始管理员；不生成客户照片和资金。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Component
public class Bootstrap implements ApplicationRunner {
  final Store db;
  final BCryptPasswordEncoder encoder;
  final String username, password;
  static final List<String> CODES =
      List.of(
          "jobs",
          "clients",
          "upload",
          "edit",
          "release",
          "cash",
          "reports",
          "users",
          "roles",
          "settings",
          "audit",
          "portal");

  /** 连接首次初始化配置，不输出初始密码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Bootstrap(
      Store db,
      BCryptPasswordEncoder encoder,
      @Value("${photoproof.admin-username}") String username,
      @Value("${photoproof.admin-password}") String password) {
    this.db = db;
    this.encoder = encoder;
    this.username = username;
    this.password = password;
  }

  /** 幂等初始化，重启不改写密码、角色或业务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!db.all(Account.class).isEmpty()) return;
    AdminService.validatePassword(password);
    if (!username.matches("[a-zA-Z0-9_.-]{3,60}"))
      throw new IllegalStateException("Invalid initial administrator name");
    for (String code : CODES) {
      var p = new Permission();
      p.code = code;
      p.name = code;
      db.save(p);
    }
    var d = new Department();
    d.name = "主工作室 / Main studio";
    d.zone = "Asia/Shanghai";
    d.currency = "CNY";
    db.save(d);
    var admin =
        role(
            "管理员 / Administrator", "ALL", CODES.stream().filter(c -> !c.equals("portal")).toList());
    role(
        "工作室经理 / Studio manager",
        "DEPARTMENT",
        List.of("jobs", "clients", "upload", "edit", "release", "cash", "reports", "audit"));
    role("摄影师 / Photographer", "ASSIGNED", List.of("jobs", "clients", "upload", "release"));
    role("修图人员 / Retoucher", "ASSIGNED", List.of("jobs", "edit"));
    role("收款登记 / Cash desk", "DEPARTMENT", List.of("cash", "reports"));
    role("客户 / Client", "CLIENT", List.of("portal"));
    var a = new Account();
    a.username = username.toLowerCase(Locale.ROOT);
    a.displayName = "管理员 / Administrator";
    a.passwordHash = encoder.encode(password);
    a.roleId = admin.id;
    a.departmentId = d.id;
    a.kind = "STAFF";
    db.save(a);
    String[][] menus = {
      {"jobs", "项目", "Projects", "jobs"},
      {"edit", "修图台", "Retouching", "edit"},
      {"clients", "客户", "Clients", "clients"},
      {"cash", "收款与退款", "Receipts & refunds", "cash"},
      {"reports", "经营报表", "Reports", "reports"},
      {"portal", "我的图库", "My galleries", "portal"},
      {"users", "登录账号", "Accounts", "users"},
      {"roles", "角色与权限", "Roles & permissions", "roles"},
      {"settings", "工作室与设置", "Studios & settings", "settings"},
      {"audit", "操作记录", "Audit trail", "audit"}
    };
    for (int i = 0; i < menus.length; i++) {
      var m = new NavMenu();
      m.code = menus[i][0];
      m.name = menus[i][1];
      m.nameEn = menus[i][2];
      m.permissionCode = menus[i][3];
      m.position = i;
      m.enabled = true;
      db.save(m);
    }
    String[][] types = {
      {"PORTRAIT", "人像", "Portrait"},
      {"WEDDING", "婚纱与婚礼", "Wedding"},
      {"COMMERCIAL", "商业摄影", "Commercial"},
      {"EVENT", "活动摄影", "Event"}
    };
    for (var t : types) {
      var x = new DictionaryEntry();
      x.type = "SHOOT_TYPE";
      x.code = t[0];
      x.name = t[1];
      x.nameEn = t[2];
      db.save(x);
    }
    setting("gallery_days", "30");
    setting("link_hours", "72");
    setting("max_job_mb", "512");
    setting("watermark", "PROOF");
  }

  private AccessRole role(String name, String scope, List<String> codes) {
    var r = new AccessRole();
    r.name = name;
    r.scope = scope;
    r.permissions.addAll(codes);
    return db.save(r);
  }

  private void setting(String code, String value) {
    var x = new SystemSetting();
    x.code = code;
    x.value = value;
    db.save(x);
  }
}
