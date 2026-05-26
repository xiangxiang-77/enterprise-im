package com.enterpriseim.server.feature;

import com.enterpriseim.server.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "im.tcp.port=19007",
        "im.storage.local-root=${java.io.tmpdir}/enterprise-im-test-storage",
        "spring.datasource.url=jdbc:h2:mem:feature-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ProductFeatureControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void uploadsPreviewsAndDownloadsRealFileContent() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_file_" + suffix;
        userService.ensureUser(userId, "File User", "171" + Math.abs(suffix.hashCode()));
        val file = new MockMultipartFile("file", "hello.pdf", "application/pdf", "hello strict file".getBytes("UTF-8"));

        val uploadJson = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("uploaderId", userId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        val data = objectMapper.readTree(uploadJson).path("data");
        val fileId = data.path("id").asText();
        assertThat(data.path("downloadUrl").asText()).endsWith("/download");
        assertThat(data.path("previewUrl").asText()).endsWith("/preview");

        val preview = mockMvc.perform(get("/api/files/{fileId}/preview", fileId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(preview).isEqualTo("hello strict file");

        val download = mockMvc.perform(get("/api/files/{fileId}/download", fileId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(download, "UTF-8")).isEqualTo("hello strict file");
    }

    @Test
    void supportsChunkUploadAndOfficePreviewProviderBoundary() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_chunk_" + suffix;
        userService.ensureUser(userId, "Chunk User", "170" + Math.abs(suffix.hashCode()));

        val sessionJson = mockMvc.perform(post("/api/files/chunk-upload/sessions")
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "uploaderId", userId,
                                "originalName", "report.docx",
                                "contentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "totalSize", 10,
                                "totalChunks", 2))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val sessionId = objectMapper.readTree(sessionJson).path("data").path("id").asText();

        mockMvc.perform(multipart("/api/files/chunk-upload/sessions/{sessionId}/chunks", sessionId)
                        .file(new MockMultipartFile("file", "0.part", "application/octet-stream", "hello".getBytes("UTF-8")))
                        .param("chunkIndex", "0")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/files/chunk-upload/sessions/{sessionId}/chunks", sessionId)
                        .file(new MockMultipartFile("file", "1.part", "application/octet-stream", "world".getBytes("UTF-8")))
                        .param("chunkIndex", "1")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk());

        val completeJson = mockMvc.perform(post("/api/files/chunk-upload/sessions/{sessionId}/complete", sessionId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val fileId = objectMapper.readTree(completeJson).path("data").path("id").asText();
        val path = jdbcTemplate.queryForObject("SELECT object_key FROM files WHERE id = ?", String.class, fileId);
        assertThat(Files.readString(Paths.get(System.getProperty("java.io.tmpdir"), "enterprise-im-test-storage", "enterprise-im", path))).isEqualTo("helloworld");

        val previewJson = mockMvc.perform(get("/api/files/{fileId}/office-preview", fileId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(previewJson).path("data").path("providerMode").asText()).isEqualTo("disabled");
    }

    @Test
    void rejectsBlockedFileTypeFromPolicy() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_file_block_" + suffix;
        userService.ensureUser(userId, "File Block User", "172" + Math.abs(suffix.hashCode()));
        val file = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "bad".getBytes("UTF-8"));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("uploaderId", userId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFileTransfersOverRatePolicy() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_file_rate_" + suffix;
        userService.ensureUser(userId, "File Rate User", "173" + Math.abs(suffix.hashCode()));
        upsertPolicy("max_uploads_per_minute", "0");
        val file = new MockMultipartFile("file", "hello.pdf", "application/pdf", "rate".getBytes("UTF-8"));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("uploaderId", userId)
                        .header("Authorization", token(userId)))
                .andExpect(status().isBadRequest());
        upsertPolicy("max_uploads_per_minute", "60");
    }

    @Test
    void enforcesMessageStrictRulesAndReadStatus() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val sender = "u_msg_sender_" + suffix;
        val reader = "u_msg_reader_" + suffix;
        val unread = "u_msg_unread_" + suffix;
        val conversationId = "c_msg_" + suffix;
        val targetConversationId = "c_msg_target_" + suffix;
        userService.ensureUser(sender, "Sender", "181" + Math.abs(sender.hashCode()));
        userService.ensureUser(reader, "Reader", "182" + Math.abs(reader.hashCode()));
        userService.ensureUser(unread, "Unread", "183" + Math.abs(unread.hashCode()));
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'group', ?)", conversationId, conversationId);
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'group', ?)", targetConversationId, targetConversationId);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, sender);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, reader);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, unread);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", targetConversationId, sender);

        val editId = insertMessage(conversationId, sender, "editable", Instant.now());
        val oldId = insertMessage(conversationId, sender, "too old", Instant.now().minusSeconds(240));
        val forwardA = insertMessage(conversationId, sender, "forward a", Instant.now());
        val forwardB = insertMessage(conversationId, sender, "forward b", Instant.now());

        mockMvc.perform(patch("/api/messages/{messageId}/edit", editId)
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "edited"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/messages/{messageId}/edit", oldId)
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "late"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/messages/{messageId}/recall", oldId)
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "late"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/messages/{messageId}/read", editId)
                        .header("Authorization", token(reader)))
                .andExpect(status().isOk());

        val readStatusJson = mockMvc.perform(get("/api/messages/{messageId}/read-status", editId)
                        .header("Authorization", token(sender)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val readStatus = objectMapper.readTree(readStatusJson).path("data");
        assertThat(readStatus.path("read").get(0).path("userId").asText()).isEqualTo(reader);
        assertThat(readStatus.path("unread").get(0).path("userId").asText()).isEqualTo(unread);

        val forwardJson = mockMvc.perform(post("/api/messages/forward")
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", List.of(forwardA, forwardB),
                                "targetConversationIds", List.of(targetConversationId),
                                "mode", "single"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(forwardJson).path("data")).hasSize(2);

        val tooMany = new ArrayList<String>();
        for (int i = 0; i < 101; i++) tooMany.add("m_" + i);
        mockMvc.perform(post("/api/messages/forward")
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "messageIds", tooMany,
                                "targetConversationIds", List.of(targetConversationId),
                                "mode", "single"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void searchAndGroupMembersUseDisplayNameSchema() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val owner = "u_group_owner_" + suffix;
        val member = "u_group_member_" + suffix;
        val groupId = "g_schema_" + suffix;
        userService.ensureUser(owner, "Schema Owner", "191" + Math.abs(owner.hashCode()));
        userService.ensureUser(member, "Schema Member", "192" + Math.abs(member.hashCode()));
        jdbcTemplate.update("INSERT INTO chat_groups(id, owner_id, name, notice) VALUES (?, ?, ?, ?)", groupId, owner, "Schema Group", "schema notice");
        jdbcTemplate.update("INSERT INTO group_members(group_id, user_id, role) VALUES (?, ?, 'owner')", groupId, owner);
        jdbcTemplate.update("INSERT INTO group_members(group_id, user_id, role) VALUES (?, ?, 'member')", groupId, member);

        val searchJson = mockMvc.perform(get("/api/search")
                        .param("q", "Schema")
                        .param("type", "contacts")
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(searchJson).path("data").path("contacts").get(0).path("name").asText()).contains("Schema");

        val membersJson = mockMvc.perform(get("/api/groups/{groupId}/members", groupId)
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(membersJson).path("data").get(0).path("userName").asText()).contains("Schema");
    }

    @Test
    void searchReturnsScopedDeepLinksAndRecommendations() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_search_" + suffix;
        val otherId = "u_search_other_" + suffix;
        val conversationId = "c_search_" + suffix;
        val hiddenConversationId = "c_search_hidden_" + suffix;
        val messageId = "m_search_" + suffix;
        userService.ensureUser(userId, "Search User", "190" + Math.abs(userId.hashCode()));
        userService.ensureUser(otherId, "Search Other", "189" + Math.abs(otherId.hashCode()));
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'single', ?)", conversationId, otherId);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, userId);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, otherId);
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'single', ?)", hiddenConversationId, otherId);
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status, client_seq) VALUES (?, ?, ?, 'text', 'deep jump keyword', 'sent', ?)",
                messageId, conversationId, otherId, "search_" + suffix);
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status, client_seq) VALUES (?, ?, ?, 'text', 'deep jump keyword hidden', 'sent', ?)",
                "m_hidden_" + suffix, hiddenConversationId, otherId, "search_hidden_" + suffix);

        val json = mockMvc.perform(get("/api/search")
                        .param("q", "deep jump")
                        .param("type", "messages")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val messages = objectMapper.readTree(json).path("data").path("messages");
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).path("id").asText()).isEqualTo(messageId);
        assertThat(messages.get(0).path("jumpUrl").asText()).contains("messageId=" + messageId);

        val recommendationsJson = mockMvc.perform(get("/api/search/recommendations")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(recommendationsJson).path("data").path("messages").get(0).path("jumpUrl").asText())
                .contains("messageId=" + messageId);
    }

    @Test
    void enforcesFriendAndGroupPolicies() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val owner = "u_policy_owner_" + suffix;
        val member = "u_policy_member_" + suffix;
        val stranger = "u_policy_stranger_" + suffix;
        val blocked = "u_policy_blocked_" + suffix;
        val singleConversationId = "c_policy_single_" + suffix;
        userService.ensureUser(owner, "Policy Owner", "193" + Math.abs(owner.hashCode()));
        userService.ensureUser(member, "Policy Member", "194" + Math.abs(member.hashCode()));
        userService.ensureUser(stranger, "Policy Stranger", "195" + Math.abs(stranger.hashCode()));
        userService.ensureUser(blocked, "Policy Blocked", "196" + Math.abs(blocked.hashCode()));
        jdbcTemplate.update("INSERT INTO blacklists(user_id, blocked_user_id) VALUES (?, ?)", blocked, owner);

        mockMvc.perform(post("/api/friend-requests")
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("receiverId", blocked, "message", "hi"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/conversations/{conversationId}/messages", singleConversationId)
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderId", owner,
                                "type", "text",
                                "content", "blocked send",
                                "conversationType", "single",
                                "targetId", blocked))))
                .andExpect(status().isForbidden());

        val groupJson = mockMvc.perform(post("/api/groups")
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Policy Group",
                                "memberIds", List.of(member)))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val groupId = objectMapper.readTree(groupJson).path("data").path("id").asText();

        mockMvc.perform(post("/api/groups/{groupId}/members", groupId)
                        .header("Authorization", token(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", stranger))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/groups/{groupId}", groupId)
                        .header("Authorization", token(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notice", "member cannot update"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/groups/{groupId}/members/{userId}/mute", groupId, member)
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("muted", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/conversations/{conversationId}/messages", groupId)
                        .header("Authorization", token(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderId", member,
                                "type", "text",
                                "content", "muted send",
                                "conversationType", "group",
                                "targetId", groupId))))
                .andExpect(status().isForbidden());

        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM system_configs WHERE config_key = 'group.maxOwnedGroups'", Integer.class) == 0) {
            jdbcTemplate.update("INSERT INTO system_configs(config_key, config_value) VALUES ('group.maxOwnedGroups', '0')");
        } else {
            jdbcTemplate.update("UPDATE system_configs SET config_value = '0' WHERE config_key = 'group.maxOwnedGroups'");
        }
        mockMvc.perform(post("/api/groups")
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Over Quota"))))
                .andExpect(status().isBadRequest());
        jdbcTemplate.update("UPDATE system_configs SET config_value = '100' WHERE config_key = 'group.maxOwnedGroups'");
    }

    @Test
    void supportsGroupInviteApprovalBatchExportAndDissolve() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val owner = "u_group_adv_owner_" + suffix;
        val member = "u_group_adv_member_" + suffix;
        val requester = "u_group_adv_requester_" + suffix;
        userService.ensureUser(owner, "Group Adv Owner", "181" + Math.abs(owner.hashCode()));
        userService.ensureUser(member, "Group Adv Member", "182" + Math.abs(member.hashCode()));
        userService.ensureUser(requester, "Group Adv Requester", "183" + Math.abs(requester.hashCode()));

        val groupJson = mockMvc.perform(post("/api/groups")
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Advanced Group", "memberIds", List.of()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val groupId = objectMapper.readTree(groupJson).path("data").path("id").asText();

        mockMvc.perform(post("/api/groups/{groupId}/members/batch", groupId)
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(member)))))
                .andExpect(status().isOk());

        val exportJson = mockMvc.perform(get("/api/groups/{groupId}/members/export", groupId)
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(exportJson).path("data").findValuesAsText("userId")).contains(member);

        mockMvc.perform(patch("/api/groups/{groupId}/approval", groupId)
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}\n"))
                .andExpect(status().isOk());

        val inviteJson = mockMvc.perform(post("/api/groups/{groupId}/invites", groupId)
                        .header("Authorization", token(member)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val inviteToken = objectMapper.readTree(inviteJson).path("data").path("token").asText();
        assertThat(inviteToken).startsWith("ginv_");

        val requestJson = mockMvc.perform(post("/api/groups/{groupId}/join-requests", groupId)
                        .header("Authorization", token(requester))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inviteToken", inviteToken, "message", "join me"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val requestId = objectMapper.readTree(requestJson).path("data").path("id").asText();
        assertThat(objectMapper.readTree(requestJson).path("data").path("status").asText()).isEqualTo("pending");

        mockMvc.perform(post("/api/groups/{groupId}/join-requests/{requestId}/handle", groupId, requestId)
                        .header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accept\":true}\n"))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ?", Integer.class, groupId, requester))
                .isEqualTo(1);

        val dissolved = mockMvc.perform(delete("/api/groups/{groupId}", groupId)
                        .header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(dissolved).path("data").path("status").asText()).isEqualTo("dissolved");
    }

    @Test
    void persistsNotificationAndConversationSettings() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_settings_" + suffix;
        val conversationId = "c_settings_" + suffix;
        userService.ensureUser(userId, "Settings User", "197" + Math.abs(userId.hashCode()));
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'single', ?)", conversationId, "target_" + suffix);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, userId);

        val notificationJson = mockMvc.perform(patch("/api/notification-settings")
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "newMessage", false,
                                "screenshotNotice", false,
                                "recallNotice", false,
                                "dndEnabled", true,
                                "dndStart", "21:30",
                                "dndEnd", "07:30"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val notification = objectMapper.readTree(notificationJson).path("data");
        assertThat(notification.path("newMessage").asBoolean()).isFalse();
        assertThat(notification.path("screenshotNotice").asBoolean()).isFalse();
        assertThat(notification.path("dndStart").asText()).isEqualTo("21:30");

        val conversationSettingsJson = mockMvc.perform(patch("/api/conversations/{conversationId}/settings", conversationId)
                        .header("Authorization", token(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "muted", true,
                                "pinned", true,
                                "screenshotNotice", false,
                                "recallNotice", false,
                                "readAfterBurn", true,
                                "strongReminder", true,
                                "displayMemberNicknames", false,
                                "savedToContacts", true))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val conversationSettings = objectMapper.readTree(conversationSettingsJson).path("data");
        assertThat(conversationSettings.path("muted").asBoolean()).isTrue();
        assertThat(conversationSettings.path("screenshotNotice").asBoolean()).isFalse();
        assertThat(conversationSettings.path("readAfterBurn").asBoolean()).isTrue();

        val conversationsJson = mockMvc.perform(get("/api/conversations")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val first = objectMapper.readTree(conversationsJson).path("data").get(0);
        assertThat(first.path("muted").asBoolean()).isTrue();
        assertThat(first.path("recallNotice").asBoolean()).isFalse();
        assertThat(first.path("savedToContacts").asBoolean()).isTrue();
    }

    @Test
    void appliesNotificationSuppressionAndMentionOverride() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val sender = "u_notify_sender_" + suffix;
        val muted = "u_notify_muted_" + suffix;
        val screenshotOff = "u_notify_shot_off_" + suffix;
        val conversationId = "c_notify_" + suffix;
        userService.ensureUser(sender, "Notify Sender", "198" + Math.abs(sender.hashCode()));
        userService.ensureUser(muted, "Notify Muted", "199" + Math.abs(muted.hashCode()));
        userService.ensureUser(screenshotOff, "Notify Screenshot Off", "190" + Math.abs(screenshotOff.hashCode()));
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'group', ?)", conversationId, conversationId);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, sender);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id, muted, screenshot_notice) VALUES (?, ?, TRUE, TRUE)", conversationId, muted);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id, screenshot_notice) VALUES (?, ?, FALSE)", conversationId, screenshotOff);
        jdbcTemplate.update("INSERT INTO user_notification_settings(user_id, dnd_enabled, dnd_start, dnd_end, mention_alert) VALUES (?, TRUE, '00:00', '23:59', TRUE)", muted);

        mockMvc.perform(post("/api/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderId", sender,
                                "type", "text",
                                "content", "normal notice",
                                "conversationType", "group",
                                "targetId", conversationId))))
                .andExpect(status().isOk());
        assertThat(notificationReason(muted, conversationId, "message")).isIn("conversation_muted", "dnd");

        mockMvc.perform(post("/api/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "senderId", sender,
                                "type", "text",
                                "content", "hello @" + muted,
                                "conversationType", "group",
                                "targetId", conversationId))))
                .andExpect(status().isOk());
        assertThat(notificationStatus(muted, conversationId, "message")).isEqualTo("delivered");

        mockMvc.perform(post("/api/conversations/{conversationId}/screenshot", conversationId)
                        .header("Authorization", token(sender)))
                .andExpect(status().isOk());
        assertThat(notificationReason(screenshotOff, conversationId, "screenshot")).isEqualTo("screenshot_notice_disabled");
    }

    @Test
    void managesPushProviderBoundariesDeviceTokensAndOfflineDeliveryRecords() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val sender = "u_push_sender_" + suffix;
        val receiver = "u_push_receiver_" + suffix;
        val conversationId = "c_push_" + suffix;
        userService.ensureUser(sender, "Push Sender", "184" + Math.abs(sender.hashCode()));
        userService.ensureUser(receiver, "Push Receiver", "185" + Math.abs(receiver.hashCode()));
        jdbcTemplate.update("INSERT INTO conversations(id, type, target_id) VALUES (?, 'single', ?)", conversationId, receiver);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, sender);
        jdbcTemplate.update("INSERT INTO conversation_members(conversation_id, user_id) VALUES (?, ?)", conversationId, receiver);

        val providers = mockMvc.perform(get("/api/push/providers")
                        .header("Authorization", token(receiver)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(providers).path("data").path("fcm").path("mode").asText()).isEqualTo("disabled");

        val tokenJson = mockMvc.perform(post("/api/push/device-tokens")
                        .header("Authorization", token(receiver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"android\",\"provider\":\"fcm\",\"token\":\"abcdef1234567890\"}\n"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val tokenId = objectMapper.readTree(tokenJson).path("data").path("id").asText();
        assertThat(objectMapper.readTree(tokenJson).path("data").path("token").asText()).contains("...");

        mockMvc.perform(post("/api/conversations/{conversationId}/messages", conversationId)
                        .header("Authorization", token(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("senderId", sender, "type", "text", "content", "offline push", "clientSeq", "push_" + suffix))))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_deliveries WHERE user_id = ? AND device_token_id = ? AND status = 'skipped' AND reason = 'provider_disabled'", Integer.class, receiver, tokenId))
                .isEqualTo(1);

        mockMvc.perform(delete("/api/push/device-tokens/{tokenId}", tokenId)
                        .header("Authorization", token(receiver)))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM push_device_tokens WHERE id = ? AND enabled = FALSE", Integer.class, tokenId))
                .isEqualTo(1);
    }

    @Test
    void workspaceAppsRespectEnablementAndDepartmentVisibility() throws Exception {
        val suffix = UUID.randomUUID().toString();
        val userId = "u_workspace_" + suffix;
        val otherUserId = "u_workspace_other_" + suffix;
        val enterpriseId = "ent_workspace_" + suffix;
        val departmentId = "dep_workspace_" + suffix;
        userService.ensureUser(userId, "Workspace User", "173" + Math.abs(userId.hashCode()));
        userService.ensureUser(otherUserId, "Other Workspace User", "174" + Math.abs(otherUserId.hashCode()));
        jdbcTemplate.update("INSERT INTO enterprises(id, name, code) VALUES (?, 'Workspace Enterprise', ?)", enterpriseId, "workspace_" + Math.abs(suffix.hashCode()));
        jdbcTemplate.update("INSERT INTO departments(id, enterprise_id, name, sort_order) VALUES (?, ?, 'Workspace Dept', 1)", departmentId, enterpriseId);
        jdbcTemplate.update("INSERT INTO department_members(department_id, user_id, position_name) VALUES (?, ?, 'Member')", departmentId, userId);
        jdbcTemplate.update("INSERT INTO workspace_apps(id, name, icon, url, sort_order, enabled) VALUES (?, 'Open App', 'briefcase', 'https://oa.example', 1, TRUE)",
                "app_open_" + suffix);
        jdbcTemplate.update("INSERT INTO workspace_apps(id, name, icon, url, visible_department_id, sort_order, enabled) VALUES (?, 'Dept App', 'calendar', 'https://dept.example', ?, 2, TRUE)",
                "app_dept_" + suffix, departmentId);
        jdbcTemplate.update("INSERT INTO workspace_apps(id, name, icon, url, sort_order, enabled) VALUES (?, 'Disabled App', 'x', 'https://disabled.example', 3, FALSE)",
                "app_disabled_" + suffix);

        val visibleJson = mockMvc.perform(get("/api/workspace-apps")
                        .header("Authorization", token(userId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        val otherJson = mockMvc.perform(get("/api/workspace-apps")
                        .header("Authorization", token(otherUserId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(visibleJson).path("data").findValuesAsText("name")).contains("Open App", "Dept App").doesNotContain("Disabled App");
        assertThat(objectMapper.readTree(otherJson).path("data").findValuesAsText("name")).contains("Open App").doesNotContain("Dept App", "Disabled App");
    }

    private String notificationStatus(String userId, String conversationId, String eventType) {
        val rows = jdbcTemplate.queryForList("SELECT status FROM notification_events WHERE user_id = ? AND conversation_id = ? AND event_type = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                String.class, userId, conversationId, eventType);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String notificationReason(String userId, String conversationId, String eventType) {
        val rows = jdbcTemplate.queryForList("SELECT reason FROM notification_events WHERE user_id = ? AND conversation_id = ? AND event_type = ? ORDER BY created_at DESC, id DESC LIMIT 1",
                String.class, userId, conversationId, eventType);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String insertMessage(String conversationId, String senderId, String content, Instant createdAt) {
        val id = "m_test_" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO messages(id, conversation_id, sender_id, type, content, status, created_at) VALUES (?, ?, ?, 'text', ?, 'sent', ?)",
                id, conversationId, senderId, content, Timestamp.from(createdAt));
        return id;
    }

    private void upsertPolicy(String key, String value) {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resource_policies WHERE policy_key = ?", Integer.class, key) == 0) {
            jdbcTemplate.update("INSERT INTO resource_policies(id, policy_key, policy_value) VALUES (?, ?, ?)", "policy_test_" + UUID.randomUUID(), key, value);
        } else {
            jdbcTemplate.update("UPDATE resource_policies SET policy_value = ? WHERE policy_key = ?", value, key);
        }
    }

    private String token(String userId) {
        return "Bearer demo-token-" + userId;
    }
}
