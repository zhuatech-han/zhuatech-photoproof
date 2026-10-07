// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** 员工、客户、后台与私有文件HTTP入口；业务权限由服务实时核查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@RestController
@RequestMapping("/api")
public class ApiController {
  final JobService jobs;
  final ClientService clients;
  final AdminService admin;
  final MediaService media;
  final ShareService shares;

  /** 连接员工、客户、照片与后台HTTP业务入口。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public ApiController(
      JobService jobs,
      ClientService clients,
      AdminService admin,
      MediaService media,
      ShareService shares) {
    this.jobs = jobs;
    this.clients = clients;
    this.admin = admin;
    this.media = media;
    this.shares = shares;
  }

  @GetMapping("/jobs")
  Object list(@RequestParam(defaultValue = "jobs") String mode) {
    return jobs.list(mode);
  }

  @GetMapping("/jobs/{id}")
  Object detail(@PathVariable Long id, @RequestParam(defaultValue = "jobs") String mode) {
    return jobs.detail(id, mode);
  }

  @PostMapping("/jobs")
  Object create(@RequestBody Map<String, Object> b) {
    return jobs.save(null, b);
  }

  @PutMapping("/jobs/{id}")
  Object save(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return jobs.save(id, b);
  }

  @PostMapping("/jobs/{id}/actions/{action}")
  Object action(
      @PathVariable Long id, @PathVariable String action, @RequestBody Map<String, Object> b) {
    return jobs.action(id, action, b);
  }

  @PutMapping("/jobs/{id}/selection")
  Object select(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return jobs.select(id, b);
  }

  @PostMapping("/jobs/{id}/reviews")
  Object review(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return jobs.review(id, b);
  }

  @PostMapping("/jobs/{id}/cash")
  Object cash(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return jobs.recordCash(id, b);
  }

  @PostMapping("/jobs/{id}/media")
  Object upload(
      @PathVariable Long id,
      @RequestParam long version,
      @RequestParam String kind,
      @RequestParam(required = false) Long photoId,
      @RequestParam MultipartFile file) {
    return media.upload(id, version, kind, photoId, file);
  }

  @DeleteMapping("/jobs/{id}/media/{mediaId}")
  Object remove(@PathVariable Long id, @PathVariable Long mediaId, @RequestParam long version) {
    return media.remove(id, mediaId, version);
  }

  @GetMapping("/media/{id}/{variant}")
  ResponseEntity<?> file(@PathVariable Long id, @PathVariable String variant) {
    var f = media.read(id, variant);
    var headers = new HttpHeaders();
    headers.setCacheControl("private, no-store");
    headers.set("X-Content-Type-Options", "nosniff");
    headers.setContentType(MediaType.parseMediaType(f.contentType()));
    if (variant.equals("original"))
      headers.setContentDisposition(
          ContentDisposition.attachment().filename(f.filename(), StandardCharsets.UTF_8).build());
    return ResponseEntity.ok().headers(headers).body(new FileSystemResource(f.path()));
  }

  /** 原生流式下载预检，不生成ZIP正文；下载GET仍重新鉴权。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @RequestMapping(value = "/jobs/{id}/download", method = RequestMethod.HEAD)
  ResponseEntity<Void> zipHead(@PathVariable Long id) {
    media.bundle(id);
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("Content-Disposition", "attachment; filename=photoproof-" + id + ".zip")
        .contentType(MediaType.parseMediaType("application/zip"))
        .build();
  }

  @GetMapping("/jobs/{id}/download")
  ResponseEntity<StreamingResponseBody> zip(@PathVariable Long id) {
    var files = media.bundle(id);
    StreamingResponseBody body =
        out -> {
          try (var zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (var f : files) {
              zip.putNextEntry(new ZipEntry(f.filename()));
              Files.copy(f.path(), zip);
              zip.closeEntry();
            }
            zip.finish();
          }
        };
    return ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .header("Content-Disposition", "attachment; filename=photoproof-" + id + ".zip")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(body);
  }

  @GetMapping("/directory")
  Object directory() {
    return jobs.directory();
  }

  @GetMapping("/clients")
  Object clients() {
    return clients.list();
  }

  @PostMapping("/clients")
  Object createClient(@RequestBody Map<String, Object> b) {
    return clients.save(null, b);
  }

  @PutMapping("/clients/{id}")
  Object updateClient(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return clients.save(id, b);
  }

  @PostMapping("/clients/import")
  Object importClients(@RequestBody List<Map<String, Object>> rows) {
    return clients.importRows(rows);
  }

  @GetMapping("/admin-options")
  Object options() {
    return admin.options();
  }

  @GetMapping("/admin/{kind}")
  Object admin(@PathVariable String kind) {
    return admin.read(kind);
  }

  @PostMapping("/admin/{kind}")
  Object createAdmin(@PathVariable String kind, @RequestBody Map<String, Object> b) {
    return admin.save(kind, null, b);
  }

  @PutMapping("/admin/{kind}/{id}")
  Object updateAdmin(
      @PathVariable String kind, @PathVariable Long id, @RequestBody Map<String, Object> b) {
    return admin.save(kind, id, b);
  }

  @GetMapping("/reports")
  Object reports() {
    return jobs.reports();
  }

  @GetMapping("/audit")
  Object audit() {
    return jobs.audit();
  }

  @PostMapping("/jobs/{id}/shares")
  Object share(@PathVariable Long id, @RequestBody Map<String, Object> b) {
    return shares.create(id, b.get("version"));
  }

  @GetMapping("/jobs/{id}/shares")
  Object grants(@PathVariable Long id) {
    return shares.list(id);
  }

  @DeleteMapping("/jobs/{id}/shares/{grantId}")
  Object revoke(@PathVariable Long id, @PathVariable Long grantId, @RequestParam long version) {
    return shares.revoke(id, grantId, version);
  }

  @PostMapping("/share/exchange")
  Object exchange(
      @RequestBody Map<String, Object> b, HttpServletRequest req, HttpServletResponse res) {
    return shares.exchange(b, req, res);
  }
}
