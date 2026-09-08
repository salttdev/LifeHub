package dev.saltt.hub.matchmaking.region;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/** One region players can be sent to, placed where its datacenter is. */
public class RegionConfig {

    public static final BuilderCodec<RegionConfig> CODEC = BuilderCodec
            .builder(RegionConfig.class, RegionConfig::new)
            .append(new KeyedCodec<String>("Id", Codec.STRING),
                    (r, v, info) -> r.id = v == null ? "" : v.strip(), (r, info) -> r.id)
            .add()
            .append(new KeyedCodec<Double>("Latitude", Codec.DOUBLE),
                    (r, v, info) -> r.latitude = v == null ? 0 : v, (r, info) -> r.latitude)
            .add()
            .append(new KeyedCodec<Double>("Longitude", Codec.DOUBLE),
                    (r, v, info) -> r.longitude = v == null ? 0 : v, (r, info) -> r.longitude)
            .add()
            .build();

    private String id = "";
    private double latitude;
    private double longitude;

    public RegionConfig() {
    }

    public RegionConfig(String id, double latitude, double longitude) {
        this.id = id;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String id() { return id; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }

    public boolean isUsable() {
        return !id.isBlank();
    }

    @Override
    public String toString() {
        return id + " (" + latitude + ", " + longitude + ")";
    }
}
