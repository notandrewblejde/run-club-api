package com.runclub.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON blobs for Universal Links / App Links. Host at domain root via reverse proxy
 * (Apple expects {@code /.well-known/apple-app-site-association} on the same host as HTTPS links).
 */
@RestController
@RequestMapping("/public/link-support")
public class PublicLinkSupportController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${runclub.ios.bundle-id:app.runclub}")
    private String iosBundleId;

    @Value("${runclub.apple.team-id:}")
    private String appleTeamId;

    @GetMapping(value = "/apple-app-site-association", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> appleAppSiteAssociation() throws Exception {
        if (!StringUtils.hasText(appleTeamId)) {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}");
        }
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode applinks = root.putObject("applinks");
        applinks.putArray("apps");
        ArrayNode details = applinks.putArray("details");
        ObjectNode detail = details.addObject();
        detail.put("appID", appleTeamId + "." + iosBundleId);
        ArrayNode paths = detail.putArray("paths");
        paths.add("/api/public/activities/*");
        return ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=300")
            .body(objectMapper.writeValueAsString(root));
    }

    /**
     * Android Digital Asset Links template. Replace sha256_cert_fingerprints with your release signing cert.
     */
    @GetMapping(value = "/android-assetlinks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> androidAssetLinks() {
        String body = """
            [{
              "relation": ["delegate_permission/common.handle_all_urls"],
              "target": {
                "namespace": "android_app",
                "package_name": "app.runclub",
                "sha256_cert_fingerprints": []
              }
            }]
            """;
        return ResponseEntity.ok()
            .header("Cache-Control", "public, max-age=300")
            .body(body);
    }
}
