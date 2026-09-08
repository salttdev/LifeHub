-- Survival Games extras, one row per participant, hanging off the common stats row.

CREATE TABLE survival_games_match_player (
                                             match_id        CHAR(36) NOT NULL,
                                             player_uuid     CHAR(36) NOT NULL,

                                             placement       INT NOT NULL DEFAULT 0,
                                             time_alive      BIGINT NOT NULL DEFAULT 0,
                                             killer_uuid     CHAR(36),

                                             -- Not carried by MatchResult yet; written null until
                                             -- the protocol grows them.
                                             team_uuid       CHAR(36),
                                             cause_of_death  VARCHAR(255),

                                             PRIMARY KEY (match_id, player_uuid),

                                             FOREIGN KEY (match_id, player_uuid)
                                                 REFERENCES match_player_stats(match_id, player_uuid)
);
