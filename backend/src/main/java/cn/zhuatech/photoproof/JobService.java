// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 选片、冻结轮次、修图审批与真实交付状态；官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class JobService {
  final Store db;
  final AccessService access;
  final AdminService admin;
  final ClientService clients;
  final Clock clock;

  /** 连接项目、客户、权限和计费事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public JobService(
      Store db, AccessService access, AdminService admin, ClientService clients, Clock clock) {
    this.db = db;
    this.access = access;
    this.admin = admin;
    this.clients = clients;
    this.clock = clock;
  }

  /** 有权限的列表，客户与员工应用同一实时范围规则。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String mode) {
    String permission =
        switch (mode) {
          case "jobs" -> "jobs";
          case "edit" -> "edit";
          case "cash" -> "cash";
          case "portal" -> "portal";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    if (permission.equals("portal")) {
      if (!access.has("portal")) throw new Problem(403, "CLIENT_ONLY");
    } else access.require(permission);
    return db.all(PhotoJob.class).stream().filter(access::visible).map(this::summary).toList();
  }

  /** 操作前序列化并核对最新版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  PhotoJob writable(Long id, Object version, String permission) {
    admin.lock();
    var j = db.get(PhotoJob.class, id);
    if (permission.equals("portal")) {
      access.client(j);
      available(j);
    } else access.job(j, permission);
    Rules.check(j.version == Rules.id(version), "VERSION_CONFLICT");
    return j;
  }

  void available(PhotoJob j) {
    Rules.check(
        !j.state.equals("CANCELLED")
            && !j.state.equals("DRAFT")
            && j.galleryUntil != null
            && j.galleryUntil.isAfter(clock.instant())
            && db.get(Client.class, j.clientId).enabled
            && db.get(Department.class, j.departmentId).enabled,
        "GALLERY_UNAVAILABLE");
  }

  /** 创建及修改草稿套餐，冻结后不能偷偷改价或换客户。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> save(Long id, Map<String, Object> b) {
    access.require("jobs");
    admin.lock();
    var j = id == null ? new PhotoJob() : db.get(PhotoJob.class, id);
    if (id != null) {
      access.job(j, "jobs");
      Rules.check(j.version == Rules.id(b.get("version")), "VERSION_CONFLICT");
      Rules.check(j.state.equals("DRAFT"), "DRAFT_REQUIRED");
    }
    var c = db.get(Client.class, Rules.id(b.get("clientId")));
    Rules.check(c.enabled && clients.visible(c), "CLIENT_SCOPE_INVALID");
    var d = db.get(Department.class, c.departmentId);
    Rules.check(d.enabled, "DEPARTMENT_DISABLED");
    if (id != null) {
      Rules.check(j.departmentId.equals(c.departmentId), "IDENTITY_LOCKED");
      if (!j.clientId.equals(c.id))
        Rules.check(assets(j).isEmpty() && cash(j).isEmpty(), "REFERENCE_PROTECTED");
    }
    Long photographer = Rules.id(b.get("photographerId")), editor = Rules.id(b.get("editorId"));
    for (Long worker : List.of(photographer, editor)) {
      var a = db.get(Account.class, worker);
      Rules.check(
          a.enabled && a.kind.equals("STAFF") && a.departmentId.equals(c.departmentId),
          "WORKER_SCOPE_INVALID");
    }
    var photoRole = db.get(AccessRole.class, db.get(Account.class, photographer).roleId);
    var editRole = db.get(AccessRole.class, db.get(Account.class, editor).roleId);
    Rules.check(
        photoRole.permissions.contains("upload") && editRole.permissions.contains("edit"),
        "WORKER_PERMISSION_INVALID");
    String type = Rules.text(b.get("shootType"), 60, true);
    Rules.check(
        db.query(
                    DictionaryEntry.class,
                    "from DictionaryEntry where type='SHOOT_TYPE' and code=?1 and enabled=true",
                    type)
                .size()
            == 1,
        "UNKNOWN_SHOOT_TYPE");
    j.clientId = c.id;
    j.departmentId = c.departmentId;
    j.photographerId = photographer;
    j.editorId = editor;
    j.title = Rules.text(b.get("title"), 160, true);
    j.shootType = type;
    j.shootDate = LocalDate.parse(Rules.text(b.get("shootDate"), 10, true));
    j.minSelect = Rules.integer(b.get("minSelect"), 1, 100);
    j.includedCount = Rules.integer(b.get("includedCount"), j.minSelect, 100);
    j.maxSelect = Rules.integer(b.get("maxSelect"), j.includedCount, 100);
    j.baseAmount = Rules.money(b.get("baseAmount"));
    j.extraPrice = Rules.money(b.get("extraPrice"));
    j.amountDue = j.baseAmount;
    j.currency = d.currency;
    if (id == null) {
      j.createdBy = access.current().id;
      j.createdAt = clock.instant();
      db.save(j);
    } else j.version++;
    event(j, "SAVE", j.state, "");
    return summary(j);
  }

  List<MediaAsset> assets(PhotoJob j) {
    return db.query(MediaAsset.class, "from MediaAsset where jobId=?1 order by id", j.id);
  }

  List<PhotoSelection> picks(PhotoJob j) {
    return db.query(
        PhotoSelection.class,
        "from PhotoSelection where jobId=?1 and roundNumber=?2 order by photoId",
        j.id,
        j.roundNumber);
  }

  List<CashEntry> cash(PhotoJob j) {
    return db.query(CashEntry.class, "from CashEntry where jobId=?1 order by id", j.id);
  }

  List<MediaAsset> finals(PhotoJob j) {
    var map = new LinkedHashMap<Long, MediaAsset>();
    for (var m : assets(j))
      if (m.active && m.kind.equals("FINAL") && m.roundNumber == j.roundNumber)
        map.put(m.photoId, m);
    return new ArrayList<>(map.values());
  }

  PhotoReview latestReview(MediaAsset m) {
    var rows =
        db.query(PhotoReview.class, "from PhotoReview where mediaId=?1 order by id desc", m.id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  boolean accepted(PhotoJob j) {
    var selected = picks(j).stream().filter(s -> s.selected).map(s -> s.photoId).toList();
    var current = finals(j);
    return !selected.isEmpty()
        && current.size() == selected.size()
        && current.stream()
            .allMatch(
                m -> {
                  var r = latestReview(m);
                  return r != null && r.decision.equals("ACCEPT");
                });
  }

  /** 净收款来自不可覆盖的外部资金事实。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  BigDecimal paid(PhotoJob j) {
    var rows = cash(j);
    Set<Long> reversed = new HashSet<>();
    for (var c : rows) if (c.kind.equals("REVERSAL")) reversed.add(c.sourceId);
    BigDecimal sum = BigDecimal.ZERO.setScale(2);
    for (var c : rows)
      if (!reversed.contains(c.id)) {
        if (c.kind.equals("RECEIPT")) sum = sum.add(c.amount);
        if (c.kind.equals("REFUND")) sum = sum.subtract(c.amount);
      }
    return sum;
  }

  /** 下载资格每次重新核对，退款后不能继续取新文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void downloadable(PhotoJob j) {
    access.client(j);
    available(j);
    Rules.check(
        Set.of("DELIVERED", "CLOSED").contains(j.state)
            && paid(j).compareTo(j.amountDue) >= 0
            && accepted(j),
        "DOWNLOAD_LOCKED");
  }

  /** 单张选片与备注，约束数量并拒绝修改冻结轮次。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> select(Long id, Map<String, Object> b) {
    var j = writable(id, b.get("version"), "portal");
    Rules.check(j.state.equals("SELECTING"), "SELECTION_FROZEN");
    var photo = db.get(MediaAsset.class, Rules.id(b.get("photoId")));
    Rules.check(
        photo.jobId.equals(id) && photo.kind.equals("PROOF") && photo.active, "PHOTO_INVALID");
    boolean selected = Rules.flag(b.get("selected"));
    String note = Rules.text(b.get("note"), 1000, false);
    var rows = picks(j);
    var s =
        rows.stream()
            .filter(x -> x.photoId.equals(photo.id))
            .findFirst()
            .orElseGet(PhotoSelection::new);
    if (selected && !s.selected || selected && s.id == null)
      Rules.check(rows.stream().filter(x -> x.selected).count() < j.maxSelect, "SELECTION_LIMIT");
    s.jobId = id;
    s.photoId = photo.id;
    s.roundNumber = j.roundNumber;
    s.selected = selected;
    s.note = note;
    s.actor = access.actor();
    s.updatedAt = clock.instant();
    if (s.id == null) db.save(s);
    j.version++;
    event(j, "SELECT", j.state, photo.id.toString());
    return detailInternal(j);
  }

  /** 带明确费用确认的状态操作，客户动作不能被员工代理。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> action(Long id, String action, Map<String, Object> b) {
    String permission =
        switch (action) {
          case "submit", "close" -> "portal";
          case "publish", "release", "reopen", "extend", "cancel" -> "release";
          case "review" -> "edit";
          default -> throw new Problem(404, "NOT_FOUND");
        };
    var j = writable(id, b.get("version"), permission);
    String old = j.state, note = "";
    switch (action) {
      case "publish":
        Rules.check(j.state.equals("DRAFT"), "DRAFT_REQUIRED");
        Rules.check(
            assets(j).stream().filter(m -> m.active && m.kind.equals("PROOF")).count()
                >= j.minSelect,
            "PHOTOS_REQUIRED");
        j.galleryUntil =
            clock
                .instant()
                .atZone(ZoneId.of(db.get(Department.class, j.departmentId).zone))
                .plusDays(Integer.parseInt(admin.setting("gallery_days")))
                .toInstant();
        j.state = "SELECTING";
        break;
      case "submit":
        Rules.check(j.state.equals("SELECTING"), "SELECTION_FROZEN");
        long count = picks(j).stream().filter(s -> s.selected).count();
        Rules.check(count >= j.minSelect && count <= j.maxSelect, "SELECTION_COUNT");
        var due = Rules.due(j.baseAmount, j.extraPrice, j.includedCount, count);
        Rules.check(
            Rules.flag(b.get("confirmed")) && Rules.money(b.get("amount")).equals(due),
            "QUOTE_CONFIRMATION_REQUIRED");
        j.amountDue = due;
        j.state = "RETOUCHING";
        break;
      case "review":
        Rules.check(j.state.equals("RETOUCHING"), "RETOUCH_REQUIRED");
        Rules.check(
            finals(j).size() == picks(j).stream().filter(s -> s.selected).count(),
            "FINALS_REQUIRED");
        j.state = "REVIEW";
        break;
      case "release":
        Rules.check(j.state.equals("READY") && accepted(j), "CLIENT_APPROVAL_REQUIRED");
        Rules.check(paid(j).compareTo(j.amountDue) >= 0, "PAYMENT_REQUIRED");
        available(j);
        j.state = "DELIVERED";
        j.releasedAt = clock.instant();
        break;
      case "close":
        Rules.check(j.state.equals("DELIVERED"), "DELIVERY_REQUIRED");
        downloadable(j);
        j.state = "CLOSED";
        j.closedAt = clock.instant();
        break;
      case "reopen":
        Rules.check(Set.of("RETOUCHING", "REVIEW", "READY").contains(j.state), "REOPEN_FORBIDDEN");
        note = Rules.text(b.get("note"), 1000, true);
        Rules.check(paid(j).compareTo(j.baseAmount) <= 0, "REFUND_FIRST");
        var prior = picks(j);
        j.roundNumber++;
        for (var p : prior) {
          var s = new PhotoSelection();
          s.jobId = id;
          s.roundNumber = j.roundNumber;
          s.photoId = p.photoId;
          s.selected = p.selected;
          s.note = p.note;
          s.actor = access.actor();
          s.updatedAt = clock.instant();
          db.save(s);
        }
        j.amountDue = j.baseAmount;
        j.state = "SELECTING";
        break;
      case "extend":
        Rules.check(!Set.of("CANCELLED", "DRAFT", "CLOSED").contains(j.state), "EXTEND_FORBIDDEN");
        note = Rules.text(b.get("note"), 1000, true);
        int days = Rules.integer(b.get("days"), 1, 365);
        j.galleryUntil =
            clock
                .instant()
                .atZone(ZoneId.of(db.get(Department.class, j.departmentId).zone))
                .plusDays(days)
                .toInstant();
        break;
      case "cancel":
        Rules.check(
            !Set.of("CANCELLED", "DELIVERED", "CLOSED").contains(j.state), "CANCEL_FORBIDDEN");
        note = Rules.text(b.get("note"), 1000, true);
        j.state = "CANCELLED";
        break;
      default:
        throw new Problem(404, "NOT_FOUND");
    }
    j.version++;
    event(j, action.toUpperCase(Locale.ROOT), old, note);
    return detailInternal(j);
  }

  /** 认可当前精确版本或有理由地退回；历史文件不能顶替最新交付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> review(Long id, Map<String, Object> b) {
    var j = writable(id, b.get("version"), "portal");
    Rules.check(Set.of("REVIEW", "READY").contains(j.state), "REVIEW_REQUIRED");
    Long mediaId = Rules.id(b.get("mediaId"));
    var m =
        finals(j).stream()
            .filter(x -> x.id.equals(mediaId))
            .findFirst()
            .orElseThrow(() -> new Problem(409, "CURRENT_VERSION_REQUIRED"));
    String decision = Rules.text(b.get("decision"), 20, true);
    Rules.check(Set.of("ACCEPT", "CHANGES").contains(decision), "INVALID_INPUT");
    String note = Rules.text(b.get("note"), 1000, decision.equals("CHANGES"));
    var r = new PhotoReview();
    r.jobId = id;
    r.mediaId = m.id;
    r.decision = decision;
    r.note = note;
    r.actor = access.actor();
    r.createdAt = clock.instant();
    db.save(r);
    db.flush();
    String old = j.state;
    j.state = decision.equals("CHANGES") ? "RETOUCHING" : accepted(j) ? "READY" : "REVIEW";
    j.version++;
    event(j, "CLIENT_" + decision, old, m.id + ":" + note);
    return detailInternal(j);
  }

  /** 账目只能登记已核实外部事实；引用链保护退款与冲销。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> recordCash(Long id, Map<String, Object> b) {
    var j = writable(id, b.get("version"), "cash");
    String kind = Rules.text(b.get("kind"), 20, true);
    Rules.check(Set.of("RECEIPT", "REFUND", "REVERSAL").contains(kind), "INVALID_INPUT");
    var amount = Rules.money(b.get("amount"));
    Rules.check(amount.signum() > 0, "INVALID_AMOUNT");
    var rows = cash(j);
    Long sourceId = null;
    if (kind.equals("RECEIPT")) {
      Rules.check(!j.state.equals("CANCELLED"), "CANCELLED_RECEIPT");
      Rules.check(paid(j).add(amount).compareTo(j.amountDue) <= 0, "OVERPAYMENT");
    } else {
      sourceId = Rules.id(b.get("sourceId"));
      var source = db.get(CashEntry.class, sourceId);
      Rules.check(
          source.jobId.equals(id)
              && !source.kind.equals("REVERSAL")
              && rows.stream()
                  .noneMatch(c -> c.kind.equals("REVERSAL") && c.sourceId.equals(source.id)),
          "SOURCE_INVALID");
      if (kind.equals("REVERSAL")) {
        Rules.check(amount.equals(source.amount), "REVERSAL_AMOUNT");
        Rules.check(
            rows.stream().noneMatch(c -> Objects.equals(c.sourceId, source.id)),
            "REFUND_DEPENDENCY");
        if (source.kind.equals("REFUND"))
          Rules.check(paid(j).add(amount).compareTo(j.amountDue) <= 0, "OVERPAYMENT");
      } else {
        Rules.check(source.kind.equals("RECEIPT"), "SOURCE_INVALID");
        BigDecimal used =
            rows.stream()
                .filter(
                    c ->
                        c.kind.equals("REFUND")
                            && Objects.equals(c.sourceId, source.id)
                            && rows.stream()
                                .noneMatch(
                                    x ->
                                        x.kind.equals("REVERSAL")
                                            && Objects.equals(x.sourceId, c.id)))
                .map(c -> c.amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Rules.check(used.add(amount).compareTo(source.amount) <= 0, "REFUND_LIMIT");
      }
    }
    String reference = Rules.text(b.get("reference"), 120, true);
    Rules.check(
        rows.stream().noneMatch(c -> c.reference.equalsIgnoreCase(reference)),
        "REFERENCE_DUPLICATE");
    var c = new CashEntry();
    c.jobId = id;
    c.departmentId = j.departmentId;
    c.kind = kind;
    c.sourceId = sourceId;
    c.amount = amount;
    c.reference = reference;
    c.note = Rules.text(b.get("note"), 600, true);
    c.actor = access.actor();
    c.createdAt = clock.instant();
    db.save(c);
    j.version++;
    event(j, "CASH_" + kind, j.state, reference);
    return detailInternal(j);
  }

  /** 可追溯事件与业务写入处于同一事务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  void event(PhotoJob j, String action, String from, String note) {
    var e = new JobEvent();
    e.jobId = j.id;
    e.action = action;
    e.fromState = from;
    e.toState = j.state;
    e.roundNumber = j.roundNumber;
    e.note = note;
    e.actor = access.actor();
    e.createdAt = clock.instant();
    db.save(e);
    access.audit(action, j.id, j.departmentId);
  }

  Map<String, Object> summary(PhotoJob j) {
    var m = new LinkedHashMap<String, Object>();
    m.put("job", j);
    m.put("clientName", db.get(Client.class, j.clientId).name);
    m.put("photographer", db.get(Account.class, j.photographerId).displayName);
    m.put("editor", db.get(Account.class, j.editorId).displayName);
    m.put("paid", paid(j));
    m.put("selected", picks(j).stream().filter(s -> s.selected).count());
    m.put("photoCount", assets(j).stream().filter(x -> x.active && x.kind.equals("PROOF")).count());
    m.put("expired", j.galleryUntil != null && !j.galleryUntil.isAfter(clock.instant()));
    return m;
  }

  /** 详情按业务角色选择字段，不向收款专员泄露照片私有路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> detail(Long id, String mode) {
    var j = db.get(PhotoJob.class, id);
    if (mode.equals("portal")) {
      access.client(j);
      available(j);
    } else {
      if (!Set.of("jobs", "edit", "cash").contains(mode)) throw new Problem(404, "NOT_FOUND");
      access.job(j, mode);
    }
    return detailInternal(j);
  }

  Map<String, Object> detailInternal(PhotoJob j) {
    var m = summary(j);
    boolean client = access.has("portal"),
        photos =
            client
                || access.has("jobs")
                || access.has("edit")
                || access.has("upload")
                || access.has("release");
    if (photos) {
      boolean showFinal =
          !client || Set.of("REVIEW", "READY", "DELIVERED", "CLOSED").contains(j.state);
      var media =
          assets(j).stream()
              .filter(x -> x.active && (x.kind.equals("PROOF") || showFinal))
              .map(
                  x -> {
                    var out = new LinkedHashMap<String, Object>();
                    out.put("id", x.id);
                    out.put("kind", x.kind);
                    out.put("photoId", x.photoId);
                    out.put("filename", x.filename);
                    out.put("roundNumber", x.roundNumber);
                    out.put("revisionNumber", x.revisionNumber);
                    out.put("byteSize", x.byteSize);
                    out.put("width", x.width);
                    out.put("height", x.height);
                    out.put("watermark", x.watermark);
                    out.put("createdAt", x.createdAt);
                    var r = latestReview(x);
                    out.put("review", r);
                    return out;
                  })
              .toList();
      m.put("media", media);
      m.put("selection", picks(j));
      m.put(
          "selectionHistory",
          client
              ? List.of()
              : db.query(
                  PhotoSelection.class,
                  "from PhotoSelection where jobId=?1 order by roundNumber,photoId",
                  j.id));
    }
    m.put("cash", client ? List.of() : access.has("cash") ? cash(j) : List.of());
    m.put(
        "events",
        client
            ? List.of()
            : db.query(JobEvent.class, "from JobEvent where jobId=?1 order by id", j.id));
    m.put(
        "quote",
        Rules.due(
            j.baseAmount,
            j.extraPrice,
            j.includedCount,
            picks(j).stream().filter(s -> s.selected).count()));
    m.put(
        "downloadable",
        Set.of("DELIVERED", "CLOSED").contains(j.state)
            && paid(j).compareTo(j.amountDue) >= 0
            && accepted(j)
            && j.galleryUntil != null
            && j.galleryUntil.isAfter(clock.instant()));
    return m;
  }

  /** 表单目录只给拥有业务权限的员工，账号信息限定显示名与ID。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> directory() {
    access.require("jobs");
    var m = new LinkedHashMap<String, Object>();
    m.put(
        "clients",
        db.all(Client.class).stream()
            .filter(c -> c.enabled && clients.visible(c))
            .map(
                c ->
                    Map.of(
                        "id", c.id, "name", c.name, "code", c.code, "departmentId", c.departmentId))
            .toList());
    m.put(
        "workers",
        db.all(Account.class).stream()
            .filter(
                a ->
                    a.enabled && a.kind.equals("STAFF") && access.visibleDepartment(a.departmentId))
            .map(
                a ->
                    Map.of(
                        "id",
                        a.id,
                        "name",
                        a.displayName,
                        "departmentId",
                        a.departmentId,
                        "permissions",
                        db.get(AccessRole.class, a.roleId).permissions))
            .toList());
    m.put("types", db.all(DictionaryEntry.class).stream().filter(d -> d.enabled).toList());
    return m;
  }

  /** 工作室经营报告按币种分组，绝不把不同货币相加。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public Map<String, Object> reports() {
    access.require("reports");
    var jobs = db.all(PhotoJob.class).stream().filter(access::visible).toList();
    var totals = new TreeMap<String, Map<String, Object>>();
    for (var j : jobs) {
      var row =
          totals.computeIfAbsent(
              j.currency,
              k ->
                  new LinkedHashMap<>(
                      Map.of(
                          "jobs",
                          0,
                          "due",
                          BigDecimal.ZERO.setScale(2),
                          "paid",
                          BigDecimal.ZERO.setScale(2))));
      row.put("jobs", (int) row.get("jobs") + 1);
      row.put(
          "due",
          ((BigDecimal) row.get("due"))
              .add(j.state.equals("CANCELLED") ? BigDecimal.ZERO : j.amountDue));
      row.put("paid", ((BigDecimal) row.get("paid")).add(paid(j)));
    }
    return Map.of("totals", totals, "jobs", jobs.stream().map(this::summary).toList());
  }

  /** 审计按部门及指派项目范围过滤。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public List<AuditEvent> audit() {
    access.require("audit");
    return db.all(AuditEvent.class).stream()
        .filter(
            e ->
                access.visibleDepartment(e.departmentId)
                    && (!access.role().scope.equals("ASSIGNED") || e.actor.equals(access.actor())))
        .toList();
  }
}
