// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单项目限时分享，数据库仅保存令牌散列及PIN散列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class ShareService {
  final Store db;
  final JobService jobs;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final BCryptPasswordEncoder encoder;
  final SecureRandom random = new SecureRandom();
  final Map<String, Attempt> attempts = new LinkedHashMap<>();

  record Attempt(int count, Instant until) {}

  /** 连接限时分享与会话授权服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ShareService(
      Store db,
      JobService jobs,
      AccessService access,
      AdminService admin,
      Clock clock,
      BCryptPasswordEncoder encoder) {
    this.db = db;
    this.jobs = jobs;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
    this.encoder = encoder;
  }

  /** 分享凭据仅首次返回，过期不超过画廊期限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> create(Long id, Object version) {
    var j = jobs.writable(id, version, "release");
    jobs.available(j);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = HexFormat.of().formatHex(bytes),
        pin = String.format(Locale.ROOT, "%08d", random.nextInt(100000000));
    var g = new ShareGrant();
    g.jobId = id;
    g.tokenHash = MediaService.hash(token.getBytes(StandardCharsets.UTF_8));
    g.pinHash = encoder.encode(pin);
    var expire =
        clock.instant().plus(Duration.ofHours(Integer.parseInt(admin.setting("link_hours"))));
    g.expiresAt = expire.isBefore(j.galleryUntil) ? expire : j.galleryUntil;
    g.createdBy = access.current().id;
    g.createdAt = clock.instant();
    db.save(g);
    j.version++;
    jobs.event(j, "SHARE_CREATE", j.state, g.id.toString());
    return Map.of(
        "id", g.id, "token", token, "pin", pin, "expiresAt", g.expiresAt, "version", j.version);
  }

  /** 已撤销授权立即使对应会话失效。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> revoke(Long jobId, Long grantId, Object version) {
    var j = jobs.writable(jobId, version, "release");
    var g = db.get(ShareGrant.class, grantId);
    Rules.check(g.jobId.equals(j.id), "OUT_OF_SCOPE");
    g.enabled = false;
    j.version++;
    jobs.event(j, "SHARE_REVOKE", j.state, g.id.toString());
    return jobs.detailInternal(j);
  }

  @Transactional(readOnly = true)
  /** 只读取有交付权限项目的分享状态，不返回凭据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public java.util.List<ShareGrant> list(Long jobId) {
    var j = db.get(PhotoJob.class, jobId);
    access.job(j, "release");
    return db.query(ShareGrant.class, "from ShareGrant where jobId=?1 order by id", jobId);
  }

  /** CSRF保护的凭据交换，限速且更换会话ID，不采用URL查询参数。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public synchronized Map<String, Object> exchange(
      Map<String, Object> b, HttpServletRequest request, HttpServletResponse response) {
    String token = Rules.text(b.get("token"), 64, true), pin = Rules.text(b.get("pin"), 8, true);
    if (!token.matches("[0-9a-f]{64}") || !pin.matches("[0-9]{8}"))
      throw new Problem(401, "SHARE_INVALID");
    String hash = MediaService.hash(token.getBytes(StandardCharsets.UTF_8)),
        key =
            MediaService.hash(
                (request.getRemoteAddr() + ":" + hash).getBytes(StandardCharsets.UTF_8));
    var now = clock.instant();
    attempts.entrySet().removeIf(e -> !e.getValue().until.isAfter(now));
    var a = attempts.get(key);
    if (a != null && a.count >= 8) throw new Problem(429, "TRY_LATER");
    var rows = db.query(ShareGrant.class, "from ShareGrant where tokenHash=?1", hash);
    var g = rows.isEmpty() ? null : rows.getFirst();
    if (g == null || !g.enabled || !g.expiresAt.isAfter(now) || !encoder.matches(pin, g.pinHash)) {
      if (attempts.size() >= 4096) attempts.remove(attempts.keySet().iterator().next());
      attempts.put(
          key,
          new Attempt(a == null ? 1 : a.count + 1, a == null ? now.plusSeconds(300) : a.until));
      throw new Problem(401, "SHARE_INVALID");
    }
    var j = db.get(PhotoJob.class, g.jobId);
    jobs.available(j);
    attempts.remove(key);
    request.getSession(true);
    request.changeSessionId();
    var auth = new UsernamePasswordAuthenticationToken("share:" + g.id, null, java.util.List.of());
    auth.setDetails(new AccessService.ShareSession(g.id));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    access.audit("SHARE_LOGIN", g.id, j.departmentId);
    return access.profile();
  }
}
