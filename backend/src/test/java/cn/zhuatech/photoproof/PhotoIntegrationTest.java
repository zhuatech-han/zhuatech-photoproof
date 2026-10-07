// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.photoproof;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真HTTP/JPA和实际图片文件验证，所有客户均为TEST。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class PhotoIntegrationTest {
  static final String PASSWORD = "Aa9" + UUID.randomUUID();
  static final Instant NOW = Instant.parse("2026-10-07T04:00:00Z");
  static final Path MEDIA = temp();

  static Path temp() {
    try {
      return Files.createTempDirectory("photoproof-test-");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add(
        "spring.datasource.url",
        () -> "jdbc:h2:mem:photo;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    r.add("spring.datasource.username", () -> "sa");
    r.add("spring.datasource.password", () -> "");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
    r.add("photoproof.admin-password", () -> PASSWORD);
    r.add("photoproof.media-root", () -> MEDIA.toString());
  }

  @Autowired MockMvc mvc;
  @MockitoBean Clock clock;
  final JsonMapper json = JsonMapper.builder().build();
  MockHttpSession admin, client, other, editor;
  String suffix;
  long clientId, editorId;
  JsonNode detail;

  @BeforeEach
  void setup() throws Exception {
    when(clock.instant()).thenReturn(NOW);
    suffix = UUID.randomUUID().toString().substring(0, 8);
    admin = login("admin");
    clientId = createClient("C" + suffix, 1);
    long otherId = createClient("O" + suffix, 1);
    user("client" + suffix, "Client", "CLIENT", clientId, 1);
    user("other" + suffix, "Client", "CLIENT", otherId, 1);
    editorId = user("editor" + suffix, "Retoucher", "STAFF", null, 1);
    client = login("client" + suffix);
    other = login("other" + suffix);
    editor = login("editor" + suffix);
    detail =
        call(
            admin,
            "/jobs",
            "POST",
            m(
                "title",
                "TEST photo " + suffix,
                "clientId",
                clientId,
                "photographerId",
                1,
                "editorId",
                editorId,
                "shootType",
                "PORTRAIT",
                "shootDate",
                "2026-10-07",
                "minSelect",
                1,
                "includedCount",
                1,
                "maxSelect",
                2,
                "baseAmount",
                "100.01",
                "extraPrice",
                "20.02"),
            200);
  }

  Map<String, Object> m(Object... vs) {
    var out = new LinkedHashMap<String, Object>();
    for (int i = 0; i < vs.length; i += 2) out.put(vs[i].toString(), vs[i + 1]);
    return out;
  }

  JsonNode call(MockHttpSession s, String path, String method, Object b, int expected)
      throws Exception {
    var req =
        switch (method) {
          case "POST" -> post("/api" + path);
          case "PUT" -> put("/api" + path);
          case "DELETE" -> delete("/api" + path);
          default -> get("/api" + path);
        };
    if (s != null) req.session(s);
    if (!method.equals("GET")) req.with(csrf());
    if (b != null) req.contentType("application/json").content(json.writeValueAsString(b));
    var res = mvc.perform(req).andReturn().getResponse();
    assertEquals(expected, res.getStatus(), method + path + " " + res.getContentAsString());
    return json.readTree(res.getContentAsString().isBlank() ? "{}" : res.getContentAsString());
  }

  MockHttpSession login(String username) throws Exception {
    var r =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(m("username", username, "password", PASSWORD))))
            .andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  long role(String name) throws Exception {
    for (var r : call(admin, "/admin/roles", "GET", null, 200))
      if (r.get("name").asString().endsWith(name)) return r.get("id").asLong();
    throw new AssertionError(name);
  }

  long user(String name, String role, String kind, Long c, long dept) throws Exception {
    return call(
            admin,
            "/admin/users",
            "POST",
            m(
                "username",
                name,
                "displayName",
                "TEST " + name,
                "kind",
                kind,
                "clientId",
                c,
                "departmentId",
                dept,
                "roleId",
                role(role),
                "password",
                PASSWORD,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  long createClient(String code, long dept) throws Exception {
    return call(
            admin,
            "/clients",
            "POST",
            m(
                "code",
                code,
                "name",
                "TEST " + code,
                "contact",
                "",
                "departmentId",
                dept,
                "enabled",
                true),
            200)
        .get("id")
        .asLong();
  }

  long id() {
    return detail.get("job").get("id").asLong();
  }

  long version() {
    return detail.get("job").get("version").asLong();
  }

  byte[] image() throws Exception {
    var img = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < 320; x++)
      for (int y = 0; y < 240; y++) img.setRGB(x, y, ((x % 256) << 16) | ((y % 256) << 8) | 80);
    var out = new ByteArrayOutputStream();
    ImageIO.write(img, "jpeg", out);
    return out.toByteArray();
  }

  long upload(String kind, Long photo, String filename, byte[] bytes, MockHttpSession s, int status)
      throws Exception {
    var req =
        multipart("/api/jobs/" + id() + "/media")
            .file(new MockMultipartFile("file", filename, "image/jpeg", bytes))
            .param("kind", kind)
            .param("version", String.valueOf(version()))
            .session(s)
            .with(csrf());
    if (photo != null) req.param("photoId", photo.toString());
    var res = mvc.perform(req).andReturn().getResponse();
    assertEquals(status, res.getStatus(), res.getContentAsString());
    if (status != 200) return 0;
    detail = json.readTree(res.getContentAsString());
    return detail.get("media").get(detail.get("media").size() - 1).get("id").asLong();
  }

  JsonNode action(MockHttpSession s, String action, Map<String, Object> b, int status)
      throws Exception {
    b.put("version", version());
    var out = call(s, "/jobs/" + id() + "/actions/" + action, "POST", b, status);
    if (status == 200) detail = out;
    return out;
  }

  void publish() throws Exception {
    upload("PROOF", null, "one.jpg", image(), admin, 200);
    action(admin, "publish", m(), 200);
  }

  long proof() {
    for (var x : detail.get("media"))
      if (x.get("kind").asString().equals("PROOF")) return x.get("id").asLong();
    throw new AssertionError();
  }

  void select(long proof, boolean selected, int status) throws Exception {
    var r =
        call(
            client,
            "/jobs/" + id() + "/selection",
            "PUT",
            m("version", version(), "photoId", proof, "selected", selected, "note", "TEST note"),
            status);
    if (status == 200) detail = r;
  }

  long retouch() throws Exception {
    publish();
    select(proof(), true, 200);
    action(client, "submit", m("confirmed", true, "amount", "100.01"), 200);
    long finalId = upload("FINAL", proof(), "final.jpg", image(), editor, 200);
    action(editor, "review", m(), 200);
    return finalId;
  }

  void approve(long finalId, String decision, int status) throws Exception {
    var r =
        call(
            client,
            "/jobs/" + id() + "/reviews",
            "POST",
            m(
                "version",
                version(),
                "mediaId",
                finalId,
                "decision",
                decision,
                "note",
                decision.equals("CHANGES") ? "TEST fix exposure" : ""),
            status);
    if (status == 200) detail = r;
  }

  void receipt(String amount) throws Exception {
    detail =
        call(
            admin,
            "/jobs/" + id() + "/cash",
            "POST",
            m(
                "version",
                version(),
                "kind",
                "RECEIPT",
                "amount",
                amount,
                "reference",
                "TEST receipt " + suffix,
                "note",
                "TEST verified external cash"),
            200);
  }

  @Test
  void fullDeliveryUsesExactOriginalAndClientApproval() throws Exception {
    long f = retouch();
    approve(f, "ACCEPT", 200);
    action(admin, "release", m(), 409);
    receipt("100.01");
    action(admin, "release", m(), 200);
    var headResponse =
        mvc.perform(head("/api/jobs/" + id() + "/download").session(client))
            .andReturn()
            .getResponse();
    assertEquals(200, headResponse.getStatus());
    assertEquals(0, headResponse.getContentAsByteArray().length);
    var res =
        mvc.perform(get("/api/media/" + f + "/original").session(client)).andReturn().getResponse();
    assertEquals(200, res.getStatus());
    assertArrayEquals(image(), res.getContentAsByteArray());
    assertTrue(res.getHeader("Cache-Control").contains("no-store"));
    action(client, "close", m(), 200);
    assertEquals("CLOSED", detail.get("job").get("state").asString());
  }

  @Test
  void previewWatermarkedAndSourceHiddenUntilDelivery() throws Exception {
    publish();
    long p = proof();
    var res =
        mvc.perform(get("/api/media/" + p + "/preview").session(client)).andReturn().getResponse();
    assertEquals(200, res.getStatus());
    assertFalse(Arrays.equals(image(), res.getContentAsByteArray()));
    assertNotNull(ImageIO.read(new ByteArrayInputStream(res.getContentAsByteArray())));
    call(client, "/media/" + p + "/original", "GET", null, 409);
    assertFalse(detail.toString().contains("fileKey"));
  }

  @Test
  void otherClientCannotReadSelectOrDownload() throws Exception {
    publish();
    call(other, "/jobs/" + id() + "?mode=portal", "GET", null, 403);
    call(other, "/media/" + proof() + "/preview", "GET", null, 403);
    assertEquals(0, call(other, "/jobs?mode=portal", "GET", null, 200).size());
  }

  @Test
  void employeeCannotForgeClientSelectionOrAcceptance() throws Exception {
    publish();
    call(
        admin,
        "/jobs/" + id() + "/selection",
        "PUT",
        m("version", version(), "photoId", proof(), "selected", true, "note", ""),
        403);
    action(admin, "submit", m("confirmed", true, "amount", "100.01"), 403);
  }

  @Test
  void feesRequireExactAcknowledgmentAndFrozenPicks() throws Exception {
    publish();
    select(proof(), true, 200);
    action(client, "submit", m("confirmed", false, "amount", "100.01"), 409);
    action(client, "submit", m("confirmed", true, "amount", "100.02"), 409);
    action(client, "submit", m("confirmed", true, "amount", "100.01"), 200);
    select(proof(), false, 409);
  }

  @Test
  void latestRevisionCannotBeApprovedUsingOldId() throws Exception {
    long old = retouch();
    approve(old, "CHANGES", 200);
    long latest = upload("FINAL", proof(), "final-v2.jpg", image(), editor, 200);
    action(editor, "review", m(), 200);
    approve(old, "ACCEPT", 409);
    approve(latest, "ACCEPT", 200);
    assertEquals("READY", detail.get("job").get("state").asString());
  }

  @Test
  void approvedFinalCannotBeReplacedWhileAnotherNeedsChanges() throws Exception {
    publish();
    select(proof(), true, 200);
    action(client, "submit", m("confirmed", true, "amount", "100.01"), 200);
    long f = upload("FINAL", proof(), "final.jpg", image(), editor, 200);
    action(editor, "review", m(), 200);
    approve(f, "ACCEPT", 200);
    upload("FINAL", proof(), "other.jpg", image(), editor, 409);
  }

  @Test
  void reopenPreservesOldRoundAndRemovesCurrentFinals() throws Exception {
    retouch();
    action(admin, "reopen", m("note", "TEST revise selection"), 200);
    assertEquals(2, detail.get("job").get("roundNumber").asInt());
    assertEquals(1, detail.get("selected").asInt());
    action(client, "submit", m("confirmed", true, "amount", "100.01"), 200);
    action(editor, "review", m(), 409);
  }

  @Test
  void receiptAndRefundBoundedToTheirReferenceChain() throws Exception {
    publish();
    receipt("100.01");
    long source = detail.get("cash").get(0).get("id").asLong();
    call(
        admin,
        "/jobs/" + id() + "/cash",
        "POST",
        m(
            "version",
            version(),
            "kind",
            "REFUND",
            "sourceId",
            source,
            "amount",
            "100.02",
            "reference",
            "TEST bad",
            "note",
            "TEST"),
        409);
    detail =
        call(
            admin,
            "/jobs/" + id() + "/cash",
            "POST",
            m(
                "version",
                version(),
                "kind",
                "REFUND",
                "sourceId",
                source,
                "amount",
                "10.01",
                "reference",
                "TEST refund",
                "note",
                "TEST actual refund"),
            200);
    assertEquals(
        0,
        new java.math.BigDecimal("90.00")
            .compareTo(new java.math.BigDecimal(detail.get("paid").asString())));
    call(
        admin,
        "/jobs/" + id() + "/cash",
        "POST",
        m(
            "version",
            version(),
            "kind",
            "REVERSAL",
            "sourceId",
            source,
            "amount",
            "100.01",
            "reference",
            "TEST reverse",
            "note",
            "TEST"),
        409);
  }

  @Test
  void refundAfterDeliveryBlocksNewDownloads() throws Exception {
    long f = retouch();
    approve(f, "ACCEPT", 200);
    receipt("100.01");
    action(admin, "release", m(), 200);
    long source = detail.get("cash").get(0).get("id").asLong();
    detail =
        call(
            admin,
            "/jobs/" + id() + "/cash",
            "POST",
            m(
                "version",
                version(),
                "kind",
                "REFUND",
                "sourceId",
                source,
                "amount",
                "1.00",
                "reference",
                "TEST refund",
                "note",
                "TEST actual refund"),
            200);
    call(client, "/media/" + f + "/original", "GET", null, 409);
  }

  @Test
  void staleVersionNeverCreatesSecondReceipt() throws Exception {
    publish();
    long old = version();
    receipt("10.00");
    call(
        admin,
        "/jobs/" + id() + "/cash",
        "POST",
        m(
            "version",
            old,
            "kind",
            "RECEIPT",
            "amount",
            "10.00",
            "reference",
            "TEST second",
            "note",
            "TEST"),
        409);
  }

  @Test
  void shareIsSingleJobAndRevocationInvalidatesExistingSession() throws Exception {
    publish();
    var g = call(admin, "/jobs/" + id() + "/shares", "POST", m("version", version()), 200);
    detail = call(admin, "/jobs/" + id(), "GET", null, 200);
    var req =
        post("/api/share/exchange")
            .with(csrf())
            .contentType("application/json")
            .content(
                json.writeValueAsString(
                    m("token", g.get("token").asString(), "pin", g.get("pin").asString())));
    var r = mvc.perform(req).andReturn();
    assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    var guest = (MockHttpSession) r.getRequest().getSession(false);
    assertEquals(1, call(guest, "/jobs?mode=portal", "GET", null, 200).size());
    call(guest, "/clients", "GET", null, 403);
    call(
        admin,
        "/jobs/" + id() + "/shares/" + g.get("id").asLong() + "?version=" + version(),
        "DELETE",
        null,
        200);
    call(guest, "/auth/me", "GET", null, 401);
  }

  @Test
  void expiredGalleryBlocksPreviewAndSelection() throws Exception {
    publish();
    when(clock.instant()).thenReturn(NOW.plusSeconds(366 * 86400L));
    call(client, "/media/" + proof() + "/preview", "GET", null, 409);
    select(proof(), true, 409);
  }

  @Test
  void disguisedImageAndTraversalRejectedWithoutCreatingAsset() throws Exception {
    upload("PROOF", null, "wrong.jpg", "not image".getBytes(), admin, 409);
    upload("PROOF", null, "../escape.jpg", image(), admin, 409);
    assertEquals(0, call(admin, "/jobs/" + id(), "GET", null, 200).get("media").size());
  }

  @Test
  void proofsFrozenAndDuplicateNamesRejected() throws Exception {
    upload("PROOF", null, "one.jpg", image(), admin, 200);
    upload("PROOF", null, "ONE.JPG", image(), admin, 409);
    action(admin, "publish", m(), 200);
    upload("PROOF", null, "two.jpg", image(), admin, 409);
  }

  @Test
  void retoucherCannotInspectUnassignedOrFinancialRecords() throws Exception {
    call(editor, "/clients", "GET", null, 403);
    call(editor, "/reports", "GET", null, 403);
    call(editor, "/jobs/" + id() + "?mode=edit", "GET", null, 200);
    call(editor, "/jobs/" + id() + "?mode=cash", "GET", null, 403);
  }

  @Test
  void atomicImportRollsBackAllRowsOnDuplicate() throws Exception {
    String code = "I" + suffix;
    var rows =
        List.of(
            m(
                "code",
                code,
                "name",
                "TEST first",
                "contact",
                "",
                "departmentId",
                1,
                "enabled",
                true),
            m(
                "code",
                code,
                "name",
                "TEST duplicate",
                "contact",
                "",
                "departmentId",
                1,
                "enabled",
                true));
    assertEquals(3, call(admin, "/clients/import", "POST", rows, 409).get("row").asInt());
    for (var c : call(admin, "/clients", "GET", null, 200))
      assertNotEquals(code.toUpperCase(), c.get("code").asString());
  }

  @Test
  void csrfAndAnonymousAccessEnforced() throws Exception {
    assertEquals(
        403,
        mvc.perform(post("/api/jobs").session(admin).contentType("application/json").content("{}"))
            .andReturn()
            .getResponse()
            .getStatus());
    call(null, "/jobs", "GET", null, 401);
  }

  @Test
  void exactMoneyRejectsRoundingAndFormulaPrefixes() throws Exception {
    assertThrows(Problem.class, () -> Rules.money("1.001"));
    assertThrows(Problem.class, () -> Rules.money("-1"));
    assertEquals(
        "120.03", Rules.due(Rules.money("100.01"), Rules.money("20.02"), 1, 2).toPlainString());
    assertTrue(Rules.csv("\t\uFEFF=2").startsWith("\"'"));
  }

  @Test
  void finalPreviewStaysPrivateBeforeStaffReview() throws Exception {
    publish();
    select(proof(), true, 200);
    action(client, "submit", m("confirmed", true, "amount", "100.01"), 200);
    long f = upload("FINAL", proof(), "final.jpg", image(), editor, 200);
    call(client, "/media/" + f + "/preview", "GET", null, 409);
    action(editor, "review", m(), 200);
    assertEquals(
        200,
        mvc.perform(get("/api/media/" + f + "/preview").session(client))
            .andReturn()
            .getResponse()
            .getStatus());
  }

  @Test
  void selectedCountCannotExceedMaximumAndExtraPriceIsExact() throws Exception {
    long p1 = upload("PROOF", null, "one.jpg", image(), admin, 200),
        p2 = upload("PROOF", null, "two.jpg", image(), admin, 200),
        p3 = upload("PROOF", null, "three.jpg", image(), admin, 200);
    action(admin, "publish", m(), 200);
    select(p1, true, 200);
    select(p2, true, 200);
    select(p3, true, 409);
    action(client, "submit", m("confirmed", true, "amount", "120.03"), 200);
    assertEquals(
        0,
        new java.math.BigDecimal("120.03")
            .compareTo(new java.math.BigDecimal(detail.get("job").get("amountDue").asString())));
  }

  @Test
  void lastAdminCannotBeDisabled() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null, 200))
      if (row.get("id").asLong() == 1) a = row;
    assertNotNull(a);
    var body = json.readValue(a.toString(), Map.class);
    body.put("enabled", false);
    body.put("password", "");
    call(admin, "/admin/users/1", "PUT", body, 409);
    call(admin, "/auth/me", "GET", null, 200);
  }

  @Test
  void resetPasswordImmediatelyInvalidatesExistingClientSession() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null, 200))
      if (row.get("username").asString().equals("client" + suffix)) a = row;
    var body = json.readValue(a.toString(), Map.class);
    body.put("password", "NewAa9" + UUID.randomUUID());
    call(admin, "/admin/users/" + a.get("id").asLong(), "PUT", body, 200);
    call(client, "/auth/me", "GET", null, 401);
  }

  @Test
  void accountCannotSwitchFromStaffToClient() throws Exception {
    JsonNode a = null;
    for (var row : call(admin, "/admin/users", "GET", null, 200))
      if (row.get("id").asLong() == editorId) a = row;
    var body = json.readValue(a.toString(), Map.class);
    body.put("kind", "CLIENT");
    body.put("roleId", role("Client"));
    body.put("clientId", clientId);
    body.put("password", "");
    call(admin, "/admin/users/" + editorId, "PUT", body, 409);
  }

  @Test
  void departmentIsolationAndAssignedScopeProtectJobs() throws Exception {
    long dept =
        call(
                admin,
                "/admin/departments",
                "POST",
                m(
                    "name",
                    "TEST London " + suffix,
                    "zone",
                    "Europe/London",
                    "currency",
                    "GBP",
                    "enabled",
                    true),
                200)
            .get("id")
            .asLong();
    user("manager" + suffix, "Studio manager", "STAFF", null, dept);
    var manager = login("manager" + suffix);
    assertEquals(0, call(manager, "/jobs", "GET", null, 200).size());
    call(manager, "/jobs/" + id(), "GET", null, 403);
    user("unassigned" + suffix, "Retoucher", "STAFF", null, 1);
    var unassigned = login("unassigned" + suffix);
    call(unassigned, "/jobs/" + id() + "?mode=edit", "GET", null, 403);
  }

  @Test
  void retoucherDirectoryDoesNotDiscloseClientContact() throws Exception {
    var c = call(admin, "/clients", "GET", null, 200);
    JsonNode target = null;
    for (var row : c) if (row.get("id").asLong() == clientId) target = row;
    var b = json.readValue(target.toString(), Map.class);
    b.put("contact", "TEST private contact");
    call(admin, "/clients/" + clientId, "PUT", b, 200);
    var directory = call(editor, "/directory", "GET", null, 200);
    assertFalse(directory.toString().contains("TEST private contact"));
    assertFalse(directory.get("clients").get(0).has("contact"));
  }

  @Test
  void shareExpiryAndWrongPinAreRejected() throws Exception {
    publish();
    var g = call(admin, "/jobs/" + id() + "/shares", "POST", m("version", version()), 200);
    call(
        null,
        "/share/exchange",
        "POST",
        m(
            "token",
            g.get("token").asString(),
            "pin",
            "99999999".equals(g.get("pin").asString()) ? "00000000" : "99999999"),
        401);
    when(clock.instant()).thenReturn(NOW.plusSeconds(169 * 3600L));
    call(
        null,
        "/share/exchange",
        "POST",
        m("token", g.get("token").asString(), "pin", g.get("pin").asString()),
        401);
  }

  @Test
  void successfulReversalIsAppendOnlyAndCannotRepeat() throws Exception {
    publish();
    receipt("10.00");
    long source = detail.get("cash").get(0).get("id").asLong();
    var b =
        m(
            "version",
            version(),
            "kind",
            "REVERSAL",
            "sourceId",
            source,
            "amount",
            "10.00",
            "reference",
            "TEST correction",
            "note",
            "TEST recording error");
    detail = call(admin, "/jobs/" + id() + "/cash", "POST", b, 200);
    assertEquals(2, detail.get("cash").size());
    assertEquals(0, new java.math.BigDecimal(detail.get("paid").asString()).signum());
    b.put("version", version());
    b.put("reference", "TEST duplicate reversal");
    call(admin, "/jobs/" + id() + "/cash", "POST", b, 409);
  }

  @Test
  void draftRemovalKeepsOldBytesAndAllowsNewFilenameRevision() throws Exception {
    long old = upload("PROOF", null, "one.jpg", image(), admin, 200);
    detail =
        call(
            admin,
            "/jobs/" + id() + "/media/" + old + "?version=" + version(),
            "DELETE",
            null,
            200);
    long fresh = upload("PROOF", null, "one.jpg", image(), admin, 200);
    assertNotEquals(old, fresh);
    assertEquals(2, detail.get("media").get(0).get("revisionNumber").asInt());
    call(admin, "/media/" + old + "/original", "GET", null, 409);
  }

  @Test
  void cancelledProjectBlocksClientButKeepsStaffHistory() throws Exception {
    publish();
    action(admin, "cancel", m("note", "TEST cancellation"), 200);
    call(client, "/jobs/" + id() + "?mode=portal", "GET", null, 409);
    call(client, "/media/" + proof() + "/preview", "GET", null, 409);
    assertEquals(
        "CANCELLED",
        call(admin, "/jobs/" + id(), "GET", null, 200).get("job").get("state").asString());
  }

  @Test
  void lastAdminStudioCannotBeDisabled() throws Exception {
    var d = call(admin, "/admin/departments", "GET", null, 200).get(0);
    var b = json.readValue(d.toString(), Map.class);
    b.put("enabled", false);
    call(admin, "/admin/departments/1", "PUT", b, 409);
    call(admin, "/auth/me", "GET", null, 200);
  }

  @Test
  void staleRoleUpdateAndLossOfRecoveryPermissionsRejected() throws Exception {
    var r = call(admin, "/admin/roles", "GET", null, 200).get(0);
    var b = json.readValue(r.toString(), Map.class);
    b.put("version", 99999);
    call(admin, "/admin/roles/1", "PUT", b, 409);
    b.put("version", r.get("version").asLong());
    b.put("permissions", List.of("users"));
    call(admin, "/admin/roles/1", "PUT", b, 409);
  }

  @Test
  void concurrentSelectionHasOneWinnerAndNoDuplicateRows() throws Exception {
    publish();
    long old = version(), p = proof();
    var gate = new java.util.concurrent.CountDownLatch(1);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Integer> op =
          () -> {
            gate.await();
            var response =
                mvc.perform(
                        put("/api/jobs/" + id() + "/selection")
                            .session(client)
                            .with(csrf())
                            .contentType("application/json")
                            .content(
                                json.writeValueAsString(
                                    m(
                                        "version",
                                        old,
                                        "photoId",
                                        p,
                                        "selected",
                                        true,
                                        "note",
                                        "TEST race"))))
                    .andReturn()
                    .getResponse();
            return response.getStatus();
          };
      var a = pool.submit(op);
      var b = pool.submit(op);
      gate.countDown();
      var statuses =
          new ArrayList<>(
              List.of(
                  a.get(15, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(15, java.util.concurrent.TimeUnit.SECONDS)));
      Collections.sort(statuses);
      assertEquals(List.of(200, 409), statuses);
      var after = call(admin, "/jobs/" + id(), "GET", null, 200);
      assertEquals(1, after.get("selection").size());
      assertEquals(old + 1, after.get("job").get("version").asLong());
    } finally {
      pool.shutdownNow();
    }
  }

  @AfterAll
  static void cleanup() throws IOException {
    try (var walk = Files.walk(MEDIA)) {
      for (var file : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
    }
  }
}
