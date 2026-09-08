package dev.saltt.hub;

import org.bson.BsonDocument;

/** Builds a HubConfig the way the engine does, from its JSON form. */
public final class TestConfigs {

    private TestConfigs() {
    }

    public static HubConfig hub(String json) {
        HubConfig config = HubConfig.CODEC.decode(BsonDocument.parse(json));
        if (config == null) {
            throw new IllegalArgumentException("config did not decode: " + json);
        }
        return config;
    }

    /** Two regions, one node in each, a 2-4 player survival games queue with no fill delay. */
    public static HubConfig twoRegions() {
        return hub("""
                {
                  "Regions": [
                    {"Id": "na", "Latitude": 40.7, "Longitude": -74.0},
                    {"Id": "eu", "Latitude": 50.1, "Longitude": 8.7}
                  ],
                  "HubRegion": "na",
                  "InstancerNodes": [
                    {"Id": "na-1", "GrpcHost": "na", "GrpcPort": 50051, "PlayerHost": "na", "PlayerPort": 5530, "MaxMatches": 2, "Region": "na"},
                    {"Id": "eu-1", "GrpcHost": "eu", "GrpcPort": 50051, "PlayerHost": "eu", "PlayerPort": 5530, "MaxMatches": 2, "Region": "eu"}
                  ],
                  "QueueRules": [
                    {"GameType": "SURVIVAL_GAMES", "MinPlayers": 2, "MaxPlayers": 4, "FillWindowSeconds": 0, "MaxWaitAfterMinSeconds": 0}
                  ],
                  "GroupingDepth": 0,
                  "ReferTimeoutSeconds": 1,
                  "ApiToken": "test-secret"
                }
                """);
    }
}
