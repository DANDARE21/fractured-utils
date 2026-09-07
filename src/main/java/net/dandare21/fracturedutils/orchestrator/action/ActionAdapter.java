package net.dandare21.fracturedutils.orchestrator.action;

import com.google.gson.*;
import java.lang.reflect.Type;

public class ActionAdapter implements JsonSerializer<OrchestratorAction>, JsonDeserializer<OrchestratorAction> {
    private static final Gson RAW_GSON = new GsonBuilder().create();

    public static GsonBuilder registerAll(GsonBuilder builder) {
        ActionAdapter adapter = new ActionAdapter();
        builder.registerTypeAdapter(OrchestratorAction.class, adapter);
        builder.registerTypeAdapter(CommandAction.class, adapter);
        builder.registerTypeAdapter(WaitUntilAction.class, adapter);
        builder.registerTypeAdapter(DelayAction.class, adapter);
        builder.registerTypeAdapter(AwaitTriggerAction.class, adapter);
        builder.registerTypeAdapter(ForkSequenceAction.class, adapter);
        builder.registerTypeAdapter(RunSequenceAction.class, adapter);
        builder.registerTypeAdapter(StallParentAction.class, adapter);
        builder.registerTypeAdapter(ResumeParentAction.class, adapter);
        builder.registerTypeAdapter(CheckpointAction.class, adapter);
        builder.registerTypeAdapter(NewObjectiveAction.class, adapter);
        builder.registerTypeAdapter(EndObjectiveAction.class, adapter);
        builder.registerTypeAdapter(PlayMusicSequenceAction.class, adapter);
        builder.registerTypeAdapter(ExecutePuppetAction.class, adapter);
        builder.registerTypeAdapter(PuppetMoveToAction.class, adapter);
        builder.registerTypeAdapter(PuppetLookAtAction.class, adapter);
        builder.registerTypeAdapter(PuppetSuppressAction.class, adapter);
        builder.registerTypeAdapter(PuppetStopAction.class, adapter);
        return builder;
    }

    @Override
    public JsonElement serialize(OrchestratorAction src, Type typeOfSrc, JsonSerializationContext context) {
        JsonElement elem = RAW_GSON.toJsonTree(src);
        if (elem.isJsonObject()) {
            elem.getAsJsonObject().addProperty("type", src.getType());
        }
        return elem;
    }

    @Override
    public OrchestratorAction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (!json.isJsonObject()) {
            throw new JsonParseException("Action element must be a JSON object");
        }
        JsonObject obj = json.getAsJsonObject();
        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        switch (type) {
            case "puppet_action":
            case "execute_puppet_action":
                ExecutePuppetAction epa = RAW_GSON.fromJson(obj, ExecutePuppetAction.class);
                if (obj.has("params") && obj.get("params").isJsonObject()) {
                    epa.setParams(parseJsonToNbt(obj.getAsJsonObject("params")));
                }
                return epa;
            case "puppet_move_to":
            case "puppet_move":
                return RAW_GSON.fromJson(obj, PuppetMoveToAction.class);
            case "puppet_look_at":
            case "puppet_look":
                return RAW_GSON.fromJson(obj, PuppetLookAtAction.class);
            case "puppet_suppress_ai":
            case "puppet_suppress":
            case "puppet_ai":
                PuppetSuppressAction psa = RAW_GSON.fromJson(obj, PuppetSuppressAction.class);
                if (obj.has("disableAi") && obj.get("disableAi").isJsonPrimitive()) {
                    psa.setSuppressAi(obj.get("disableAi").getAsBoolean());
                } else if (obj.has("noAi") && obj.get("noAi").isJsonPrimitive()) {
                    psa.setSuppressAi(obj.get("noAi").getAsBoolean());
                }
                if (obj.has("suppressActions") && obj.get("suppressActions").isJsonPrimitive()) {
                    psa.setSuppressActions(obj.get("suppressActions").getAsBoolean());
                } else if (obj.has("disableActions") && obj.get("disableActions").isJsonPrimitive()) {
                    psa.setSuppressActions(obj.get("disableActions").getAsBoolean());
                } else if (obj.has("noActions") && obj.get("noActions").isJsonPrimitive()) {
                    psa.setSuppressActions(obj.get("noActions").getAsBoolean());
                }
                return psa;
            case "puppet_stop_action":
            case "puppet_stop":
                return RAW_GSON.fromJson(obj, PuppetStopAction.class);
            case "command":
                return RAW_GSON.fromJson(obj, CommandAction.class);
            case "checkpoint":
                return RAW_GSON.fromJson(obj, CheckpointAction.class);
            case "new_objective":
                return RAW_GSON.fromJson(obj, NewObjectiveAction.class);
            case "end_objective":
                return RAW_GSON.fromJson(obj, EndObjectiveAction.class);
            case "play_music_sequence":
            case "music_sequence":
                return RAW_GSON.fromJson(obj, PlayMusicSequenceAction.class);
            case "wait_until":
                return RAW_GSON.fromJson(obj, WaitUntilAction.class);
            case "dialog":
            case "dialog_end":
            case "dialogs_end":
            case "dialog_sequence":
            case "dialog_finish":
                WaitUntilAction diagAction = RAW_GSON.fromJson(obj, WaitUntilAction.class);
                diagAction.setWaitType("dialog");
                return diagAction;
            case "proximity":
            case "marker":
            case "player_proximity":
            case "area":
                WaitUntilAction proxAction = RAW_GSON.fromJson(obj, WaitUntilAction.class);
                proxAction.setWaitType("proximity");
                return proxAction;
            case "puppet_wait":
            case "wait_puppet":
            case "puppet_idle":
            case "puppet_finish":
            case "puppet_action_end":
                WaitUntilAction puppetWaitAction = RAW_GSON.fromJson(obj, WaitUntilAction.class);
                puppetWaitAction.setWaitType("puppet_action");
                return puppetWaitAction;
            case "delay":
                int ticks = obj.has("ticks") ? obj.get("ticks").getAsInt() : 20;
                return new WaitUntilAction("delay", ticks, "", "");
            case "await_trigger":
                String trig = obj.has("triggerId") ? obj.get("triggerId").getAsString() : "trigger_1";
                return new WaitUntilAction("trigger", 0, trig, "");
            case "fork_sequence":
                return RAW_GSON.fromJson(obj, ForkSequenceAction.class);
            case "run_sequence":
                return RAW_GSON.fromJson(obj, RunSequenceAction.class);
            case "stall_parent":
                return RAW_GSON.fromJson(obj, StallParentAction.class);
            case "resume_parent":
                return RAW_GSON.fromJson(obj, ResumeParentAction.class);
            default:
                throw new JsonParseException("Unknown action type: '" + type + "' in JSON object: " + json);
        }
    }

    private static net.minecraft.nbt.CompoundTag parseJsonToNbt(JsonObject paramsObj) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        for (java.util.Map.Entry<String, JsonElement> entry : paramsObj.entrySet()) {
            String key = entry.getKey();
            JsonElement elem = entry.getValue();
            if (elem.isJsonPrimitive()) {
                JsonPrimitive prim = elem.getAsJsonPrimitive();
                if (prim.isBoolean()) {
                    tag.putBoolean(key, prim.getAsBoolean());
                } else if (prim.isNumber()) {
                    Number num = prim.getAsNumber();
                    if (num instanceof Double || num instanceof Float) {
                        tag.putDouble(key, num.doubleValue());
                    } else {
                        tag.putInt(key, num.intValue());
                    }
                } else if (prim.isString()) {
                    tag.putString(key, prim.getAsString());
                }
            }
        }
        return tag;
    }
}
