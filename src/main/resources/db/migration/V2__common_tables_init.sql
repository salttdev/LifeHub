-- Common match tables. Before the per-game tables, which reference match_player_stats.

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

CREATE TABLE match_player_stats (
                                    match_id       CHAR(36) NOT NULL,
                                    player_uuid    CHAR(36) NOT NULL,

                                    kills          INT NOT NULL DEFAULT 0,
                                    deaths         INT NOT NULL DEFAULT 0,
                                    assists        INT NOT NULL DEFAULT 0,
                                    damage_dealt   BIGINT NOT NULL DEFAULT 0,
                                    damage_taken   BIGINT NOT NULL DEFAULT 0,

                                    PRIMARY KEY (match_id, player_uuid),

                                    FOREIGN KEY (match_id)
                                        REFERENCES life_match(match_id),

                                    FOREIGN KEY (player_uuid)
                                        REFERENCES life_player(uuid)
);
