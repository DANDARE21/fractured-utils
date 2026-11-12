package net.dandare21.fracturedutils.region.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Data model for a spatial polygon region.
 */
public class Region {

    private String id;
    private String name;
    private RegionType type;
    private String parentId;
    private Polygon2D polygon;
    private boolean locked;
    private String requiredFlag;
    private String deniedMessage;
    private int color;
    private String subtitle;
    private String ambientMusic;

    public Region(String id, String name, RegionType type, String parentId, Polygon2D polygon,
                  boolean locked, String requiredFlag, String deniedMessage, int color,
                  String subtitle, String ambientMusic) {
        this.id = id;
        this.name = name != null ? name : id;
        this.type = type != null ? type : RegionType.MAP_ZONE;
        this.parentId = parentId != null ? parentId : "";
        this.polygon = polygon != null ? polygon : new Polygon2D(java.util.List.of());
        this.locked = locked;
        this.requiredFlag = requiredFlag != null ? requiredFlag : "";
        this.deniedMessage = deniedMessage != null ? deniedMessage : "A magical barrier bars your passage.";
        this.color = color != 0 ? color : this.type.getDefaultColor();
        this.subtitle = subtitle != null ? subtitle : "";
        this.ambientMusic = ambientMusic != null ? ambientMusic : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RegionType getType() {
        return type;
    }

    public void setType(RegionType type) {
        this.type = type;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId != null ? parentId : "";
    }

    public Polygon2D getPolygon() {
        return polygon;
    }

    public void setPolygon(Polygon2D polygon) {
        this.polygon = polygon;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public String getRequiredFlag() {
        return requiredFlag;
    }

    public void setRequiredFlag(String requiredFlag) {
        this.requiredFlag = requiredFlag != null ? requiredFlag : "";
    }

    public String getDeniedMessage() {
        return deniedMessage;
    }

    public void setDeniedMessage(String deniedMessage) {
        this.deniedMessage = deniedMessage;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle != null ? subtitle : "";
    }

    public String getAmbientMusic() {
        return ambientMusic;
    }

    public void setAmbientMusic(String ambientMusic) {
        this.ambientMusic = ambientMusic != null ? ambientMusic : "";
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id != null ? id : "");
        tag.putString("name", name != null ? name : "");
        tag.putString("type", type != null ? type.name() : RegionType.MAP_ZONE.name());
        tag.putString("parentId", parentId != null ? parentId : "");
        if (polygon != null) {
            tag.put("polygon", polygon.toNbt());
        }
        tag.putBoolean("locked", locked);
        tag.putString("requiredFlag", requiredFlag != null ? requiredFlag : "");
        tag.putString("deniedMessage", deniedMessage != null ? deniedMessage : "");
        tag.putInt("color", color);
        tag.putString("subtitle", subtitle != null ? subtitle : "");
        tag.putString("ambientMusic", ambientMusic != null ? ambientMusic : "");
        return tag;
    }

    public static Region fromNbt(CompoundTag tag) {
        String id = tag.getString("id");
        String name = tag.getString("name");
        RegionType type = RegionType.fromString(tag.getString("type"));
        String parentId = tag.getString("parentId");
        Polygon2D polygon = tag.contains("polygon") ? Polygon2D.fromNbt(tag.getCompound("polygon")) : new Polygon2D(java.util.List.of());
        boolean locked = tag.getBoolean("locked");
        String requiredFlag = tag.getString("requiredFlag");
        String deniedMsg = tag.contains("deniedMessage") ? tag.getString("deniedMessage") : "A magical barrier bars your passage.";
        int color = tag.getInt("color");
        String subtitle = tag.getString("subtitle");
        String ambientMusic = tag.getString("ambientMusic");

        return new Region(id, name, type, parentId, polygon, locked, requiredFlag, deniedMsg, color, subtitle, ambientMusic);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id != null ? id : "");
        buf.writeUtf(name != null ? name : "");
        buf.writeEnum(type != null ? type : RegionType.MAP_ZONE);
        buf.writeUtf(parentId != null ? parentId : "");
        if (polygon != null) {
            polygon.encode(buf);
        } else {
            new Polygon2D(java.util.List.of()).encode(buf);
        }
        buf.writeBoolean(locked);
        buf.writeUtf(requiredFlag != null ? requiredFlag : "");
        buf.writeUtf(deniedMessage != null ? deniedMessage : "");
        buf.writeInt(color);
        buf.writeUtf(subtitle != null ? subtitle : "");
        buf.writeUtf(ambientMusic != null ? ambientMusic : "");
    }

    public static Region decode(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        RegionType type = buf.readEnum(RegionType.class);
        String parentId = buf.readUtf();
        Polygon2D polygon = Polygon2D.decode(buf);
        boolean locked = buf.readBoolean();
        String requiredFlag = buf.readUtf();
        String deniedMsg = buf.readUtf();
        int color = buf.readInt();
        String subtitle = buf.readUtf();
        String ambientMusic = buf.readUtf();

        return new Region(id, name, type, parentId, polygon, locked, requiredFlag, deniedMsg, color, subtitle, ambientMusic);
    }
}

