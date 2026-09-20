package net.dandare21.fracturedutils.bossbar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

public class BossHealthBarSavedData extends SavedData {
    public static final String FILE_NAME = "fractured_utils_bossbars";

    private final List<BossHealthBar> bossBars = new ArrayList<>();

    public BossHealthBarSavedData() {
    }

    public static BossHealthBarSavedData load(CompoundTag nbt) {
        BossHealthBarSavedData data = new BossHealthBarSavedData();
        if (nbt.contains("BossBars", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("BossBars", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                try {
                    BossHealthBar bar = BossHealthBar.loadNbt(list.getCompound(i));
                    data.bossBars.add(bar);
                } catch (Exception ignored) {}
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        ListTag list = new ListTag();
        for (BossHealthBar bar : this.bossBars) {
            list.add(bar.saveNbt());
        }
        nbt.put("BossBars", list);
        return nbt;
    }

    public static BossHealthBarSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                BossHealthBarSavedData::load,
                BossHealthBarSavedData::new,
                FILE_NAME
        );
    }

    public List<BossHealthBar> getBossBars() {
        return bossBars;
    }

    public void setBossBars(List<BossHealthBar> bars) {
        this.bossBars.clear();
        this.bossBars.addAll(bars);
        setDirty();
    }
}
