package com.runclub.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(name = "Kudo")
public class Kudo {
    @JsonProperty("object")
    public final String object = "kudo";

    @JsonProperty("activity_id")
    public UUID activityId;

    @JsonProperty("kudoed_by_viewer")
    public boolean kudoedByViewer;

    @JsonProperty("kudos_count")
    public int kudosCount;

    public Kudo() {}

    public Kudo(UUID activityId, boolean kudoedByViewer, int kudosCount) {
        this.activityId = activityId;
        this.kudoedByViewer = kudoedByViewer;
        this.kudosCount = kudosCount;
    }
}
