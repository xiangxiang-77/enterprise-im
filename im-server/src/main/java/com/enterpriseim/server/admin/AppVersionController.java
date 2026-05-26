package com.enterpriseim.server.admin;

import com.enterpriseim.server.api.ApiResponse;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/app")
public class AppVersionController {
    private final JdbcTemplate jdbcTemplate;

    public AppVersionController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/version/check")
    public ApiResponse<VersionCheckResult> checkVersion(@RequestParam String platform,
                                                         @RequestParam int versionCode) {
        java.util.List<VersionRow> rows = jdbcTemplate.query(
                "SELECT id, platform, version_name, version_code, download_url, force_update, notes, rollout_percent, min_version_code, status " +
                "FROM app_versions WHERE platform = ? AND status = 'active' ORDER BY version_code DESC",
                (rs, rowNum) -> new VersionRow(
                        rs.getString("id"),
                        rs.getString("platform"),
                        rs.getString("version_name"),
                        rs.getInt("version_code"),
                        rs.getString("download_url"),
                        rs.getBoolean("force_update"),
                        rs.getString("notes"),
                        rs.getInt("rollout_percent"),
                        rs.getInt("min_version_code"),
                        rs.getString("status")
                ),
                platform
        );

        if (rows.isEmpty()) {
            return ApiResponse.ok(new VersionCheckResult(false, false, "", "", "", 0));
        }

        VersionRow latest = rows.get(0);
        boolean updateRequired = latest.versionCode > versionCode;
        boolean forceUpdate = updateRequired && latest.forceUpdate;

        // If client version is below min_version_code, force update
        if (versionCode < latest.minVersionCode) {
            forceUpdate = true;
            updateRequired = true;
        }

        return ApiResponse.ok(new VersionCheckResult(
                updateRequired,
                forceUpdate,
                latest.downloadUrl != null ? latest.downloadUrl : "",
                latest.notes != null ? latest.notes : "",
                latest.versionName,
                latest.versionCode
        ));
    }

    private static class VersionRow {
        final String id;
        final String platform;
        final String versionName;
        final int versionCode;
        final String downloadUrl;
        final boolean forceUpdate;
        final String notes;
        final int rolloutPercent;
        final int minVersionCode;
        final String status;

        VersionRow(String id, String platform, String versionName, int versionCode, String downloadUrl,
                   boolean forceUpdate, String notes, int rolloutPercent, int minVersionCode, String status) {
            this.id = id;
            this.platform = platform;
            this.versionName = versionName;
            this.versionCode = versionCode;
            this.downloadUrl = downloadUrl;
            this.forceUpdate = forceUpdate;
            this.notes = notes;
            this.rolloutPercent = rolloutPercent;
            this.minVersionCode = minVersionCode;
            this.status = status;
        }
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Accessors(fluent = true)
    public static class VersionCheckResult {
        private boolean updateRequired;
        private boolean forceUpdate;
        private String downloadUrl;
        private String releaseNotes;
        private String latestVersion;
        private int latestVersionCode;
    }
}
