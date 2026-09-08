-- One row per match of any game type; the per-game tables hang off match_player_stats.
CREATE TABLE life_match (
    match_id     CHAR(36)    NOT NULL PRIMARY KEY,
    game_type    VARCHAR(32) NOT NULL,
    map_name     VARCHAR(64),
    node_id      VARCHAR(64),
    started_at   DATETIME    NOT NULL,
    ended_at     DATETIME,
    abandoned    TINYINT(1)  NOT NULL DEFAULT 0,
    winner_uuid  CHAR(36),

    INDEX idx_game_type (game_type),
    INDEX idx_started_at (started_at)
);
