CREATE TABLE match_player_stats (
    match_id       CHAR(36) NOT NULL,
    player_uuid    CHAR(36) NOT NULL,

    kills          INT NOT NULL DEFAULT 0,
    deaths         INT NOT NULL DEFAULT 0,
    assists        INT NOT NULL DEFAULT 0,
    damage_dealt   BIGINT NOT NULL DEFAULT 0,
    damage_taken   BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (match_id, player_uuid),
    FOREIGN KEY (match_id) REFERENCES life_match(match_id),
    FOREIGN KEY (player_uuid) REFERENCES life_player(uuid)
);
