// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 客户主档、整批导入及摄影师关联范围。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ClientService {
  final Store db;
  final AccessService access;
  final AdminService admin;

  /** 连接客户目录与实时范围校验。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ClientService(Store db, AccessService access, AdminService admin) {
    this.db = db;
    this.access = access;
    this.admin = admin;
  }

  /** 客户主档按部门和创建/关联项目过滤，修图角色不能读取联系方式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public boolean visible(Client c) {
    if (!access.visibleDepartment(c.departmentId)) return false;
    if (!access.role().scope.equals("ASSIGNED")) return true;
    return Objects.equals(c.createdBy, access.current().id)
        || db.query(PhotoJob.class, "from PhotoJob where clientId=?1", c.id).stream()
            .anyMatch(access::visible);
  }

  @Transactional(readOnly = true)
  /** 按员工工作室与指派范围读取客户列表。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Client> list() {
    access.require("clients");
    return db.all(Client.class).stream().filter(this::visible).toList();
  }

  /** 创建编辑保留引用和乐观版本；客户绑定不随资料编辑变动。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Client save(Long id, Map<String, Object> b) {
    access.require("clients");
    admin.lock();
    var c = id == null ? new Client() : db.get(Client.class, id);
    Long dept = Rules.id(b.get("departmentId"));
    access.department(dept);
    Rules.check(db.get(Department.class, dept).enabled, "DEPARTMENT_DISABLED");
    if (id != null) {
      Rules.check(visible(c), "OUT_OF_SCOPE");
      Rules.check(c.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
      Rules.check(c.departmentId.equals(dept) && c.code.equals(b.get("code")), "IDENTITY_LOCKED");
    }
    String code = Rules.text(b.get("code"), 60, true).toUpperCase(Locale.ROOT);
    if (!code.matches("[A-Z0-9_-]{2,60}")) throw new Problem(400, "INVALID_INPUT");
    c.departmentId = dept;
    c.code = code;
    c.name = Rules.text(b.get("name"), 120, true);
    c.contact = Rules.text(b.get("contact"), 160, false);
    c.enabled = Rules.flag(b.get("enabled"));
    if (id == null) {
      c.createdBy = access.current().id;
      db.save(c);
    } else c.version++;
    db.flush();
    access.audit("CLIENT_SAVE", c.id, dept);
    return c;
  }

  /** 确认后的CSV记录整批校验，任何行失败回滚全部新增。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<Client> importRows(List<Map<String, Object>> rows) {
    access.require("clients");
    Rules.check(!rows.isEmpty() && rows.size() <= 300, "IMPORT_LIMIT");
    List<Client> saved = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++)
      try {
        saved.add(save(null, rows.get(i)));
      } catch (Problem e) {
        throw new ImportProblem(e.status, e.getMessage(), i + 2);
      } catch (org.springframework.dao.DataIntegrityViolationException
          | jakarta.persistence.PersistenceException e) {
        throw new ImportProblem(409, "CONFLICT", i + 2);
      }
    return saved;
  }
}
