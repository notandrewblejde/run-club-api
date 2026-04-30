package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Public activity link previews (Open Graph HTML). Policy: {@value #POLICY_PUBLIC_PROFILE_ONLY}.
 */
@Service
public class ActivitySharePreviewService {

    /** Only activities whose owner has a public profile are link-shareable (no token in MVP). */
    public static final String POLICY_PUBLIC_PROFILE_ONLY = "public_profile_only";

    private final ActivityRepository activityRepository;

    @Value("${mapbox.access-token:}")
    private String mapboxAccessToken;

    @Value("${runclub.share-page.ios-app-store-id:}")
    private String iosAppStoreId;

    @Value("${runclub.share-page.app-store-url:}")
    private String appStoreUrl;

    @Value("${runclub.share-page.play-store-url:https://play.google.com/store/apps/details?id=app.runclub}")
    private String playStoreUrl;

    public ActivitySharePreviewService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public Optional<Activity> findShareableActivity(UUID activityId) {
        Optional<Activity> opt = activityRepository.findByIdWithUser(activityId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        Activity a = opt.get();
        User owner = a.getUser();
        if (owner == null || !"public".equalsIgnoreCase(owner.getPrivacyLevel())) {
            return Optional.empty();
        }
        return Optional.of(a);
    }

    /**
     * Mapbox Static Images URL for OG / Twitter cards (1200×630). Empty token or missing polyline → empty optional.
     */
    public Optional<String> mapboxPreviewImageUrl(String encodedPolyline) {
        if (mapboxAccessToken == null || mapboxAccessToken.isBlank()) {
            return Optional.empty();
        }
        if (encodedPolyline == null || encodedPolyline.isBlank()) {
            return Optional.empty();
        }
        String pathOverlay = "path-4+FF6B35-0.9(" + URLEncoder.encode(encodedPolyline, StandardCharsets.UTF_8) + ")";
        String url = "https://api.mapbox.com/styles/v1/mapbox/dark-v11/static/"
            + pathOverlay
            + "/auto/1200x630@2x?access_token="
            + URLEncoder.encode(mapboxAccessToken, StandardCharsets.UTF_8)
            + "&padding=40";
        return Optional.of(url);
    }

    public String buildHtmlPage(Activity activity, String canonicalHttpsUrl, String deepLinkRunclub, UUID activityId) {
        String desc = escapeHtml(buildDescription(activity));
        Optional<String> img = mapboxPreviewImageUrl(activity.getMapPolyline());
        String ogTitle = escapeHtml(buildOgTitle(activity));

        StringBuilder meta = new StringBuilder();
        meta.append("<meta charset=\"utf-8\"/>");
        meta.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        meta.append("<meta name=\"theme-color\" content=\"#0f0f0f\"/>");
        meta.append("<meta property=\"og:site_name\" content=\"Run Club\"/>");
        meta.append("<title>").append(ogTitle).append("</title>");
        meta.append("<link rel=\"canonical\" href=\"").append(escapeAttr(canonicalHttpsUrl)).append("\"/>");
        meta.append("<meta property=\"og:title\" content=\"").append(ogTitle).append("\"/>");
        meta.append("<meta property=\"og:description\" content=\"").append(desc).append("\"/>");
        meta.append("<meta property=\"og:url\" content=\"").append(escapeAttr(canonicalHttpsUrl)).append("\"/>");
        meta.append("<meta property=\"og:type\" content=\"website\"/>");
        meta.append("<meta name=\"twitter:card\" content=\"").append(img.isPresent() ? "summary_large_image" : "summary").append("\"/>");
        meta.append("<meta name=\"twitter:title\" content=\"").append(ogTitle).append("\"/>");
        meta.append("<meta name=\"twitter:description\" content=\"").append(desc).append("\"/>");
        img.ifPresent(u -> {
            meta.append("<meta property=\"og:image\" content=\"").append(escapeAttr(u)).append("\"/>");
            meta.append("<meta name=\"twitter:image\" content=\"").append(escapeAttr(u)).append("\"/>");
        });
        if (StringUtils.hasText(iosAppStoreId)) {
            String arg = escapeAttr(deepLinkRunclub);
            meta.append("<meta name=\"apple-itunes-app\" content=\"app-id=").append(escapeAttr(iosAppStoreId.trim()))
                .append(", app-argument=").append(arg).append("\"/>");
        }

        String body = buildRichBody(activity, ogTitle, desc, img, deepLinkRunclub, activityId);

        return "<!DOCTYPE html><html lang=\"en\"><head>" + meta + "</head><body>" + body + "</body></html>";
    }

    private String buildRichBody(
        Activity activity,
        String headlineHtml,
        String descHtml,
        Optional<String> heroImg,
        String deepLinkRunclub,
        UUID activityId
    ) {
        // ── Data extraction ───────────────────────────────────────────────
        String deepJs = escapeJsString(deepLinkRunclub);
        User owner = activity.getUser();
        String runnerName = (owner != null && owner.getDisplayName() != null && !owner.getDisplayName().isBlank())
            ? owner.getDisplayName()
            : (owner != null (owner != null && owner.getFirstName() != null ? owner.getFirstName() : "Runner")(owner != null && owner.getFirstName() != null ? owner.getFirstName() : "Runner") owner.getDisplayName() != null ? owner.getDisplayName() : "Runner");
        String avatarUrl = owner != null ? owner.getProfilePicUrl() : null;

        java.math.BigDecimal distMi = activity.getDistanceMiles();
        String distStr = distMi != null ? String.format("%.2f", distMi.doubleValue()) : "—";
        String pace = activity.getAvgPaceDisplay();
        Integer movingSecs = activity.getMovingTimeSeconds();
        String timeStr = "—";
        if (movingSecs != null && movingSecs > 0) {
            int h = movingSecs / 3600, m = (movingSecs % 3600) / 60, s = movingSecs % 60;
            timeStr = h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
        }
        java.math.BigDecimal elevFt = activity.getElevationGainFt();
        String elevStr = elevFt != null ? String.format("%.0f ft", elevFt.doubleValue()) : "—";
        Integer avgHr = activity.getAvgHeartRateBpm();
        String hrStr = avgHr != null ? avgHr + " bpm" : "—";
        String location = "";
        if (activity.getCity() != null) location = activity.getCity();
        if (activity.getState() != null) location += (location.isEmpty() ? "" : ", ") + activity.getState();
        java.time.LocalDateTime startDate = activity.getStartDate();
        String dateStr = startDate != null
            ? startDate.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))
            : "";

        // ── Map block ────────────────────────────────────────────────────
        String mapBlock = heroImg
            .map(u -> "<div class=\"map-wrap\"><img class=\"map-img\" src=\"" + escapeAttr(u) + "\" alt=\"Route map\" loading=\"eager\"/></div>")
            .orElse("<div class=\"map-fallback\"><div class=\"map-logo\">🏃</div></div>");

        // ── Avatar HTML ──────────────────────────────────────────────────
        String avatarHtml = (avatarUrl != null && !avatarUrl.isBlank())
            ? "<img class=\"avatar\" src=\"" + escapeAttr(avatarUrl) + "\" alt=\"\" />"
            : "<div class=\"avatar avatar-placeholder\">" + escapeHtml(runnerName.substring(0, 1).toUpperCase()) + "</div>";

        // ── Store buttons ────────────────────────────────────────────────
        String iosBtn = resolveIosStoreUrl()
            .map(url -> "<a class=\"store-btn\" href=\"" + escapeAttr(url) + "\" target=\"_blank\" rel=\"noopener\">📱 App Store</a>")
            .orElse("");
        String playBtn = StringUtils.hasText(playStoreUrl)
            ? "<a class=\"store-btn\" href=\"" + escapeAttr(playStoreUrl.trim()) + "\" target=\"_blank\" rel=\"noopener\">🤖 Google Play</a>"
            : "";

        // ── CSS ──────────────────────────────────────────────────────────
        String css = "<style>"
            + "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
            + "background:#0a0a0a;color:#fff;min-height:100vh;display:flex;align-items:flex-start;"
            + "justify-content:center;padding:0 0 40px}"
            + ".card{width:100%;max-width:480px;background:#111;overflow:hidden;border-radius:0 0 24px 24px}"
            + "@media(min-width:520px){body{align-items:center;padding:32px 16px}"
            + ".card{border-radius:24px;box-shadow:0 32px 64px rgba(0,0,0,.6)}}"
            // Map
            + ".map-wrap{width:100%;aspect-ratio:16/9;overflow:hidden;position:relative;background:#0d0d0d}"
            + ".map-img{width:100%;height:100%;object-fit:cover;display:block}"
            + ".map-fallback{width:100%;aspect-ratio:16/9;display:flex;align-items:center;justify-content:center;"
            + "background:linear-gradient(135deg,#1a1a2e,#0a0a0a);font-size:3rem}"
            // Runner row
            + ".runner{display:flex;align-items:center;gap:12px;padding:18px 20px 12px}"
            + ".avatar{width:44px;height:44px;border-radius:50%;object-fit:cover;border:2px solid rgba(255,255,255,.12)}"
            + ".avatar-placeholder{width:44px;height:44px;border-radius:50%;background:#FC4C02;"
            + "display:flex;align-items:center;justify-content:center;font-weight:700;font-size:1.1rem;color:#fff}"
            + ".runner-info{flex:1;min-width:0}"
            + ".runner-name{font-weight:600;font-size:.95rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"
            + ".runner-meta{font-size:.78rem;color:#6e6e75;margin-top:2px}"
            // Activity title
            + ".activity-title{font-size:1.3rem;font-weight:700;line-height:1.25;padding:0 20px 16px}"
            // Stats grid
            + ".stats{display:grid;grid-template-columns:1fr 1fr 1fr;gap:0;border-top:1px solid rgba(255,255,255,.07);"
            + "border-bottom:1px solid rgba(255,255,255,.07);margin-bottom:20px}"
            + ".stat{padding:16px 10px;text-align:center;border-right:1px solid rgba(255,255,255,.07)}"
            + ".stat:last-child{border-right:none}"
            + ".stat-value{font-size:1.25rem;font-weight:700;line-height:1;color:#fff}"
            + ".stat-label{font-size:.68rem;color:#6e6e75;text-transform:uppercase;letter-spacing:.06em;margin-top:4px}"
            // Second stats row
            + ".stats-row2{display:flex;gap:0;border-bottom:1px solid rgba(255,255,255,.07);margin-bottom:20px}"
            + ".stat2{flex:1;padding:12px 10px;text-align:center;border-right:1px solid rgba(255,255,255,.07)}"
            + ".stat2:last-child{border-right:none}"
            + ".stat2 .stat-value{font-size:1.05rem}"
            // CTA
            + ".cta{padding:0 20px 8px}"
            + ".btn-open{display:block;width:100%;padding:15px;background:#FC4C02;color:#fff;text-align:center;"
            + "text-decoration:none;border-radius:12px;font-weight:700;font-size:1rem;letter-spacing:.01em;"
            + "transition:filter .15s}"
            + ".btn-open:hover{filter:brightness(1.1)}"
            + ".store-row{display:flex;gap:8px;padding:12px 20px 0}"
            + ".store-btn{flex:1;padding:11px 8px;background:rgba(255,255,255,.08);color:#fff;text-align:center;"
            + "text-decoration:none;border-radius:10px;font-size:.8rem;font-weight:500;"
            + "border:1px solid rgba(255,255,255,.1)}"
            + ".footer{padding:16px 20px 0;font-size:.75rem;color:#444;text-align:center}"
            + "</style>";

        // ── HTML ─────────────────────────────────────────────────────────
        String html = css
            + "<div class=\"card\">"
            + mapBlock
            + "<div class=\"runner\">"
            + avatarHtml
            + "<div class=\"runner-info\">"
            + "<div class=\"runner-name\">" + escapeHtml(runnerName) + "</div>"
            + "<div class=\"runner-meta\">" + (dateStr.isEmpty() ? "" : escapeHtml(dateStr))
            + (location.isEmpty() ? "" : (dateStr.isEmpty() ? "" : " · ") + escapeHtml(location)) + "</div>"
            + "</div></div>"
            + "<div class=\"activity-title\">" + headlineHtml + "</div>"
            + "<div class=\"stats\">"
            + "<div class=\"stat\"><div class=\"stat-value\">" + escapeHtml(distStr) + "</div><div class=\"stat-label\">miles</div></div>"
            + "<div class=\"stat\"><div class=\"stat-value\">" + escapeHtml(pace != null ? pace : "—") + "</div><div class=\"stat-label\">avg pace</div></div>"
            + "<div class=\"stat\"><div class=\"stat-value\">" + escapeHtml(timeStr) + "</div><div class=\"stat-label\">time</div></div>"
            + "</div>"
            + "<div class=\"stats-row2\">"
            + "<div class=\"stat2\"><div class=\"stat-value\">" + escapeHtml(elevStr) + "</div><div class=\"stat-label\">elevation</div></div>"
            + "<div class=\"stat2\"><div class=\"stat-value\">" + escapeHtml(hrStr) + "</div><div class=\"stat-label\">avg hr</div></div>"
            + "</div>"
            + "<div class=\"cta\"><a class=\"btn-open\" id=\"rc-open\" href=\"" + escapeAttr(deepLinkRunclub) + "\">Open in Run Club</a></div>"
            + ((!iosBtn.isEmpty() || !playBtn.isEmpty()) ? "<div class=\"store-row\">" + iosBtn + playBtn + "</div>" : "")
            + "<div class=\"footer\">Run Club · Share your run</div>"
            + "</div>"
            + "<script>(function(){var d=" + deepJs + ";setTimeout(function(){try{window.location.href=d}catch(e){}},200);})();</script>";

        return html;
    }

        private Optional<String> resolveIosStoreUrl() {
        if (StringUtils.hasText(appStoreUrl)) {
            return Optional.of(appStoreUrl.trim());
        }
        if (StringUtils.hasText(iosAppStoreId)) {
            return Optional.of("https://apps.apple.com/app/id" + iosAppStoreId.trim());
        }
        return Optional.empty();
    }

    private static String buildOgTitle(Activity a) {
        BigDecimal mi = a.getDistanceMiles();
        String base = safeTitle(a);
        if (mi != null && mi.compareTo(BigDecimal.ZERO) > 0) {
            return String.format("%.2f mi · Run Club", mi.doubleValue());
        }
        return base + " · Run Club";
    }

    private static String escapeJsString(String s) {
        if (s == null) {
            return "\"\"";
        }
        String escaped = s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("<", "\\u003c");
        return "\"" + escaped + "\"";
    }

    private static String safeTitle(Activity a) {
        String n = a.getName();
        return (n == null || n.isBlank()) ? "Run" : n.trim();
    }

    private static String buildDescription(Activity a) {
        StringBuilder sb = new StringBuilder();
        BigDecimal mi = a.getDistanceMiles();
        if (mi != null) {
            sb.append(String.format("%.2f mi", mi.doubleValue()));
        }
        String pace = a.getAvgPaceDisplay();
        if (pace != null && !pace.isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(pace);
        }
        if (sb.length() == 0) {
            sb.append("Shared from Run Club");
        }
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static String escapeAttr(String s) {
        return escapeHtml(s).replace("\n", " ");
    }
}
