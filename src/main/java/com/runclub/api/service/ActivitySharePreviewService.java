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
        String deepJs = escapeJsString(deepLinkRunclub);
        String heroBlock = heroImg
            .map(u -> "<div class=\"hero\"><img src=\"" + escapeAttr(u) + "\" alt=\"\" loading=\"eager\"/></div>")
            .orElse("<div class=\"hero hero-fallback\"><span class=\"hero-mark\">Run Club</span></div>");

        String iosBtn = resolveIosStoreUrl()
            .map(url -> "<a class=\"btn btn-ios\" href=\"" + escapeAttr(url) + "\" rel=\"noopener noreferrer\" target=\"_blank\">Download on the App Store</a>")
            .orElse("");

        String playBtn = "";
        if (StringUtils.hasText(playStoreUrl)) {
            playBtn = "<a class=\"btn btn-play\" href=\"" + escapeAttr(playStoreUrl.trim())
                + "\" rel=\"noopener noreferrer\" target=\"_blank\">Get it on Google Play</a>";
        }

        String storeRow = "";
        if (!iosBtn.isEmpty() || !playBtn.isEmpty()) {
            storeRow = "<div class=\"store-row\">" + iosBtn + playBtn + "</div>"
                + "<p class=\"hint\">Don't have the app yet? Grab Run Club to see full stats, comments, and your clubs.</p>";
        } else {
            storeRow = "<p class=\"hint\">Get Run Club on the App Store or Google Play to open this activity in the app.</p>";
        }

        return ""
            + "<style>"
            + "*,*::before,*::after{box-sizing:border-box;}"
            + "body{margin:0;font-family:system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;background:#0a0a0b;color:#f2f2f2;"
            + "min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:flex-start;padding:24px 18px 40px;}"
            + ".card{width:100%;max-width:420px;background:linear-gradient(165deg,#18181c 0%,#121214 100%);border-radius:20px;"
            + "border:1px solid rgba(255,255,255,.08);box-shadow:0 24px 48px rgba(0,0,0,.45);overflow:hidden;}"
            + ".hero{width:100%;aspect-ratio:16/10;background:#111;position:relative;}"
            + ".hero img{width:100%;height:100%;object-fit:cover;display:block;}"
            + ".hero-fallback{display:flex;align-items:center;justify-content:center;background:radial-gradient(circle at 30% 20%,#2a1810,#0a0a0b);}"
            + ".hero-mark{font-weight:800;font-size:1.1rem;letter-spacing:.06em;color:#ff6b35;}"
            + ".pad{padding:22px 20px 24px;}"
            + "h1{margin:0 0 10px;font-size:1.35rem;line-height:1.25;font-weight:700;}"
            + ".desc{margin:0 0 22px;color:#a8a8ae;font-size:.98rem;line-height:1.45;}"
            + ".actions{display:flex;flex-direction:column;gap:10px;}"
            + ".btn{display:inline-flex;align-items:center;justify-content:center;text-decoration:none;font-weight:600;"
            + "font-size:.95rem;padding:14px 18px;border-radius:12px;border:none;cursor:pointer;text-align:center;}"
            + ".btn-primary{background:linear-gradient(180deg,#4da3ff,#2d7dd2);color:#fff;}"
            + ".btn-primary:hover{filter:brightness(1.06);}"
            + ".store-row{display:flex;flex-wrap:wrap;gap:10px;margin-top:18px;}"
            + ".btn-ios,.btn-play{flex:1;min-width:140px;background:rgba(255,255,255,.1);color:#fff;font-size:.85rem;padding:12px 14px;}"
            + ".hint{margin:14px 0 0;font-size:.8rem;color:#6e6e75;line-height:1.4;}"
            + ".sub{margin-top:18px;font-size:.75rem;color:#555;text-align:center;}"
            + "</style>"
            + "<div class=\"card\">"
            + heroBlock
            + "<div class=\"pad\">"
            + "<h1>" + headlineHtml + "</h1>"
            + "<p class=\"desc\">" + descHtml + "</p>"
            + "<div class=\"actions\">"
            + "<a id=\"rc-open\" class=\"btn btn-primary\" href=\"" + escapeAttr(deepLinkRunclub) + "\">Open in Run Club</a>"
            + "</div>"
            + storeRow
            + "<p class=\"sub\">Activity " + escapeHtml(activityId.toString()) + "</p>"
            + "</div></div>"
            + "<script>"
            + "(function(){"
            + "var deep=" + deepJs + ";"
            + "function tryOpen(){try{window.location.href=deep;}catch(e){}}"
            + "if(document.readyState==='loading'){document.addEventListener('DOMContentLoaded',function(){"
            + "setTimeout(tryOpen,150);});}else{setTimeout(tryOpen,150);}"
            + "})();"
            + "</script>";
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
