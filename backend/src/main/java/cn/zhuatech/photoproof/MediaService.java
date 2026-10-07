// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

/** 私有原片、真实水印预览与不可覆盖版本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MediaService {
  final Store db;
  final JobService jobs;
  final AccessService access;
  final AdminService admin;
  final Clock clock;
  final Path root;

  /** 连接私有磁盘与照片版本业务服务。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public MediaService(
      Store db,
      JobService jobs,
      AccessService access,
      AdminService admin,
      Clock clock,
      @Value("${photoproof.media-root}") String root) {
    this.db = db;
    this.jobs = jobs;
    this.access = access;
    this.admin = admin;
    this.clock = clock;
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  /** 流式上传有严格体积、真实格式与像素上限；回滚仅清理本次新文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> upload(
      Long jobId, Object version, String kind, Long photoId, MultipartFile file) {
    Rules.check(Set.of("PROOF", "FINAL").contains(kind), "INVALID_INPUT");
    var j = jobs.writable(jobId, version, kind.equals("PROOF") ? "upload" : "edit");
    String name = Rules.text(file.getOriginalFilename(), 160, true);
    Rules.check(
        !name.contains("/") && !name.contains("\\") && !name.equals(".") && !name.equals(".."),
        "INVALID_FILENAME");
    String lower = name.toLowerCase(Locale.ROOT);
    Rules.check(
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png"),
        "IMAGE_FORMAT");
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new Problem(413, "FILE_TOO_LARGE");
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new Problem(400, "UPLOAD_READ_FAILED");
    }
    if (bytes.length > 10 * 1024 * 1024) throw new Problem(413, "FILE_TOO_LARGE");
    boolean png =
        bytes.length >= 8
            && bytes[0] == (byte) 137
            && bytes[1] == 80
            && bytes[2] == 78
            && bytes[3] == 71
            && bytes[4] == 13
            && bytes[5] == 10
            && bytes[6] == 26
            && bytes[7] == 10;
    boolean jpg =
        bytes.length >= 3
            && bytes[0] == (byte) 255
            && bytes[1] == (byte) 216
            && bytes[2] == (byte) 255;
    Rules.check(
        png && lower.endsWith(".png") || jpg && (lower.endsWith(".jpg") || lower.endsWith(".jpeg")),
        "IMAGE_FORMAT");
    BufferedImage image;
    int width, height;
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new Problem(400, "IMAGE_FORMAT");
      var reader = readers.next();
      try {
        reader.setInput(input);
        width = reader.getWidth(0);
        height = reader.getHeight(0);
        Rules.check(
            width > 0
                && height > 0
                && width <= 10000
                && height <= 10000
                && (long) width * height <= 24000000,
            "IMAGE_DIMENSIONS");
        image = reader.read(0);
        if (image == null) throw new Problem(400, "IMAGE_FORMAT");
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new Problem(400, "IMAGE_FORMAT");
    }
    var existing = jobs.assets(j);
    Rules.check(
        existing.stream().mapToLong(m -> m.byteSize).sum() + bytes.length
            <= Long.parseLong(admin.setting("max_job_mb")) * 1024 * 1024,
        "JOB_STORAGE_LIMIT");
    String slot;
    int round;
    if (kind.equals("PROOF")) {
      Rules.check(j.state.equals("DRAFT"), "PROOFS_FROZEN");
      Rules.check(
          existing.stream().filter(m -> m.kind.equals("PROOF") && m.active).count() < 100,
          "PHOTO_LIMIT");
      slot = lower;
      round = 0;
      Rules.check(
          existing.stream()
              .noneMatch(m -> m.kind.equals("PROOF") && m.active && m.slotCode.equals(slot)),
          "FILENAME_DUPLICATE");
      photoId = null;
    } else {
      Rules.check(j.state.equals("RETOUCHING"), "RETOUCH_REQUIRED");
      var proof = db.get(MediaAsset.class, photoId);
      Rules.check(
          proof.jobId.equals(j.id)
              && proof.kind.equals("PROOF")
              && proof.active
              && jobs.picks(j).stream().anyMatch(s -> s.photoId.equals(proof.id) && s.selected),
          "PHOTO_INVALID");
      var latest =
          jobs.finals(j).stream().filter(m -> m.photoId.equals(proof.id)).findFirst().orElse(null);
      if (latest != null) {
        var review = jobs.latestReview(latest);
        Rules.check(review == null || !review.decision.equals("ACCEPT"), "APPROVED_VERSION_LOCKED");
      }
      slot = photoId.toString();
      round = j.roundNumber;
    }
    int revision =
        existing.stream()
                .filter(
                    m -> m.kind.equals(kind) && m.slotCode.equals(slot) && m.roundNumber == round)
                .mapToInt(m -> m.revisionNumber)
                .max()
                .orElse(0)
            + 1;
    String key = UUID.randomUUID().toString(), watermark = admin.setting("watermark");
    try {
      write(key, "original", bytes);
      write(key, "preview", render(image, 1600, watermark));
      write(key, "thumb", render(image, 400, watermark));
    } catch (RuntimeException e) {
      clean(key);
      throw e;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) clean(key);
          }
        });
    var m = new MediaAsset();
    m.jobId = j.id;
    m.kind = kind;
    m.photoId = photoId;
    m.roundNumber = round;
    m.revisionNumber = revision;
    m.slotCode = slot;
    m.filename = name;
    m.fileKey = key;
    m.contentType = png ? "image/png" : "image/jpeg";
    m.sha256 = hash(bytes);
    m.byteSize = bytes.length;
    m.width = width;
    m.height = height;
    m.watermark = watermark;
    m.createdBy = access.current().id;
    m.createdAt = clock.instant();
    db.save(m);
    j.version++;
    jobs.event(j, "UPLOAD_" + kind, j.state, m.id + ":" + name);
    return jobs.detailInternal(j);
  }

  /** 草稿移除为软删除，保留引用与磁盘历史，不覆盖旧文件。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Map<String, Object> remove(Long id, Long mediaId, Object version) {
    var j = jobs.writable(id, version, "upload");
    var m = db.get(MediaAsset.class, mediaId);
    Rules.check(
        j.state.equals("DRAFT") && m.jobId.equals(id) && m.kind.equals("PROOF") && m.active,
        "PROOFS_FROZEN");
    m.active = false;
    j.version++;
    jobs.event(j, "REMOVE_PROOF", j.state, m.id.toString());
    return jobs.detailInternal(j);
  }

  /** 授权内容由随机服务端键定位；客户端无法指定路径。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public AssetFile read(Long id, String variant) {
    Rules.check(Set.of("thumb", "preview", "original").contains(variant), "INVALID_INPUT");
    var m = db.get(MediaAsset.class, id);
    var j = db.get(PhotoJob.class, m.jobId);
    Rules.check(m.active, "PHOTO_REMOVED");
    if (access.has("portal")) {
      access.client(j);
      jobs.available(j);
      if (variant.equals("original")) {
        jobs.downloadable(j);
        Rules.check(
            m.kind.equals("FINAL") && jobs.finals(j).stream().anyMatch(x -> x.id.equals(m.id)),
            "CURRENT_VERSION_REQUIRED");
      } else if (m.kind.equals("FINAL"))
        Rules.check(
            Set.of("REVIEW", "READY", "DELIVERED", "CLOSED").contains(j.state), "FINALS_PRIVATE");
    } else {
      if (!(access.visible(j)
          && (access.has("jobs")
              || access.has("edit")
              || access.has("upload")
              || access.has("release")))) throw new Problem(403, "FORBIDDEN");
    }
    Path path = path(m.fileKey, variant);
    try {
      Rules.check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "FILE_MISSING");
      if (variant.equals("original"))
        Rules.check(
            Files.size(path) == m.byteSize && hash(Files.readAllBytes(path)).equals(m.sha256),
            "FILE_CORRUPT");
      return new AssetFile(
          path, m.filename, variant.equals("original") ? m.contentType : "image/jpeg", m.byteSize);
    } catch (IOException e) {
      throw new Problem(409, "FILE_MISSING");
    }
  }

  /** 内部授权文件描述，不直接序列化磁盘路径到客户端。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record AssetFile(Path path, String filename, String contentType, long size) {}

  /** ZIP只收当前已认可原片，以精确版本标识防重名。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Transactional(readOnly = true)
  public java.util.List<AssetFile> bundle(Long id) {
    var j = db.get(PhotoJob.class, id);
    jobs.downloadable(j);
    var finals = jobs.finals(j);
    Rules.check(
        finals.stream().mapToLong(m -> m.byteSize).sum() <= 500L * 1024 * 1024, "ZIP_LIMIT");
    return finals.stream()
        .map(
            m -> {
              var f = read(m.id, "original");
              return new AssetFile(
                  f.path(),
                  m.photoId + "-v" + m.revisionNumber + "-" + f.filename(),
                  f.contentType(),
                  f.size());
            })
        .toList();
  }

  Path path(String key, String variant) {
    Rules.check(
        key != null
            && key.matches("[0-9a-f-]{36}")
            && Set.of("original", "preview", "thumb").contains(variant),
        "INVALID_FILE_KEY");
    var dir = root.resolve(variant);
    var target = dir.resolve(key).normalize();
    Rules.check(
        target.startsWith(root)
            && !Files.isSymbolicLink(root)
            && !Files.isSymbolicLink(dir)
            && !Files.isSymbolicLink(target),
        "FILE_PATH_INVALID");
    return target;
  }

  void write(String key, String variant, byte[] bytes) {
    try {
      Path f = path(key, variant);
      Files.createDirectories(f.getParent());
      Files.write(f, bytes, StandardOpenOption.CREATE_NEW);
    } catch (IOException e) {
      throw new Problem(507, "STORAGE_WRITE_FAILED");
    }
  }

  void clean(String key) {
    for (String variant : java.util.List.of("original", "preview", "thumb"))
      try {
        Files.deleteIfExists(path(key, variant));
      } catch (Exception ignored) {
        /* Only newly generated UUID files are eligible; no committed files are deleted. */
      }
  }

  static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  byte[] render(BufferedImage src, int limit, String label) {
    double ratio = Math.min(1d, (double) limit / Math.max(src.getWidth(), src.getHeight()));
    int w = Math.max(1, (int) (src.getWidth() * ratio)),
        h = Math.max(1, (int) (src.getHeight() * ratio));
    var dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var g = dest.createGraphics();
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, w, h);
      g.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.drawImage(src, 0, 0, w, h, null);
      int font = Math.max(12, Math.min(48, w / 14));
      g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, font));
      g.setColor(new Color(0, 0, 0, 115));
      g.fillRect(0, Math.max(0, h - font * 2), w, font * 2);
      g.setColor(Color.WHITE);
      g.drawString(label, Math.max(3, w / 30), Math.max(font, h - font / 2));
    } finally {
      g.dispose();
    }
    try (var out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(dest, "jpeg", out)) throw new IOException();
      return out.toByteArray();
    } catch (IOException e) {
      throw new Problem(507, "PREVIEW_FAILED");
    }
  }
}
