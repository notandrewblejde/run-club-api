package com.runclub.api.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Stripe-style collection envelope.
 *
 * <pre>
 * { "object": "list", "data": [...], "has_more": false, "total_count": 42, "url": "/v1/activities" }
 * </pre>
 */
@Schema(name = "List")
public class ApiList<T> {
    @JsonProperty("object")
    public final String object = "list";

    @JsonProperty("data")
    public List<T> data;

    @JsonProperty("has_more")
    public boolean hasMore;

    @JsonProperty("total_count")
    public long totalCount;

    @JsonProperty("url")
    public String url;

    public ApiList() {}

    public ApiList(List<T> data, boolean hasMore, long totalCount, String url) {
        this.data = data;
        this.hasMore = hasMore;
        this.totalCount = totalCount;
        this.url = url;
    }

    public static <T> ApiList<T> of(List<T> data, boolean hasMore, long totalCount, String url) {
        return new ApiList<>(data, hasMore, totalCount, url);
    }
}
