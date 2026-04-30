package com.runclub.api.service;

import com.runclub.api.entity.Activity;
import com.runclub.api.entity.User;
import com.runclub.api.repository.ActivityRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    public String buildHtmlPage(Activity activity, String canonicalHttpsUrl, String deepLinkRunclub) {
        String title = escapeHtml(safeTitle(activity));
        String desc = escapeHtml(buildDescription(activity));
        Optional<String> img = mapboxPreviewImageUrl(activity.getMapPolyline());

        StringBuilder meta = new StringBuilder();
        meta.append("<meta charset=\"utf-8\"/>");
        meta.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>");
        meta.append("<title>").append(title).append(" · Run Club</title>");
        meta.append("<link rel=\"canonical\" href=\"").append(escapeAttr(canonicalHttpsUrl)).append("\"/>");
        meta.append("<meta property=\"og:title\" content=\"").append(title).append("\"/>");
        meta.append("<meta property=\"og:description\" content=\"").append(desc).append("\"/>");
        meta.append("<meta property=\"og:url\" content=\"").append(escapeAttr(canonicalHttpsUrl)).append("\"/>");
        meta.append("<meta property=\"og:type\" content=\"website\"/>");
        meta.append("<meta name=\"twitter:card\" content=\"").append(img.isPresent() ? "summary_large_image" : "summary").append("\"/>");
        meta.append("<meta name=\"twitter:title\" content=\"").append(title).append("\"/>");
        meta.append("<meta name=\"twitter:description\" content=\"").append(desc).append("\"/>");
        img.ifPresent(u -> {
            meta.append("<meta property=\"og:image\" content=\"").append(escapeAttr(u)).append("\"/>");
            meta.append("<meta name=\"twitter:image\" content=\"").append(escapeAttr(u)).append("\"/>");
        });

        String body =
            "<main style=\"font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0f0f0f;color:#eee;padding:2rem;max-width:36rem;margin:0 auto;\">"
                + "<h1 style=\"font-size:1.25rem;margin:0 0 0.5rem;\">" + title + "</h1>"
                + "<p style=\"color:#aaa;margin:0 0 1.5rem;line-height:1.5;\">" + desc + "</p>"
                + "<p><a href=\"" + escapeAttr(deepLinkRunclub) + "\" style=\"color:#4DA3FF;\">Open in Run Club app</a></p>"
                + "</main>";

        return "<!DOCTYPE html><html lang=\"en\"><head>" + meta + "</head><body>" + body + "</body></html>";
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
