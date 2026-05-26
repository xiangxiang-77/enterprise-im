package com.enterpriseim.server.admin;

import lombok.val;

import com.enterpriseim.server.user.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=19004",
        "im.storage.local-root=${java.io.tmpdir}/enterprise-im-admin-test-storage",
        "spring.datasource.url=jdbc:h2:mem:admin-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AdminApiTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private String adminToken() throws Exception {
        val json = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"18800000000\",\"password\":\"admin123\"}\n"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(json).path("data").path("token").asText();
    }

    @Test
    void overviewUsesCurrentDatabaseCounts() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val from = "u_overview_from_" + suffix;
        val to = "u_overview_to_" + suffix;
        val conversationId = "c_overview_" + suffix;
        val requestId = "req_overview_" + suffix;
        userService.ensureUser(from, "Overview From", "177" + Math.abs(from.hashCode()));
        userService.ensureUser(to, "Overview To", "177" + Math.abs(to.hashCode()));
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id)\n" +
                "VALUES (?, 'single', ?)\n", conversationId, to);
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status, client_seq)\n" +
                "VALUES (?, ?, ?, 'text', 'overview message', 'sent', ?)\n", "m_overview_" + suffix, conversationId, from, requestId);
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status)\n" +
                "VALUES (?, ?, ?, 'overview.pdf', 'application/pdf', 1234, 'available')\n", "file_overview_" + suffix, from, "overview/" + suffix);
        jdbcTemplate.update("INSERT INTO risk_events(id, event_type, user_id, conversation_id, detail)\n" +
                "VALUES (?, 'overview_risk', ?, ?, 'risk overview')\n", "risk_overview_" + suffix, from, conversationId);
        jdbcTemplate.update("INSERT INTO call_records(id, conversation_id, caller_id, callee_id, media_type, status, turn_session_id)\n" +
                "VALUES (?, ?, ?, ?, 'audio', 'ringing', ?)\n", "call_overview_active_" + suffix, conversationId, from, to, "turn_overview_active_" + suffix);
        jdbcTemplate.update("INSERT INTO call_records(id, conversation_id, caller_id, callee_id, media_type, status, answered_at, turn_session_id)\n" +
                "VALUES (?, ?, ?, ?, 'video', 'answered', CURRENT_TIMESTAMP, ?)\n", "call_overview_answered_" + suffix, conversationId, from, to, "turn_overview_answered_" + suffix);
        jdbcTemplate.update("INSERT INTO call_records(id, conversation_id, caller_id, callee_id, media_type, status, ended_at, turn_session_id)\n" +
                "VALUES (?, ?, ?, ?, 'audio', 'rejected', CURRENT_TIMESTAMP, ?)\n", "call_overview_missed_" + suffix, conversationId, from, to, "turn_overview_missed_" + suffix);

        val json = mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(json).path("data");
        assertThat(data.path("enterpriseUsers").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(data.path("singleConversations").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("todayMessages").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("totalCalls").asInt()).isGreaterThanOrEqualTo(3);
        assertThat(data.path("activeCalls").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("answeredCalls").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("missedCalls").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(data.path("messageTrend")).isNotEmpty();
        assertThat(data.path("storageBreakdown").findValuesAsText("kind")).contains("application/pdf");
        assertThat(data.path("riskTrend")).isNotEmpty();
        assertThat(data.path("permissionMatrix").findValuesAsText("role")).contains("SUPER_ADMIN", "SECURITY_AUDITOR");
    }

    @Test
    void rejectsAdminApiWithoutToken() throws Exception {
        mockMvc.perform(get("/api/admin/users").param("limit", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminLoginAndMeExposePermissionSet() throws Exception {
        val loginJson = mockMvc.perform(post("/api/admin/auth/login")
                        .contentType("application/json")
                        .content("{\"phone\":\"18800000000\",\"password\":\"admin123\"}\n"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val loginData = objectMapper.readTree(loginJson).path("data");
        assertThat(loginData.path("permissions").toString()).contains("dashboard.read", "admin.write", "advanced.read");

        val meJson = mockMvc.perform(get("/api/admin/auth/me")
                        .header("Authorization", "Bearer " + loginData.path("token").asText()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        val meData = objectMapper.readTree(meJson).path("data");
        assertThat(meData.path("role").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(meData.path("permissions").toString()).contains("organization.write", "resources.write");
    }

    @Test
    void listsUsersForAdminConsole() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_admin_api_" + suffix;
        userService.ensureUser(userId, "Admin API User", "188" + Math.abs(suffix.hashCode()));

        val json = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.path("success").asBoolean()).isTrue();
        assertThat(root.path("data").findValuesAsText("id")).contains(userId);
    }

    @Test
    void updatesUserProfileAndListsDeviceSessions() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val token = adminToken();
        val enterpriseId = "ent_profile_" + suffix;
        val departmentId = "dep_profile_" + suffix;
        val userId = "u_profile_" + suffix;
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code) VALUES (?, 'Profile Enterprise', ?)", enterpriseId, "profile_" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("INSERT INTO departments(id, enterprise_id, name, sort_order) VALUES (?, ?, 'Product', 1)", departmentId, enterpriseId);
        userService.ensureUser(userId, "Profile Before", "184" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("UPDATE users SET enterprise_id = ? WHERE id = ?", enterpriseId, userId);
        jdbcTemplate.update("INSERT INTO device_sessions(id, user_id, device_type, device_name, token_hash, online, last_seen_at, expires_at)\n" +
                        "VALUES (?, ?, 'desktop', 'Qt Client', 'hash', true, CURRENT_TIMESTAMP, ?)\n",
                "session_profile_" + suffix, userId, Timestamp.from(Instant.now().plusSeconds(3600)));

        val profileJson = mockMvc.perform(patch("/api/admin/users/{userId}/profile", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"displayName\":\"Profile After\",\"email\":\"profile%s@example.com\",\"avatarUrl\":\"https://cdn.example.com/a.png\",\"shortNo\":\"sn%s\",\"gender\":\"female\",\"signature\":\"ready\",\"positionName\":\"Product Manager\"}\n",
                                Math.abs(suffix.hashCode()), Math.abs((suffix + "short").hashCode()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val profile = objectMapper.readTree(profileJson).path("data");
        assertThat(profile.path("displayName").asText()).isEqualTo("Profile After");
        assertThat(profile.path("positionName").asText()).isEqualTo("Product Manager");
        assertThat(profile.path("signature").asText()).isEqualTo("ready");
        assertThat(jdbcTemplate.queryForObject("SELECT position_name FROM department_members WHERE user_id = ?", String.class, userId)).isEqualTo("Product Manager");

        val sessionsJson = mockMvc.perform(get("/api/admin/users/{userId}/device-sessions", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode sessions = objectMapper.readTree(sessionsJson).path("data");
        assertThat(sessions.findValuesAsText("deviceName")).contains("Qt Client");
        assertThat(sessions.findValuesAsText("deviceType")).contains("desktop");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_PROFILE_UPDATE' AND target_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void createsEnterpriseAndUserWithFiltersAndAudit() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val token = adminToken();
        val enterpriseJson = mockMvc.perform(post("/api/admin/enterprises")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"name\":\"Filter Enterprise\",\"code\":\"filter_%s\"}\n", Math.abs(suffix.hashCode()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val enterpriseId = objectMapper.readTree(enterpriseJson).path("data").path("id").asText();

        val userJson = mockMvc.perform(post("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"enterpriseId\":\"%s\",\"phone\":\"157%s\",\"displayName\":\"Filtered User\"}\n", enterpriseId, Math.abs(suffix.hashCode()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val userId = objectMapper.readTree(userJson).path("data").path("id").asText();

        val listJson = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token)
                        .param("enterpriseId", enterpriseId)
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val enterpriseListJson = mockMvc.perform(get("/api/admin/enterprises")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(listJson).path("data").findValuesAsText("id")).contains(userId);
        assertThat(objectMapper.readTree(enterpriseListJson).path("data").findValuesAsText("id")).contains(enterpriseId);
        val auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs\n" +
                "WHERE target_id IN (?, ?) AND action IN ('ENTERPRISE_CREATE', 'USER_CREATE')\n", Integer.class, enterpriseId, userId);
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void listsAuditLogsForAdminConsole() throws Exception {
        val auditId = "audit_admin_api_" + UUID.randomUUID();
        val ignoredAuditId = "audit_admin_api_ignored_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail)\n" +
                "VALUES (?, 'u_admin_api', 'USER_DISABLE', 'user', 'u_target', 'demo audit')\n", auditId);
        jdbcTemplate.update("INSERT INTO audit_logs(id, operator_id, action, target_type, target_id, detail)\n" +
                "VALUES (?, 'u_other_admin', 'GROUP_DELETE', 'group', 'g_target', 'ignored audit')\n", ignoredAuditId);

        val json = mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken())
                        .param("limit", "20")
                        .param("operatorId", "u_admin_api")
                        .param("action", "USER_DISABLE")
                        .param("targetType", "user"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(json);
        assertThat(root.path("success").asBoolean()).isTrue();
        assertThat(root.path("data").findValuesAsText("id")).contains(auditId);
        assertThat(root.path("data").findValuesAsText("id")).doesNotContain(ignoredAuditId);
    }

    @Test
    void listsDepartmentsAndRolesForAdminConsole() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val enterpriseId = "ent_admin_api_" + suffix;
        val departmentId = "dep_admin_api_" + suffix;
        val userId = "u_dep_api_" + suffix;
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code)\n" +
                "VALUES (?, 'Demo Enterprise', ?)\n", enterpriseId, "demo_" + Math.abs(suffix.hashCode()));
        userService.ensureUser(userId, "Department User", "155" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("UPDATE users SET enterprise_id = ? WHERE id = ?", enterpriseId, userId);
        jdbcTemplate.update("INSERT INTO departments(id, enterprise_id, name, sort_order)\n" +
                "VALUES (?, ?, 'Engineering', 10)\n", departmentId, enterpriseId);
        jdbcTemplate.update("INSERT INTO department_members(department_id, user_id, position_name)\n" +
                "VALUES (?, ?, 'Developer')\n", departmentId, userId);

        val token = adminToken();
        val departmentJson = mockMvc.perform(get("/api/admin/departments")
                        .header("Authorization", "Bearer " + token)
                        .param("enterpriseId", enterpriseId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val roleJson = mockMvc.perform(get("/api/admin/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode departments = objectMapper.readTree(departmentJson).path("data");
        JsonNode roles = objectMapper.readTree(roleJson).path("data");
        assertThat(departments.findValuesAsText("id")).contains(departmentId);
        assertThat(departments.findValuesAsText("enterpriseName")).contains("Demo Enterprise");
        assertThat(departments.findValues("memberCount").stream().map(JsonNode::asInt)).contains(1);
        assertThat(roles.findValuesAsText("name")).contains("SUPER_ADMIN", "SECURITY_AUDITOR");
    }

    @Test
    void mutatesDepartmentsWithConfirmationAndAudit() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val enterpriseId = "ent_mutation_api_" + suffix;
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code)\n" +
                "VALUES (?, 'Mutation Enterprise', ?)\n", enterpriseId, "mutation_" + Math.abs(suffix.hashCode()));
        val token = adminToken();

        val createJson = mockMvc.perform(post("/api/admin/departments")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"enterpriseId\":\"%s\",\"name\":\"Support\",\"sortOrder\":5}\n", enterpriseId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val departmentId = objectMapper.readTree(createJson).path("data").path("id").asText();

        mockMvc.perform(patch("/api/admin/departments/{departmentId}", departmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"enterpriseId\":\"%s\",\"name\":\"Customer Support\",\"sortOrder\":6}\n", enterpriseId)))
                .andExpect(status().isOk());

        val renamed = jdbcTemplate.queryForObject("SELECT name FROM departments WHERE id = ?", String.class, departmentId);
        assertThat(renamed).isEqualTo("Customer Support");

        mockMvc.perform(delete("/api/admin/departments/{departmentId}", departmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"NOPE\"}\n"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/admin/departments/{departmentId}", departmentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"CONFIRM\"}\n"))
                .andExpect(status().isOk());

        val departmentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM departments WHERE id = ?", Integer.class, departmentId);
        val auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs\n" +
                "WHERE target_id = ? AND action IN ('DEPARTMENT_CREATE', 'DEPARTMENT_UPDATE', 'DEPARTMENT_DELETE')\n", Integer.class, departmentId);
        assertThat(departmentCount).isEqualTo(0);
        assertThat(auditCount).isEqualTo(3);
    }

    @Test
    void assignsAndDisablesAdminUsersWithAudit() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_admin_assign_" + suffix;
        userService.ensureUser(userId, "Assigned Admin", "156" + Math.abs(suffix.hashCode()));
        val token = adminToken();

        val createJson = mockMvc.perform(post("/api/admin/admin-users")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"userId\":\"%s\",\"roleId\":\"role_auditor\"}\n", userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val adminUserId = objectMapper.readTree(createJson).path("data").path("id").asText();

        val listJson = mockMvc.perform(get("/api/admin/admin-users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(listJson).path("data").findValuesAsText("id")).contains(adminUserId);

        mockMvc.perform(patch("/api/admin/admin-users/{adminUserId}/enabled", adminUserId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"confirmText\":\"NOPE\"}\n"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/admin-users/{adminUserId}/enabled", adminUserId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"enabled\":false,\"confirmText\":\"CONFIRM\"}\n"))
                .andExpect(status().isOk());

        val enabled = jdbcTemplate.queryForObject("SELECT enabled FROM admin_users WHERE id = ?", Boolean.class, adminUserId);
        val auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs\n" +
                "WHERE target_id = ? AND action IN ('ADMIN_USER_CREATE', 'ADMIN_USER_ENABLED_UPDATE')\n", Integer.class, adminUserId);
        assertThat(enabled).isFalse();
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void listsGroupsFilesMessagesAndCallsForAuditConsole() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val enterpriseId = "ent_resource_api_" + suffix;
        val ownerId = "u_resource_owner_" + suffix;
        val calleeId = "u_resource_callee_" + suffix;
        val groupId = "g_resource_" + suffix;
        val conversationId = "c_resource_" + suffix;
        val fileId = "file_resource_" + suffix;
        val callId = "call_resource_" + suffix;
        userService.ensureUser(ownerId, "Resource Owner", "158" + Math.abs(suffix.hashCode()));
        userService.ensureUser(calleeId, "Resource Callee", "159" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code)\n" +
                "VALUES (?, 'Resource Enterprise', ?)\n", enterpriseId, "resource_" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("UPDATE users SET enterprise_id = ? WHERE id = ?", enterpriseId, ownerId);
        jdbcTemplate.update("INSERT INTO chat_groups(id, enterprise_id, owner_id, name, status)\n" +
                "VALUES (?, ?, ?, 'Resource Group', 'active')\n", groupId, enterpriseId, ownerId);
        jdbcTemplate.update("INSERT INTO group_members(group_id, user_id, role)\n" +
                "VALUES (?, ?, 'owner')\n", groupId, ownerId);
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id)\n" +
                "VALUES (?, 'group', ?)\n", conversationId, groupId);
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status)\n" +
                "VALUES (?, ?, 'objects/demo.txt', 'demo.txt', 'text/plain', 12, 'available')\n", fileId, ownerId);
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status)\n" +
                "VALUES (?, ?, ?, 'text', 'audit message', 'sent')\n", "m_resource_" + suffix, conversationId, ownerId);
        jdbcTemplate.update("INSERT INTO call_records(id, conversation_id, caller_id, callee_id, media_type, status, turn_session_id)\n" +
                "VALUES (?, ?, ?, ?, 'video', 'answered', ?)\n", callId, conversationId, ownerId, calleeId, "turn_resource_" + suffix);

        val token = adminToken();
        val groupsJson = mockMvc.perform(get("/api/admin/groups")
                        .header("Authorization", "Bearer " + token)
                        .param("enterpriseId", enterpriseId)
                        .param("status", "active"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val filesJson = mockMvc.perform(get("/api/admin/files")
                        .header("Authorization", "Bearer " + token)
                        .param("uploaderId", ownerId)
                        .param("status", "available"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val messagesJson = mockMvc.perform(get("/api/admin/messages")
                        .header("Authorization", "Bearer " + token)
                        .param("conversationId", conversationId)
                        .param("senderId", ownerId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val callsJson = mockMvc.perform(get("/api/admin/calls")
                        .header("Authorization", "Bearer " + token)
                        .param("userId", ownerId)
                        .param("status", "answered")
                        .param("mediaType", "video"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(groupsJson).path("data").findValuesAsText("id")).contains(groupId);
        assertThat(objectMapper.readTree(filesJson).path("data").findValuesAsText("id")).contains(fileId);
        assertThat(objectMapper.readTree(messagesJson).path("data").findValuesAsText("content")).contains("audit message");
        JsonNode callData = objectMapper.readTree(callsJson).path("data");
        assertThat(callData.findValuesAsText("id")).contains(callId);
        assertThat(callData.findValuesAsText("turnSessionId")).contains("turn_resource_" + suffix);
    }

    @Test
    void adminControlsFileResourceStatusAndTransferLogs() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val ownerId = "u_file_admin_" + suffix;
        val fileId = "file_admin_" + suffix;
        userService.ensureUser(ownerId, "File Admin Owner", "168" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status)\n" +
                "VALUES (?, ?, ?, 'strict.pdf', 'application/pdf', 16, 'available')\n", fileId, ownerId, "objects/strict.pdf");
        jdbcTemplate.update("INSERT INTO file_transfer_logs(id, file_id, user_id, direction, progress, status)\n" +
                "VALUES (?, ?, ?, 'download', 100, 'completed')\n", "ft_admin_" + suffix, fileId, ownerId);

        val token = adminToken();
        val transfersJson = mockMvc.perform(get("/api/admin/files/{fileId}/transfers", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(transfersJson).path("data").findValuesAsText("direction")).contains("download");

        mockMvc.perform(patch("/api/admin/files/{fileId}/status", fileId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"status\":\"disabled\"}\n"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM files WHERE id = ?", String.class, fileId)).isEqualTo("disabled");

        mockMvc.perform(delete("/api/admin/files/{fileId}", fileId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"NOPE\"}\n"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/admin/files/{fileId}", fileId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"CONFIRM\"}\n"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM files WHERE id = ?", String.class, fileId)).isEqualTo("deleted");
        val auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action IN ('FILE_STATUS_UPDATE', 'FILE_DELETE')", Integer.class, fileId);
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void adminRunsFileLifecycleCleanup() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val ownerId = "u_file_lifecycle_" + suffix;
        val fileId = "file_lifecycle_" + suffix;
        val objectKey = ownerId + "/" + fileId + "/expired.pdf";
        val path = Paths.get(System.getProperty("java.io.tmpdir"), "enterprise-im-admin-test-storage", "enterprise-im", objectKey);
        Files.createDirectories(path.getParent());
        Files.write(path, "expired".getBytes("UTF-8"));
        userService.ensureUser(ownerId, "Lifecycle Owner", "169" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("INSERT INTO files(id, uploader_id, object_key, original_name, content_type, size_bytes, status, created_at)\n" +
                "VALUES (?, ?, ?, 'expired.pdf', 'application/pdf', 7, 'available', ?)\n", fileId, ownerId, objectKey, Timestamp.from(Instant.now().minusSeconds(86400)));
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resource_policies WHERE policy_key = 'file_retention_days'", Integer.class) == 0) {
            jdbcTemplate.update("INSERT INTO resource_policies(id, policy_key, policy_value) VALUES (?, 'file_retention_days', '0')", "policy_lifecycle_" + suffix);
        } else {
            jdbcTemplate.update("UPDATE resource_policies SET policy_value = '0' WHERE policy_key = 'file_retention_days'");
        }

        val cleanupJson = mockMvc.perform(post("/api/admin/files/lifecycle-cleanup")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(cleanupJson).path("data").path("expiredFiles").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM files WHERE id = ?", String.class, fileId)).isEqualTo("deleted");
        assertThat(Files.exists(path)).isFalse();
        jdbcTemplate.update("UPDATE resource_policies SET policy_value = '365' WHERE policy_key = 'file_retention_days'");
    }

    @Test
    void exposesCallConnectivityProbeForAdminConsole() throws Exception {
        val json = mockMvc.perform(get("/api/admin/call-connectivity")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(json).path("data");
        assertThat(data.path("checks").findValuesAsText("name")).contains("turn", "pjsipSignal");
        assertThat(data.path("checks").findValuesAsText("host")).contains("localhost");
        assertThat(data.path("checks").findValues("port").stream().map(JsonNode::asInt)).contains(3478, 7070);
        assertThat(data.path("checks").findValues("durationMs").stream().map(JsonNode::asLong)).allMatch(value -> value >= 0);
    }

    @Test
    void highRiskUserStatusChangeRequiresConfirmationAndWritesAudit() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_status_api_" + suffix;
        userService.ensureUser(userId, "Status API User", "166" + Math.abs(suffix.hashCode()));
        val token = adminToken();

        mockMvc.perform(patch("/api/admin/users/{userId}/status", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"status\":\"disabled\",\"confirmText\":\"NOPE\"}\n"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/admin/users/{userId}/status", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"status\":\"disabled\",\"confirmText\":\"CONFIRM\"}\n"))
                .andExpect(status().isOk());

        val status = jdbcTemplate.queryForObject("SELECT status FROM users WHERE id = ?", String.class, userId);
        val auditCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs\n" +
                "WHERE action = 'USER_STATUS_UPDATE' AND target_id = ?\n", Integer.class, userId);
        assertThat(status).isEqualTo("disabled");
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void batchImportsUsersAndManagesDevicePolicies() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val token = adminToken();
        val enterpriseId = "ent_batch_" + suffix;
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code) VALUES (?, 'Batch Enterprise', ?)", enterpriseId, "batch_" + Math.abs(suffix.hashCode()));

        val importJson = mockMvc.perform(post("/api/admin/users/batch-import")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"users\":[{\"enterpriseId\":\"%s\",\"phone\":\"181%s\",\"displayName\":\"Batch One\"},{\"enterpriseId\":\"%s\",\"phone\":\"182%s\",\"displayName\":\"Batch Two\"}]}\n",
                                enterpriseId, Math.abs(suffix.hashCode()), enterpriseId, Math.abs((suffix + "b").hashCode()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode importData = objectMapper.readTree(importJson).path("data");
        assertThat(importData.path("created").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE enterprise_id = ?", Integer.class, enterpriseId)).isEqualTo(2);

        mockMvc.perform(patch("/api/admin/users/device-policies/maxOnlineDevices")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"value\":\"3\"}\n"))
                .andExpect(status().isOk());

        val policiesJson = mockMvc.perform(get("/api/admin/users/device-policies")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(policiesJson).path("data").findValuesAsText("key")).contains("device.maxOnlineDevices");
        assertThat(jdbcTemplate.queryForObject("SELECT config_value FROM system_configs WHERE config_key = 'device.maxOnlineDevices'", String.class)).isEqualTo("3");
    }

    @Test
    void forceOfflineRequiresConfirmationAndAudits() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_force_offline_" + suffix;
        userService.ensureUser(userId, "Force Offline", "183" + Math.abs(suffix.hashCode()));
        val token = adminToken();

        mockMvc.perform(post("/api/admin/users/{userId}/force-offline", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"NOPE\"}\n"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/users/{userId}/force-offline", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"CONFIRM\"}\n"))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_FORCE_OFFLINE' AND target_id = ?", Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void managesWorkspaceAppLifecycleAndVisibility() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val token = adminToken();
        val enterpriseId = "ent_app_" + suffix;
        val departmentId = "dep_app_" + suffix;
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code) VALUES (?, 'App Enterprise', ?)", enterpriseId, "app_" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("INSERT INTO departments(id, enterprise_id, name, sort_order) VALUES (?, ?, 'Visible Dept', 1)", departmentId, enterpriseId);

        val appJson = mockMvc.perform(post("/api/admin/workspace-apps")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"name\":\"CRM\",\"icon\":\"briefcase\",\"url\":\"/crm\",\"visibleDepartmentId\":\"%s\",\"sortOrder\":10,\"enabled\":true}\n", departmentId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val appId = objectMapper.readTree(appJson).path("data").path("id").asText();

        mockMvc.perform(patch("/api/admin/workspace-apps/{id}", appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"name\":\"CRM Pro\",\"icon\":\"briefcase\",\"url\":\"/crm\",\"visibleDepartmentId\":\"%s\",\"sortOrder\":20,\"enabled\":false}\n", departmentId)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/admin/workspace-apps/reorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(String.format("{\"items\":[{\"id\":\"%s\",\"sortOrder\":3}]}\n", appId)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT sort_order FROM workspace_apps WHERE id = ?", Integer.class, appId)).isEqualTo(3);

        mockMvc.perform(delete("/api/admin/workspace-apps/{id}", appId)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmText\":\"CONFIRM\"}\n"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workspace_apps WHERE id = ?", Integer.class, appId)).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE target_id = ? AND action IN ('WORKSPACE_APP_CREATE','WORKSPACE_APP_UPDATE','WORKSPACE_APP_REORDER','WORKSPACE_APP_DELETE')", Integer.class, appId)).isGreaterThanOrEqualTo(3);
    }
}
