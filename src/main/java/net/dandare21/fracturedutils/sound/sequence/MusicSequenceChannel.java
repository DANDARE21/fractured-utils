package net.dandare21.fracturedutils.sound.sequence;

import java.util.Locale;
import java.util.UUID;

public class MusicSequenceChannel {
    public static final String TYPE_COMMAND = "COMMAND";
    public static final String TYPE_DIALOG = "DIALOG";
    public static final String TYPE_SCREEN_EFFECT = "SCREEN_EFFECT";
    @Deprecated
    public static final String TYPE_OBJECTIVE = "OBJECTIVE";
    public static final String TYPE_CAMERA = "CAMERA";
    public static final String TYPE_CHECKPOINT = "CHECKPOINT";
    public static final String TYPE_PUPPET = "PUPPET";

    public static final int COLOR_COMMAND = 0xFF00E5FF;
    public static final int COLOR_DIALOG = 0xFFFFD700;
    public static final int COLOR_SCREEN_EFFECT = 0xFFFF00CC;
    public static final int COLOR_OBJECTIVE = 0xFFFF00CC;
    public static final int COLOR_CAMERA = 0xFFFF0055;
    public static final int COLOR_CHECKPOINT = 0xFFFF9900;
    public static final int COLOR_PUPPET = 0xFFAA55FF;
    public static final int COLOR_PUPPET_SUB = 0xFFBB77FF;

    private String id;
    private String type;
    private String name;
    private String puppetActor;
    private int color;

    private String actorName;
    private String actorTag;
    private String actorEntityType;
    private boolean actorRegisteredOnly;
    private String parentChannelId = "";

    public MusicSequenceChannel() {
        this(UUID.randomUUID().toString(), TYPE_COMMAND, "Command Channel", "", COLOR_COMMAND);
    }

    public MusicSequenceChannel(String id, String type, String name, String puppetActor, int color) {
        this.id = (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
        String t = (type != null && !type.isBlank()) ? type.toUpperCase(Locale.ROOT) : TYPE_COMMAND;
        if (TYPE_OBJECTIVE.equalsIgnoreCase(t)) t = TYPE_SCREEN_EFFECT;
        this.type = t;
        this.puppetActor = puppetActor != null ? puppetActor : "";
        this.color = color != 0 ? color : getDefaultColorForType(this.type);
        this.name = (name != null && !name.isBlank()) ? name : getDefaultNameForType(this.type, this.puppetActor);
    }

    public static int getDefaultColorForType(String type) {
        if (type == null) return COLOR_COMMAND;
        return switch (type.toUpperCase(Locale.ROOT)) {
            case TYPE_DIALOG -> COLOR_DIALOG;
            case TYPE_SCREEN_EFFECT, TYPE_OBJECTIVE -> COLOR_SCREEN_EFFECT;
            case TYPE_CAMERA -> COLOR_CAMERA;
            case TYPE_CHECKPOINT -> COLOR_CHECKPOINT;
            case TYPE_PUPPET -> COLOR_PUPPET;
            default -> COLOR_COMMAND;
        };
    }

    public static String getDefaultNameForType(String type, String puppetActor) {
        if (type == null) return "Command Track";
        return switch (type.toUpperCase(Locale.ROOT)) {
            case TYPE_DIALOG -> "Dialog Track";
            case TYPE_SCREEN_EFFECT, TYPE_OBJECTIVE -> "Screen Effect Track";
            case TYPE_CAMERA -> "Camera Track";
            case TYPE_CHECKPOINT -> "Checkpoint Track";
            case TYPE_PUPPET -> (puppetActor != null && !puppetActor.isBlank()) ? ("Puppet: " + puppetActor) : "Puppet Track";
            default -> "Command Track";
        };
    }

    public String getId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type != null ? type : TYPE_COMMAND;
    }

    public void setType(String type) {
        this.type = type != null ? type.toUpperCase(Locale.ROOT) : TYPE_COMMAND;
    }

    public String getName() {
        if (name == null || name.isBlank()) {
            return getDefaultNameForType(type, puppetActor);
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPuppetActor() {
        if (actorTag != null && !actorTag.isBlank()) {
            return "@e[tag=" + actorTag.trim() + ",limit=1]";
        }
        return puppetActor != null ? puppetActor : "";
    }

    public void setPuppetActor(String puppetActor) {
        this.puppetActor = puppetActor != null ? puppetActor : "";
    }

    public String getActorName() {
        return (actorName != null && !actorName.isBlank()) ? actorName : name;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorTag() {
        if (actorTag != null && !actorTag.isBlank()) {
            return actorTag;
        }
        if (puppetActor != null && puppetActor.contains("tag=")) {
            int start = puppetActor.indexOf("tag=") + 4;
            int end = puppetActor.indexOf(",", start);
            if (end == -1) end = puppetActor.indexOf("]", start);
            if (end != -1) return puppetActor.substring(start, end).trim();
            return puppetActor.substring(start).trim();
        }
        return "";
    }

    public void setActorTag(String actorTag) {
        this.actorTag = actorTag;
    }

    public String getActorEntityType() {
        return actorEntityType != null ? actorEntityType : "";
    }

    public void setActorEntityType(String actorEntityType) {
        this.actorEntityType = actorEntityType;
    }

    public String getEntityTypeId() {
        return getActorEntityType();
    }

    public void setEntityTypeId(String entityTypeId) {
        this.actorEntityType = entityTypeId;
    }

    public boolean isActorRegisteredOnly() {
        return actorRegisteredOnly;
    }

    public void setActorRegisteredOnly(boolean actorRegisteredOnly) {
        this.actorRegisteredOnly = actorRegisteredOnly;
    }

    public int getColor() {
        return color != 0 ? color : getDefaultColorForType(type);
    }

    public void setColor(int color) {
        this.color = color;
    }

    public String getParentChannelId() {
        return parentChannelId != null ? parentChannelId : "";
    }

    public void setParentChannelId(String parentChannelId) {
        this.parentChannelId = parentChannelId != null ? parentChannelId : "";
    }

    public boolean isSubChannel() {
        return parentChannelId != null && !parentChannelId.isBlank();
    }

    public MusicSequenceChannel copy() {
        MusicSequenceChannel c = new MusicSequenceChannel(this.id, this.type, this.name, this.puppetActor, this.color);
        c.setActorName(this.actorName);
        c.setActorTag(this.actorTag);
        c.setActorEntityType(this.actorEntityType);
        c.setActorRegisteredOnly(this.actorRegisteredOnly);
        c.setParentChannelId(this.parentChannelId);
        return c;
    }
}
