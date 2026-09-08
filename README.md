# LifeHub

Hytale server mod for the hub: the lobby players queue from, the matchmaker that places lobbies
on instancer nodes, and the gRPC database API. Depends on
[LifeProtocol](https://github.com/Life-Steal/LifeProtocol) from JitPack; the instancer nodes
consume the same artifact.

## Flow

1. A player joins the hub. Their IP is looked up in the GeoLite2 City database for coordinates,
   and their stored per-region pings are loaded.
2. `/queue join sg` puts them in the survival games queue. Their ping to the hub is sampled as a
   measurement for `HubRegion`.
3. The matchmaker pass claims a lobby once the queue's clocks say so (see `QueueRules`). With a
   deep queue (`GroupingDepth` or more waiting) the lobby is built from players who share a best
   region.
4. Each region is scored by the worst estimated or measured ping in the lobby, lowest first. The
   nodes of the best region are tried by load; the other region only if
   `AllowCrossRegionFallback`.
5. `SetupMatch` on the node, then every player is referred with a signed `TransferTicket` in the
   referral payload. The node bounces anyone without a valid ticket.
6. Heartbeats from the node keep the match cache current; `claimed_player_ids` is authoritative
   for who is still held. A player the node lets go is free to queue again.
7. Anyone still on the hub `ReferTimeoutSeconds` after being referred is told to `/rejoin` or
   `/forfeit`; if that is the whole lobby the match is cancelled.
8. `MatchFinished` writes the result and stores the pings the node measured per player.

## Commands

| Command | Who | Purpose |
|---|---|---|
| `/queue join [game]`, `/queue leave [game]`, `/queue status` | players | Queueing |
| `/rejoin` | players | Go back to the match that still holds you |
| `/forfeit` | players | Give up that match so you can queue again |
| `/spectate <uuid>` | players | Watch someone's match |
| `/mm nodes`, `/mm matches`, `/mm player <name>` | admins | What the matchmaker believes |

## Config (`LifeHub.json`)

| Key | Meaning |
|---|---|
| `JdbcUrl`, `DbUser`, `DbPassword`, `DbPoolSize` | MySQL; Flyway migrations run at startup |
| `ApiBind`, `ApiPort`, `ApiThreads` | The gRPC server (player API + matchmaker service) |
| `ApiToken` | Secret shared with every node; signs transfer tickets. Must equal each node's `SharedSecret` |
| `Regions` | `[{Id, Latitude, Longitude}]`, one per datacenter |
| `HubRegion` | Which of those the hub itself runs in |
| `GeoIpDatabasePath` | Default `GeoLite2-City.mmdb`, read from the mod data directory next to `LifeHub.json` (`mods/Saltt_LifeHub/GeoLite2-City.mmdb`) unless absolute. The file is reopened when replaced. Missing file: no estimates, measured pings and config order decide |
| `LatencyBaseMillis`, `LatencyMillisPerKm` | Distance-to-ping estimate |
| `AllowCrossRegionFallback` | Try the other region's nodes when the best one is full |
| `GroupingDepth` | Queue size from which lobbies are grouped by region; 0 disables |
| `InstancerNodes` | `[{Id, GrpcHost, GrpcPort, PlayerHost, PlayerPort, MaxMatches, Region}]` |
| `QueueRules` | Per game type: `MinPlayers`, `MaxPlayers`, `FillWindowSeconds`, `MaxWaitAfterMinSeconds` |
| `MatchmakerIntervalMillis` | How often the pass runs |
| `MatchStaleAfterSeconds` | Heartbeat silence before a match is dropped and its players freed |
| `SetupMatchDeadlineSeconds` | Covers a world load on the node |
| `ReferTimeoutSeconds` | Grace for a referred player to leave the hub |

## Local loop

The defaults of both mods already describe one hub and one node on the same machine: hub game
port 5520 and API port 8080, node `instancer-1` in region `na` with gRPC on 50051 and its game
port on 5530, secrets equal. Only the node's listen port has to be set by hand.

1. MySQL: `docker run -d --name life-mysql -e MYSQL_DATABASE=life -e MYSQL_USER=life
   -e MYSQL_PASSWORD=change-me -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8`
2. `./gradlew devServer` here. First start writes `devserver/mods/Saltt_LifeHub/LifeHub.json`.
3. In LifeInstancer: place an instance template under the dev server's assets and list it in
   `MapsConfig.json` with a `MapConfig` that has spawns, then start its dev server listening on
   5530 (the server flag is `--bind 0.0.0.0:5530`; pass it through the run configuration).
4. Optional: `GeoLite2-City.mmdb` into `devserver/mods/Saltt_LifeHub/`. Without it every player
   is region-less and lobbies land on the first configured region.
5. Two players on the hub, `/queue join sg`, wait out `FillWindowSeconds`, and watch
   `/mm matches` on the hub and the `[Match ...]` lines on the node.

## Build

Java 25. `./gradlew shadowJar` produces the deployable jar (the Hytale server is excluded);
`./gradlew test` runs the unit tests, which need no engine.
