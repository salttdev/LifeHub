CREATE TABLE life_player_name (
    player_uuid  CHAR(36)    NOT NULL,
    username     VARCHAR(64) NOT NULL,
    first_seen   DATETIME    NOT NULL,
    last_seen    DATETIME    NOT NULL,

    PRIMARY KEY (player_uuid, username),
    FOREIGN KEY (player_uuid) REFERENCES life_player(uuid) ON DELETE CASCADE,

    INDEX idx_username (username)
);
