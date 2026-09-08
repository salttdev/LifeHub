-- Smoothed round trip time per player per region, fed by the nodes' match results and the hub's
-- own ping samples. What the matchmaker prefers over a GeoIP estimate.
CREATE TABLE player_region_latency (
    player_uuid  CHAR(36)    NOT NULL,
    region       VARCHAR(32) NOT NULL,
    rtt_millis   INT         NOT NULL,
    samples      INT         NOT NULL DEFAULT 1,
    updated_at   DATETIME    NOT NULL,

    PRIMARY KEY (player_uuid, region)
);
