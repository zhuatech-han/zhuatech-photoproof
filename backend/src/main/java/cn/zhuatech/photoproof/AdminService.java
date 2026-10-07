// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号、角色、菜单、部门、字典及参数管理；保护最后管理员和客户绑定。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class AdminService {
  final Store db;
  final AccessService access;
  final BCryptPasswordEncoder encoder;

  /** 连接后台目录与密码编码服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public AdminService(Store db, AccessService access, BCryptPasswordEncoder encoder) {
    this.db = db;
    this.access = access;
    this.encoder = encoder;
  }

  /** 强密码且尊重BCrypt字节上限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void validatePassword(String s) {
    if (s == null
        || s.getBytes(StandardCharsets.UTF_8).length < 12
        || s.getBytes(StandardCharsets.UTF_8).length > 72
        || !s.matches("(?s).*[a-z].*")
        || !s.matches("(?s).*[A-Z].*")
        || !s.matches("(?s).*[0-9].*")
        || s.chars().anyMatch(Character::isISOControl)) throw new Problem(400, "PASSWORD_WEAK");
  }

  /** 全实例管理权限必须同时是ALL范围，不能通过部门角色自行提权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void require(String code) {
    access.require(code);
    if (!access.role().scope.equals("ALL")) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 同一实例目录写入串行化，避免最后管理员或币种变更竞争。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void lock() {
    db.lock(Department.class, 1L);
  }

  /** 安全账号信息从不返回密码散列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> user(Account a) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", a.id);
    m.put("username", a.username);
    m.put("displayName", a.displayName);
    m.put("roleId", a.roleId);
    m.put("departmentId", a.departmentId);
    m.put("clientId", a.clientId);
    m.put("kind", a.kind);
    m.put("enabled", a.enabled);
    m.put("version", a.version);
    return m;
  }

  /** 只读取对应管理权限的目录。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Object read(String kind) {
    require(permission(kind));
    return switch (kind) {
      case "users" -> db.all(Account.class).stream().map(this::user).toList();
      case "roles" -> db.all(AccessRole.class);
      case "permissions" -> db.all(Permission.class);
      case "menus" -> db.all(NavMenu.class);
      case "departments" -> db.all(Department.class);
      case "dictionaries" -> db.all(DictionaryEntry.class);
      case "settings" -> db.all(SystemSetting.class);
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  String permission(String kind) {
    return switch (kind) {
      case "users" -> "users";
      case "roles", "permissions" -> "roles";
      case "menus", "departments", "dictionaries", "settings" -> "settings";
      default -> throw new Problem(404, "NOT_FOUND");
    };
  }

  /** 完整新增与修改；稳定代码和已关联身份不变。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String kind, Long id, Map<String, Object> b) {
    require(permission(kind));
    lock();
    Object result;
    switch (kind) {
      case "users":
        {
          var a = id == null ? new Account() : db.get(Account.class, id);
          if (id != null) Rules.check(a.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
          String kindValue = Rules.text(b.get("kind"), 20, true);
          if (!Set.of("STAFF", "CLIENT").contains(kindValue))
            throw new Problem(400, "INVALID_INPUT");
          Long clientId = kindValue.equals("CLIENT") ? Rules.id(b.get("clientId")) : null;
          Long dept = Rules.id(b.get("departmentId"));
          var department = db.get(Department.class, dept);
          Rules.check(department.enabled, "DEPARTMENT_DISABLED");
          var role = db.get(AccessRole.class, Rules.id(b.get("roleId")));
          Rules.check(
              kindValue.equals("CLIENT") == role.scope.equals("CLIENT"), "ROLE_KIND_MISMATCH");
          if (clientId != null) {
            var client = db.get(Client.class, clientId);
            Rules.check(client.enabled && client.departmentId.equals(dept), "CLIENT_SCOPE_INVALID");
          }
          if (id != null) {
            Rules.check(
                a.kind.equals(kindValue) && Objects.equals(a.clientId, clientId),
                "IDENTITY_LOCKED");
            if (!a.departmentId.equals(dept))
              Rules.check(
                  db.query(
                          PhotoJob.class,
                          "from PhotoJob where createdBy=?1 or photographerId=?1 or editorId=?1",
                          a.id)
                      .isEmpty(),
                  "REFERENCE_PROTECTED");
          }
          String username = Rules.text(b.get("username"), 60, true).toLowerCase(Locale.ROOT);
          if (!username.matches("[a-z0-9_.-]{3,60}")) throw new Problem(400, "INVALID_INPUT");
          if (id != null) Rules.check(username.equals(a.username), "IDENTITY_LOCKED");
          String password = Objects.toString(b.get("password"), "");
          if (id == null || !password.isEmpty()) {
            validatePassword(password);
            a.passwordHash = encoder.encode(password);
          }
          a.username = username;
          a.displayName = Rules.text(b.get("displayName"), 120, true);
          a.roleId = role.id;
          a.departmentId = dept;
          a.kind = kindValue;
          a.clientId = clientId;
          a.enabled = Rules.flag(b.get("enabled"));
          if (id == null) db.save(a);
          else a.version++;
          db.flush();
          lastAdmin();
          result = user(a);
          break;
        }
      case "roles":
        {
          var r = id == null ? new AccessRole() : db.get(AccessRole.class, id);
          if (id != null) Rules.check(r.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
          String scope = Rules.text(b.get("scope"), 20, true);
          if (!Set.of("ALL", "DEPARTMENT", "ASSIGNED", "CLIENT").contains(scope))
            throw new Problem(400, "INVALID_INPUT");
          if (!(b.get("permissions") instanceof List<?> list))
            throw new Problem(400, "INVALID_INPUT");
          Set<String> perms = new HashSet<>();
          for (Object v : list) {
            String c = Rules.text(v, 60, true);
            Rules.check(Bootstrap.CODES.contains(c), "UNKNOWN_PERMISSION");
            perms.add(c);
          }
          Rules.check(
              scope.equals("CLIENT") ? perms.equals(Set.of("portal")) : !perms.contains("portal"),
              "ROLE_KIND_MISMATCH");
          if (id != null)
            for (var a : db.query(Account.class, "from Account where roleId=?1", r.id))
              Rules.check(a.kind.equals("CLIENT") == scope.equals("CLIENT"), "ROLE_KIND_MISMATCH");
          r.name = Rules.text(b.get("name"), 120, true);
          r.scope = scope;
          r.permissions.clear();
          r.permissions.addAll(perms);
          if (id == null) db.save(r);
          else r.version++;
          db.flush();
          lastAdmin();
          result = r;
          break;
        }
      case "departments":
        {
          var d = id == null ? new Department() : db.get(Department.class, id);
          if (id != null) Rules.check(d.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
          String zone = Rules.text(b.get("zone"), 80, true);
          ZoneId.of(zone);
          String currency = Rules.text(b.get("currency"), 3, true);
          Rules.check(
              Set.of("CNY", "USD", "EUR", "GBP", "HKD").contains(currency), "INVALID_CURRENCY");
          if (id != null
              && !db.query(PhotoJob.class, "from PhotoJob where departmentId=?1", id).isEmpty())
            Rules.check(
                d.currency.equals(currency) && d.zone.equals(zone), "DEPARTMENT_CONTRACT_LOCKED");
          d.name = Rules.text(b.get("name"), 120, true);
          d.zone = zone;
          d.currency = currency;
          d.enabled = Rules.flag(b.get("enabled"));
          if (id == null) db.save(d);
          else d.version++;
          db.flush();
          lastAdmin();
          result = d;
          break;
        }
      case "dictionaries":
        {
          var d = id == null ? new DictionaryEntry() : db.get(DictionaryEntry.class, id);
          String code = Rules.text(b.get("code"), 60, true);
          Rules.check(code.matches("[A-Z0-9_]{2,60}"), "INVALID_INPUT");
          if (id != null) Rules.check(d.code.equals(code), "IDENTITY_LOCKED");
          d.type = "SHOOT_TYPE";
          d.code = code;
          d.name = Rules.text(b.get("name"), 120, true);
          d.nameEn = Rules.text(b.get("nameEn"), 120, true);
          d.enabled = Rules.flag(b.get("enabled"));
          if (id == null) db.save(d);
          result = d;
          break;
        }
      case "menus":
        {
          Rules.check(id != null, "SYSTEM_DIRECTORY_FIXED");
          var m = db.get(NavMenu.class, id);
          Rules.check(
              m.code.equals(b.get("code")) && m.permissionCode.equals(b.get("permissionCode")),
              "MENU_PERMISSION_FIXED");
          m.name = Rules.text(b.get("name"), 120, true);
          m.nameEn = Rules.text(b.get("nameEn"), 120, true);
          m.position = Rules.integer(b.get("position"), 0, 100);
          m.enabled = Rules.flag(b.get("enabled"));
          result = m;
          break;
        }
      case "permissions":
        {
          Rules.check(id != null, "SYSTEM_DIRECTORY_FIXED");
          var x = db.get(Permission.class, id);
          Rules.check(x.code.equals(b.get("code")), "IDENTITY_LOCKED");
          x.name = Rules.text(b.get("name"), 120, true);
          result = x;
          break;
        }
      case "settings":
        {
          Rules.check(id != null, "SYSTEM_DIRECTORY_FIXED");
          var x = db.get(SystemSetting.class, id);
          Rules.check(x.code.equals(b.get("code")), "IDENTITY_LOCKED");
          String v = Rules.text(b.get("value"), 40, true);
          switch (x.code) {
            case "gallery_days" -> Rules.integer(v, 1, 365);
            case "link_hours" -> Rules.integer(v, 1, 168);
            case "max_job_mb" -> Rules.integer(v, 10, 4096);
            case "watermark" ->
                Rules.check(v.matches("[A-Za-z0-9 ._-]{1,32}"), "INVALID_WATERMARK");
            default -> throw new Problem(400, "INVALID_INPUT");
          }
          x.value = v;
          result = x;
          break;
        }
      default:
        throw new Problem(404, "NOT_FOUND");
    }
    access.audit(
        "ADMIN_" + kind.toUpperCase(Locale.ROOT),
        id == null ? "NEW" : id,
        access.current().departmentId);
    return result;
  }

  void lastAdmin() {
    Rules.check(
        db.all(Account.class).stream()
            .anyMatch(
                a ->
                    a.enabled
                        && a.kind.equals("STAFF")
                        && db.get(Department.class, a.departmentId).enabled
                        && db.get(AccessRole.class, a.roleId).scope.equals("ALL")
                        && db.get(AccessRole.class, a.roleId)
                            .permissions
                            .containsAll(
                                Bootstrap.CODES.stream()
                                    .filter(c -> !c.equals("portal"))
                                    .toList())),
        "LAST_ADMIN");
  }

  /** 管理账号的安全选项目录，不要求额外读取客户联系方式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> options() {
    require("users");
    return Map.of(
        "roles",
        db.all(AccessRole.class),
        "departments",
        db.all(Department.class),
        "clients",
        db.all(Client.class).stream()
            .map(
                c ->
                    Map.of(
                        "id",
                        c.id,
                        "name",
                        c.name,
                        "departmentId",
                        c.departmentId,
                        "enabled",
                        c.enabled))
            .toList());
  }

  /** 参数读取不接受动态SQL，值经过目录管理校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String setting(String code) {
    return db.query(SystemSetting.class, "from SystemSetting where code=?1", code).getFirst().value;
  }
}
