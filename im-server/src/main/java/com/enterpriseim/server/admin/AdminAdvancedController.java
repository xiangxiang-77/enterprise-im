package com.enterpriseim.server.admin;

import com.enterpriseim.server.api.ApiResponse;
import com.enterpriseim.server.config.ImProperties;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.val;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/admin")
public class AdminAdvancedController {
    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthService authService;
    private final ImProperties imProperties;

    public AdminAdvancedController(JdbcTemplate jdbcTemplate, AdminAuthService authService, ImProperties imProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.imProperties = imProperties;
    }

    @GetMapping("/risk-events")
    public ApiResponse<List<RiskEventDto>> riskEvents(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestParam(defaultValue = "50") int limit,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(required = false) String eventType) {
        authService.requireAdmin(authorization);
        StringBuilder sql = new StringBuilder("SELECT id, event_type, user_id, conversation_id, message_id, detail, status, created_at FROM risk_events WHERE 1=1");
        new Object() {};
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (hasText(status)) { sql.append(" AND status = ?"); params.add(status); }
        if (hasText(eventType)) { sql.append(" AND event_type = ?"); params.add(eventType); }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(bound(limit));
        return ApiResponse.ok(jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new RiskEventDto(rs.getString("id"), rs.getString("event_type"),
                rs.getString("user_id"), rs.getString("conversation_id"), rs.getString("message_id"), rs.getString("detail"), rs.getString("status"),
                toInstant(rs.getTimestamp("created_at"))), params.toArray()));
    }

    @GetMapping("/sensitive-words")
    public ApiResponse<List<SensitiveWordDto>> sensitiveWords(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT id, word, action, enabled, created_at FROM sensitive_words ORDER BY created_at DESC",
                (rs, rowNum) -> new SensitiveWordDto(rs.getString("id"), rs.getString("word"), rs.getString("action"), rs.getBoolean("enabled"), toInstant(rs.getTimestamp("created_at")))));
    }

    @PostMapping("/sensitive-words")
    public ApiResponse<SensitiveWordDto> createSensitiveWord(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                             @RequestBody SensitiveWordRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "SECURITY_AUDITOR");
        val id = "sw_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO sensitive_words(id, word, action, enabled) VALUES (?, ?, ?, ?)", id, request.word(), valueOr(request.action(), "block"), request.enabled());
        audit(admin.userId(), "SENSITIVE_WORD_CREATE", "sensitive_word", id, request.word());
        return ApiResponse.ok(sensitiveWordById(id));
    }

    @DeleteMapping("/sensitive-words/{id}")
    public ApiResponse<SensitiveWordDto> deleteSensitiveWord(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                             @PathVariable String id,
                                                             @RequestBody ConfirmRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "SECURITY_AUDITOR");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "确认文本必须为 CONFIRM");
        }
        val existing = sensitiveWordById(id);
        jdbcTemplate.update("DELETE FROM sensitive_words WHERE id = ?", id);
        audit(admin.userId(), "SENSITIVE_WORD_DELETE", "sensitive_word", id, existing.word());
        return ApiResponse.ok(existing);
    }

    @GetMapping("/resource-policies")
    public ApiResponse<List<KeyValueDto>> resourcePolicies(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT policy_key, policy_value, updated_at FROM resource_policies ORDER BY policy_key",
                (rs, rowNum) -> new KeyValueDto(rs.getString("policy_key"), rs.getString("policy_value"), toInstant(rs.getTimestamp("updated_at")))));
    }

    @PatchMapping("/resource-policies/{key}")
    public ApiResponse<KeyValueDto> updateResourcePolicy(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                         @PathVariable String key,
                                                         @RequestBody KeyValueRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        upsert("resource_policies", "policy_key", "policy_value", key, request.value());
        audit(admin.userId(), "RESOURCE_POLICY_UPDATE", "resource_policy", key, request.value());
        return ApiResponse.ok(new KeyValueDto(key, request.value(), null));
    }

    @GetMapping("/workspace-apps")
    public ApiResponse<List<WorkspaceAppDto>> workspaceApps(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT id, name, icon, url, visible_department_id, sort_order, enabled FROM workspace_apps ORDER BY sort_order ASC, name ASC",
                (rs, rowNum) -> new WorkspaceAppDto(rs.getString("id"), rs.getString("name"), rs.getString("icon"), rs.getString("url"),
                        rs.getString("visible_department_id"), rs.getInt("sort_order"), rs.getBoolean("enabled"))));
    }

    @PostMapping("/workspace-apps")
    public ApiResponse<WorkspaceAppDto> createWorkspaceApp(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @RequestBody WorkspaceAppRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        validateWorkspaceApp(request);
        val id = "app_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO workspace_apps(id, name, icon, url, visible_department_id, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, request.name(), request.icon(), request.url(), request.visibleDepartmentId(), request.sortOrder(), request.enabled());
        audit(admin.userId(), "WORKSPACE_APP_CREATE", "workspace_app", id, request.name());
        return ApiResponse.ok(workspaceAppById(id));
    }

    @PostMapping("/workspace-apps/{id}/icon")
    public ApiResponse<WorkspaceAppDto> uploadWorkspaceAppIcon(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                               @PathVariable String id,
                                                               @RequestParam("iconFile") MultipartFile iconFile) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        ensureWorkspaceApp(id);
        String iconValue = saveIconFile(iconFile);
        jdbcTemplate.update("UPDATE workspace_apps SET icon = ? WHERE id = ?", iconValue, id);
        audit(admin.userId(), "WORKSPACE_APP_ICON_UPLOAD", "workspace_app", id, iconValue);
        return ApiResponse.ok(workspaceAppById(id));
    }

    private String saveIconFile(MultipartFile file) {
        try {
            String storageRoot = imProperties.getStorage().getLocalRoot();
            Path iconsDir = Paths.get(storageRoot, "icons");
            Files.createDirectories(iconsDir);
            String filename = "icon_" + UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path target = iconsDir.resolve(filename);
            file.transferTo(target.toFile());
            return "/api/files/icons/" + filename;
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "图标上传失败: " + e.getMessage());
        }
    }

    @PatchMapping("/workspace-apps/{id}")
    public ApiResponse<WorkspaceAppDto> updateWorkspaceApp(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @PathVariable String id,
                                                           @RequestBody WorkspaceAppRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        ensureWorkspaceApp(id);
        validateWorkspaceApp(request);
        jdbcTemplate.update("UPDATE workspace_apps SET name = ?, icon = ?, url = ?, visible_department_id = ?, sort_order = ?, enabled = ? WHERE id = ?",
                request.name(), request.icon(), request.url(), request.visibleDepartmentId(), request.sortOrder(), request.enabled(), id);
        audit(admin.userId(), "WORKSPACE_APP_UPDATE", "workspace_app", id, request.name());
        return ApiResponse.ok(workspaceAppById(id));
    }

    @PatchMapping("/workspace-apps/reorder")
    @Transactional
    public ApiResponse<List<WorkspaceAppDto>> reorderWorkspaceApps(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                   @RequestBody WorkspaceAppReorderRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "缺少 items 字段");
        }
        for (WorkspaceAppOrderItem item : request.items()) {
            ensureWorkspaceApp(item.id());
            jdbcTemplate.update("UPDATE workspace_apps SET sort_order = ? WHERE id = ?", item.sortOrder(), item.id());
        }
        audit(admin.userId(), "WORKSPACE_APP_REORDER", "workspace_app", "batch", "items=" + request.items().size());
        return workspaceApps(authorization);
    }

    @DeleteMapping("/workspace-apps/{id}")
    public ApiResponse<WorkspaceAppDto> deleteWorkspaceApp(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @PathVariable String id,
                                                           @RequestBody ConfirmRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (!"CONFIRM".equals(request.confirmText())) {
            throw new ResponseStatusException(BAD_REQUEST, "确认文本必须为 CONFIRM");
        }
        val existing = workspaceAppById(id);
        jdbcTemplate.update("DELETE FROM workspace_apps WHERE id = ?", id);
        audit(admin.userId(), "WORKSPACE_APP_DELETE", "workspace_app", id, existing.name());
        return ApiResponse.ok(existing);
    }

    @GetMapping("/app-versions")
    public ApiResponse<List<AppVersionDto>> appVersions(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.query(
                "SELECT v.id, v.platform, v.version_name, v.version_code, v.download_url, v.force_update, v.notes, v.rollout_percent, v.min_version_code, v.file_size, v.sha256, v.status, v.created_at, " +
                "(SELECT COUNT(*) FROM version_downloads d WHERE d.version_id = v.id) AS download_count " +
                "FROM app_versions v ORDER BY v.created_at DESC",
                (rs, rowNum) -> new AppVersionDto(rs.getString("id"), rs.getString("platform"), rs.getString("version_name"), rs.getInt("version_code"),
                        rs.getString("download_url"), rs.getBoolean("force_update"), rs.getString("notes"), rs.getInt("rollout_percent"),
                        rs.getInt("min_version_code"), rs.getLong("file_size"), rs.getString("sha256"), rs.getString("status"),
                        rs.getLong("download_count"), toInstant(rs.getTimestamp("created_at")))));
    }

    @PostMapping("/app-versions")
    public ApiResponse<AppVersionDto> createAppVersion(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestBody AppVersionRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val id = "ver_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_versions(id, platform, version_name, version_code, download_url, force_update, notes, rollout_percent, min_version_code, file_size, sha256, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'active')",
                id, request.platform(), request.versionName(), request.versionCode(), request.downloadUrl(), request.forceUpdate(), request.notes(),
                valueOrInt(request.rolloutPercent(), 100), request.minVersionCode(), request.fileSize(), valueOr(request.sha256(), ""));
        audit(admin.userId(), "APP_VERSION_CREATE", "app_version", id, request.versionName());
        return ApiResponse.ok(appVersionById(id));
    }

    @GetMapping("/system-configs")
    public ApiResponse<List<KeyValueDto>> systemConfigs(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(jdbcTemplate.query("SELECT config_key, config_value, updated_at FROM system_configs ORDER BY config_key",
                (rs, rowNum) -> new KeyValueDto(rs.getString("config_key"), rs.getString("config_value"), toInstant(rs.getTimestamp("updated_at")))));
    }

    @PatchMapping("/system-configs/{key}")
    public ApiResponse<KeyValueDto> updateSystemConfig(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @PathVariable String key,
                                                       @RequestBody KeyValueRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        upsert("system_configs", "config_key", "config_value", key, request.value());
        audit(admin.userId(), "SYSTEM_CONFIG_UPDATE", "system_config", key, request.value());
        return ApiResponse.ok(new KeyValueDto(key, request.value(), null));
    }

    @GetMapping("/friend-requests")
    public ApiResponse<List<FriendRequestDto>> friendRequests(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                              @RequestParam(defaultValue = "50") int limit,
                                                              @RequestParam(required = false) String status) {
        authService.requireAdmin(authorization);
        String sql = "SELECT id, requester_id, receiver_id, message, status, created_at, handled_at FROM friend_requests WHERE (? IS NULL OR status = ?) ORDER BY created_at DESC LIMIT ?";
        return ApiResponse.ok(jdbcTemplate.query(sql, (rs, rowNum) -> new FriendRequestDto(rs.getString("id"), rs.getString("requester_id"),
                rs.getString("receiver_id"), rs.getString("message"), rs.getString("status"), toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("handled_at"))),
                emptyToNull(status), emptyToNull(status), bound(limit)));
    }

    @PostMapping("/friend-requests/{id}/handle")
    @Transactional
    public ApiResponse<FriendRequestDto> handleFriendRequest(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                             @PathVariable String id,
                                                             @RequestBody FriendHandleRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val item = friendRequestById(id);
        val status = request.accept() ? "accepted" : "rejected";
        jdbcTemplate.update("UPDATE friend_requests SET status = ?, handled_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
        if (request.accept()) {
            ensureFriend(item.requesterId(), item.receiverId(), "admin");
            ensureFriend(item.receiverId(), item.requesterId(), "admin");
        }
        audit(admin.userId(), "FRIEND_REQUEST_HANDLE", "friend_request", id, status);
        return ApiResponse.ok(friendRequestById(id));
    }

    @PatchMapping("/groups/{groupId}/owner")
    @Transactional
    public ApiResponse<GroupOpDto> transferGroupOwner(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String groupId,
                                                      @RequestBody GroupOwnerRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        jdbcTemplate.update("UPDATE chat_groups SET owner_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.ownerId(), groupId);
        jdbcTemplate.update("UPDATE group_members SET role = 'member' WHERE group_id = ? AND role = 'owner'", groupId);
        if (exists("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", groupId, request.ownerId())) {
            jdbcTemplate.update("UPDATE group_members SET role = 'owner' WHERE group_id = ? AND user_id = ?", groupId, request.ownerId());
        } else {
            jdbcTemplate.update("INSERT INTO group_members(group_id, user_id, role) VALUES (?, ?, 'owner')", groupId, request.ownerId());
        }
        audit(admin.userId(), "GROUP_OWNER_TRANSFER", "group", groupId, request.ownerId());
        return ApiResponse.ok(new GroupOpDto(groupId, "owner_transferred"));
    }

    @PatchMapping("/groups/{groupId}/notice")
    public ApiResponse<GroupOpDto> publishGroupNotice(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String groupId,
                                                      @RequestBody GroupNoticeRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        jdbcTemplate.update("UPDATE chat_groups SET notice = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", request.notice(), groupId);
        audit(admin.userId(), "GROUP_NOTICE_PUBLISH", "group", groupId, request.notice());
        return ApiResponse.ok(new GroupOpDto(groupId, "notice_published"));
    }

    // ── App Template Library ──

    private static final List<AppTemplateDto> APP_TEMPLATES = List.of(
            new AppTemplateDto("jira", "Jira", "项目管理工具", "jira", "{\"url\":\"https://your-domain.atlassian.net/jira\",\"visibleDepartmentId\":null,\"sortOrder\":0,\"enabled\":true}"),
            new AppTemplateDto("confluence", "Confluence", "知识库管理", "confluence", "{\"url\":\"https://your-domain.atlassian.net/wiki\",\"visibleDepartmentId\":null,\"sortOrder\":0,\"enabled\":true}"),
            new AppTemplateDto("gitlab", "GitLab", "代码仓库", "gitlab", "{\"url\":\"https://gitlab.your-domain.com\",\"visibleDepartmentId\":null,\"sortOrder\":0,\"enabled\":true}"),
            new AppTemplateDto("figma", "Figma", "设计协作", "figma", "{\"url\":\"https://www.figma.com\",\"visibleDepartmentId\":null,\"sortOrder\":0,\"enabled\":true}"),
            new AppTemplateDto("notion", "Notion", "笔记与文档", "notion", "{\"url\":\"https://www.notion.so\",\"visibleDepartmentId\":null,\"sortOrder\":0,\"enabled\":true}"),
            new AppTemplateDto("slack", "Slack", "团队通讯", "slack", "{\"url\":\"https://slack.com\",\"visibleDepartmentId\":null,\"sortOrder\":0,\"enabled\":true}")
    );

    @GetMapping("/app-templates")
    public ApiResponse<List<AppTemplateDto>> appTemplates(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(APP_TEMPLATES);
    }

    @PostMapping("/app-templates/{id}/install")
    public ApiResponse<WorkspaceAppDto> installAppTemplate(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @PathVariable String id,
                                                            @RequestBody AppTemplateInstallRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        val template = APP_TEMPLATES.stream()
                .filter(t -> t.templateId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "模板未找到"));
        val appId = "app_" + UUID.randomUUID();
        try {
            val mapper = new ObjectMapper();
            val config = mapper.readTree(template.config());
            val url = config.has("url") ? config.get("url").asText() : "";
            val visibleDeptId = config.has("visibleDepartmentId") && !config.get("visibleDepartmentId").isNull() ? config.get("visibleDepartmentId").asText() : null;
            val sortOrder = config.has("sortOrder") ? config.get("sortOrder").asInt() : 0;
            val enabled = !config.has("enabled") || config.get("enabled").asBoolean();
            jdbcTemplate.update("INSERT INTO workspace_apps(id, name, icon, url, visible_department_id, sort_order, enabled) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    appId, template.name(), template.icon(), url, visibleDeptId != null ? visibleDeptId : request.workspaceId(), sortOrder, enabled);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(BAD_REQUEST, "模板配置解析失败");
        }
        audit(admin.userId(), "APP_TEMPLATE_INSTALL", "workspace_app", appId, template.name());
        return ApiResponse.ok(workspaceAppById(appId));
    }

    // ── Version Push Closed Loop ──

    @GetMapping("/app-versions/{id}/stats")
    public ApiResponse<AppVersionStatsDto> appVersionStats(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                            @PathVariable String id) {
        authService.requireAdmin(authorization);
        ensureAppVersion(id);
        val stats = jdbcTemplate.query("SELECT " +
                "COUNT(*) AS total_downloads, " +
                "COUNT(DISTINCT user_id) AS unique_users " +
                "FROM version_downloads WHERE version_id = ?", rs -> {
            if (!rs.next()) return new AppVersionStatsDto(0, 0, 0.0);
            long total = rs.getLong("total_downloads");
            long unique = rs.getLong("unique_users");
            long totalAll = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM version_downloads", Long.class);
            double rate = totalAll > 0 ? (double) total / totalAll * 100.0 : 0.0;
            return new AppVersionStatsDto(total, unique, Math.round(rate * 100.0) / 100.0);
        }, id);
        return ApiResponse.ok(stats);
    }

    @PatchMapping("/app-versions/{id}/deprecate")
    public ApiResponse<AppVersionDto> deprecateAppVersion(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                           @PathVariable String id) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        ensureAppVersion(id);
        jdbcTemplate.update("UPDATE app_versions SET status = 'deprecated' WHERE id = ?", id);
        audit(admin.userId(), "APP_VERSION_DEPRECATE", "app_version", id, "deprecated");
        return ApiResponse.ok(appVersionById(id));
    }

    @PatchMapping("/app-versions/{id}/rollback")
    public ApiResponse<AppVersionDto> rollbackAppVersion(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                          @PathVariable String id) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        ensureAppVersion(id);
        jdbcTemplate.update("UPDATE app_versions SET status = 'rolled_back' WHERE id = ?", id);
        audit(admin.userId(), "APP_VERSION_ROLLBACK", "app_version", id, "rolled_back");
        return ApiResponse.ok(appVersionById(id));
    }

    // ── System Configuration UI Backend ──

    @GetMapping("/system-config")
    public ApiResponse<SystemConfigDto> systemConfig(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.requireAdmin(authorization);
        return ApiResponse.ok(buildSystemConfigDto());
    }

    @PatchMapping("/system-config")
    public ApiResponse<SystemConfigDto> updateSystemConfigAggregated(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                      @RequestBody SystemConfigPatchRequest request) {
        val admin = authService.requireAdmin(authorization);
        authService.requireRole(admin, "SUPER_ADMIN", "OPERATOR_ADMIN");
        if (request.primaryColor != null) upsert("system_configs", "config_key", "config_value", "theme.primaryColor", request.primaryColor);
        if (request.logoUrl != null) upsert("system_configs", "config_key", "config_value", "launch.logoUrl", request.logoUrl);
        if (request.slogan != null) upsert("system_configs", "config_key", "config_value", "launch.slogan", request.slogan);
        if (request.defaultLanguage != null) upsert("system_configs", "config_key", "config_value", "i18n.defaultLanguage", request.defaultLanguage);
        if (request.termsUrl != null) upsert("system_configs", "config_key", "config_value", "legal.termsUrl", request.termsUrl);
        if (request.privacyUrl != null) upsert("system_configs", "config_key", "config_value", "legal.privacyUrl", request.privacyUrl);
        audit(admin.userId(), "SYSTEM_CONFIG_AGGREGATED_UPDATE", "system_config", "batch", "updated");
        return ApiResponse.ok(buildSystemConfigDto());
    }

    private SystemConfigDto buildSystemConfigDto() {
        return new SystemConfigDto(
                configValue("theme.primaryColor", imProperties.getTheme().getPrimaryColor()),
                configValue("launch.logoUrl", imProperties.getLaunch().getLogoUrl()),
                configValue("launch.slogan", imProperties.getLaunch().getSlogan()),
                configValue("i18n.defaultLanguage", imProperties.getI18n().getDefaultLanguage()),
                configValue("legal.termsUrl", imProperties.getLegal().getTermsUrl()),
                configValue("legal.privacyUrl", imProperties.getLegal().getPrivacyUrl())
        );
    }

    private String configValue(String key, String defaultValue) {
        List<String> values = jdbcTemplate.query("SELECT config_value FROM system_configs WHERE config_key = ?",
                (rs, rowNum) -> rs.getString("config_value"), key);
        return values.isEmpty() ? defaultValue : values.get(0);
    }

    private void ensureAppVersion(String id) {
        if (!exists("SELECT COUNT(*) FROM app_versions WHERE id = ?", id)) {
            throw new ResponseStatusException(BAD_REQUEST, "应用版本未找到");
        }
    }

    private void ensureFriend(String userId, String friendId, String source) {
        if (!exists("SELECT COUNT(*) FROM friendships WHERE user_id = ? AND friend_id = ?", userId, friendId)) {
            jdbcTemplate.update("INSERT INTO friendships(user_id, friend_id, source) VALUES (?, ?, ?)", userId, friendId, source);
        }
    }

    private void upsert(String table, String keyColumn, String valueColumn, String key, String value) {
        if (exists("SELECT COUNT(*) FROM " + table + " WHERE " + keyColumn + " = ?", key)) {
            jdbcTemplate.update("UPDATE " + table + " SET " + valueColumn + " = ?, updated_at = CURRENT_TIMESTAMP WHERE " + keyColumn + " = ?", value, key);
        } else if ("resource_policies".equals(table)) {
            jdbcTemplate.update("INSERT INTO resource_policies(id, policy_key, policy_value) VALUES (?, ?, ?)", "policy_" + UUID.randomUUID(), key, value);
        } else {
            jdbcTemplate.update("INSERT INTO system_configs(config_key, config_value) VALUES (?, ?)", key, value);
        }
    }

    private SensitiveWordDto sensitiveWordById(String id) {
        return jdbcTemplate.query("SELECT id, word, action, enabled, created_at FROM sensitive_words WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "敏感词未找到");
            return new SensitiveWordDto(rs.getString("id"), rs.getString("word"), rs.getString("action"), rs.getBoolean("enabled"), toInstant(rs.getTimestamp("created_at")));
        }, id);
    }

    private WorkspaceAppDto workspaceAppById(String id) {
        return jdbcTemplate.query("SELECT id, name, icon, url, visible_department_id, sort_order, enabled FROM workspace_apps WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "工作台应用未找到");
            return new WorkspaceAppDto(rs.getString("id"), rs.getString("name"), rs.getString("icon"), rs.getString("url"), rs.getString("visible_department_id"), rs.getInt("sort_order"), rs.getBoolean("enabled"));
        }, id);
    }

    private void ensureWorkspaceApp(String id) {
        if (!exists("SELECT COUNT(*) FROM workspace_apps WHERE id = ?", id)) {
            throw new ResponseStatusException(BAD_REQUEST, "工作台应用未找到");
        }
    }

    private void validateWorkspaceApp(WorkspaceAppRequest request) {
        if (!hasText(request.name())) throw new ResponseStatusException(BAD_REQUEST, "名称不能为空");
        if (hasText(request.visibleDepartmentId()) && !exists("SELECT COUNT(*) FROM departments WHERE id = ?", request.visibleDepartmentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "可见部门未找到");
        }
    }

    private AppVersionDto appVersionById(String id) {
        return jdbcTemplate.query(
                "SELECT v.id, v.platform, v.version_name, v.version_code, v.download_url, v.force_update, v.notes, v.rollout_percent, v.min_version_code, v.file_size, v.sha256, v.status, v.created_at, " +
                "(SELECT COUNT(*) FROM version_downloads d WHERE d.version_id = v.id) AS download_count " +
                "FROM app_versions v WHERE v.id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "应用版本未找到");
            return new AppVersionDto(rs.getString("id"), rs.getString("platform"), rs.getString("version_name"), rs.getInt("version_code"),
                    rs.getString("download_url"), rs.getBoolean("force_update"), rs.getString("notes"), rs.getInt("rollout_percent"),
                    rs.getInt("min_version_code"), rs.getLong("file_size"), rs.getString("sha256"), rs.getString("status"),
                    rs.getLong("download_count"), toInstant(rs.getTimestamp("created_at")));
        }, id);
    }

    private FriendRequestDto friendRequestById(String id) {
        return jdbcTemplate.query("SELECT id, requester_id, receiver_id, message, status, created_at, handled_at FROM friend_requests WHERE id = ?", rs -> {
            if (!rs.next()) throw new ResponseStatusException(BAD_REQUEST, "好友申请未找到");
            return new FriendRequestDto(rs.getString("id"), rs.getString("requester_id"), rs.getString("receiver_id"), rs.getString("message"), rs.getString("status"), toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("handled_at")));
        }, id);
    }

    private void audit(String operatorId, String action, String targetType, String targetId, String detail) {
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail) VALUES (?, ?, ?, ?, ?, ?)",
                "audit_" + UUID.randomUUID(), operatorId, action, targetType, targetId, detail);
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    private int bound(int limit) { return Math.max(1, Math.min(limit, 200)); }
    private boolean hasText(String value) { return value != null && !value.trim().isEmpty(); }
    private String emptyToNull(String value) { return hasText(value) ? value : null; }
    private String valueOr(String value, String fallback) { return hasText(value) ? value : fallback; }
    private int valueOrInt(int value, int fallback) { return value > 0 ? value : fallback; }
    private String toInstant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant().toString(); }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class RiskEventDto { private String id; private String eventType; private String userId; private String conversationId; private String messageId; private String detail; private String status; private String createdAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SensitiveWordDto { private String id; private String word; private String action; private boolean enabled; private String createdAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SensitiveWordRequest { private String word; private String action; private boolean enabled = true; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class KeyValueDto { private String key; private String value; private String updatedAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class KeyValueRequest { private String value; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WorkspaceAppDto { private String id; private String name; private String icon; private String url; private String visibleDepartmentId; private int sortOrder; private boolean enabled; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WorkspaceAppRequest { private String name; private String icon; private String url; private String visibleDepartmentId; private int sortOrder; private boolean enabled = true; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WorkspaceAppOrderItem { private String id; private int sortOrder; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class WorkspaceAppReorderRequest { private List<WorkspaceAppOrderItem> items; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class ConfirmRequest { private String confirmText; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class AppVersionDto { private String id; private String platform; private String versionName; private int versionCode; private String downloadUrl; private boolean forceUpdate; private String notes; private int rolloutPercent; private int minVersionCode; private long fileSize; private String sha256; private String status; private long downloadCount; private String createdAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class AppVersionRequest { private String platform; private String versionName; private int versionCode; private String downloadUrl; private boolean forceUpdate; private String notes; private int rolloutPercent = 100; private int minVersionCode; private long fileSize; private String sha256; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FriendRequestDto { private String id; private String requesterId; private String receiverId; private String message; private String status; private String createdAt; private String handledAt; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class FriendHandleRequest { private boolean accept; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupOwnerRequest { private String ownerId; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupNoticeRequest { private String notice; }
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class GroupOpDto { private String groupId; private String status; }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class AppTemplateDto { private String templateId; private String name; private String description; private String icon; private String config; }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class AppTemplateInstallRequest { private String workspaceId; }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class AppVersionStatsDto { private long totalDownloads; private long uniqueUsers; private double downloadRate; }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SystemConfigDto { private String primaryColor; private String logoUrl; private String slogan; private String defaultLanguage; private String termsUrl; private String privacyUrl; }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class SystemConfigPatchRequest { private String primaryColor; private String logoUrl; private String slogan; private String defaultLanguage; private String termsUrl; private String privacyUrl; }
}
