// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import java.io.Serializable;
import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** 实时账号权限、工作室/指派项目和单项目分享范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
public class AccessService {
  final Store db;
  final Clock clock;

  /** 分享会话只记授权ID，不记原始令牌和访问码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record ShareSession(Long grantId) implements Serializable {}

  /** 连接持久化身份与服务端时钟。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public AccessService(Store db, Clock clock) {
    this.db = db;
    this.clock = clock;
  }

  /** 正常账号重新读取密码指纹，禁用和重置立即失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Account current() {
    var a = authentication();
    if (a.getDetails() instanceof ShareSession) throw new Problem(403, "STAFF_ONLY");
    var rows = db.query(Account.class, "from Account where username=?1", a.getName());
    if (rows.isEmpty()
        || !rows.getFirst().enabled
        || !Objects.equals(a.getDetails(), rows.getFirst().passwordHash))
      throw new Problem(401, "UNAUTHENTICATED");
    if (!db.get(Department.class, rows.getFirst().departmentId).enabled)
      throw new Problem(401, "DEPARTMENT_DISABLED");
    return rows.getFirst();
  }

  org.springframework.security.core.Authentication authentication() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !a.isAuthenticated()) throw new Problem(401, "UNAUTHENTICATED");
    return a;
  }

  /** 当前分享必须未撤销、未过期且客户仍启用。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ShareGrant grant() {
    if (!(authentication().getDetails() instanceof ShareSession s)) return null;
    var g = db.get(ShareGrant.class, s.grantId());
    var j = db.get(PhotoJob.class, g.jobId);
    if (!g.enabled
        || !g.expiresAt.isAfter(clock.instant())
        || !db.get(Client.class, j.clientId).enabled
        || !db.get(Department.class, j.departmentId).enabled)
      throw new Problem(401, "SHARE_EXPIRED");
    return g;
  }

  /** 读取当前账号实时角色。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public AccessRole role() {
    return db.get(AccessRole.class, current().roleId);
  }

  /** 客户或分享入口不能使用员工接口，即使管理员给角色误配置权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void require(String code) {
    var a = current();
    if (!a.kind.equals("STAFF") || !role().permissions.contains(code))
      throw new Problem(403, "FORBIDDEN");
  }

  /** 按员工、客户或单项目分享判断业务权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean has(String code) {
    if (authentication().getDetails() instanceof ShareSession)
      return code.equals("portal") && grant() != null;
    var a = current();
    return role().permissions.contains(code)
        && (code.equals("portal") ? a.kind.equals("CLIENT") : a.kind.equals("STAFF"));
  }

  /** 拒绝跨权限范围的工作室访问。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void department(Long id) {
    if (!visibleDepartment(id)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 核对全部或本人工作室范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visibleDepartment(Long id) {
    return role().scope.equals("ALL") || Objects.equals(current().departmentId, id);
  }

  /**
   * STAFF按指派/部门/全部过滤项目，客户只见自己的已发布项目，分享限一个项目。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。
   */
  public boolean visible(PhotoJob j) {
    var g = grant();
    if (g != null) return Objects.equals(g.jobId, j.id) && !j.state.equals("DRAFT");
    var a = current();
    var r = role();
    if (a.kind.equals("CLIENT"))
      return r.permissions.contains("portal")
          && db.get(Client.class, a.clientId).enabled
          && Objects.equals(a.clientId, j.clientId)
          && !j.state.equals("DRAFT");
    if (!visibleDepartment(j.departmentId)) return false;
    return !r.scope.equals("ASSIGNED")
        || Objects.equals(j.photographerId, a.id)
        || Objects.equals(j.editorId, a.id)
        || Objects.equals(j.createdBy, a.id);
  }

  /** 客户确认仅允许客户或限项目分享会话。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void client(PhotoJob j) {
    if (!has("portal") || !visible(j)) throw new Problem(403, "CLIENT_ONLY");
  }

  /** 检查项目角色和真实数据范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void job(PhotoJob j, String permission) {
    require(permission);
    if (!visible(j)) throw new Problem(403, "OUT_OF_SCOPE");
  }

  /** 审计不包含密码、令牌和访问码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void audit(String action, Object id, Long department) {
    var e = new AuditEvent();
    e.actor = actor();
    e.action = action;
    e.objectId = String.valueOf(id);
    e.departmentId = department;
    e.createdAt = clock.instant();
    db.save(e);
  }

  /** 返回审计身份，不记录会话或访问码。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public String actor() {
    var g = grant();
    return g == null ? current().username : "share:" + g.id;
  }

  /** 菜单和安全身份返回；分享入口不泄露全局客户/账号列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> profile() {
    var g = grant();
    var out = new LinkedHashMap<String, Object>();
    Set<String> codes;
    String kind;
    if (g != null) {
      var j = db.get(PhotoJob.class, g.jobId);
      out.put("id", "share:" + g.id);
      out.put("displayName", db.get(Client.class, j.clientId).name);
      out.put("departmentId", j.departmentId);
      out.put("shareJobId", j.id);
      out.put("role", "Client gallery");
      codes = Set.of("portal");
      kind = "SHARE";
    } else {
      var a = current();
      var r = role();
      out.put("id", a.id);
      out.put("username", a.username);
      out.put("displayName", a.displayName);
      out.put("departmentId", a.departmentId);
      out.put("clientId", a.clientId);
      out.put("role", r.name);
      out.put("scope", r.scope);
      codes = r.permissions;
      kind = a.kind;
    }
    out.put("kind", kind);
    out.put("permissions", codes);
    out.put(
        "menus",
        db.all(NavMenu.class).stream()
            .filter(
                m ->
                    m.enabled
                        && codes.contains(m.permissionCode)
                        && (kind.equals("STAFF")
                            ? !m.code.equals("portal")
                            : m.code.equals("portal")))
            .sorted(Comparator.comparingInt(m -> m.position))
            .toList());
    return out;
  }
}
