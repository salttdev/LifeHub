CREATE TABLE life_player (
    uuid         CHAR(36)    NOT NULL PRIMARY KEY,
    display_name VARCHAR(64),
    first_join   DATETIME    NOT NULL,
    last_joined  DATETIME    NOT NULL
);
