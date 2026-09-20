package net.dandare21.fracturedutils.orchestrator.action;

import net.dandare21.fracturedutils.orchestrator.SequenceInstance;
import net.dandare21.fracturedutils.sound.sequence.MusicSequenceManager;
import net.minecraft.server.MinecraftServer;

public class WaitForMusicSequenceAction implements OrchestratorAction {
    private String type = "wait_for_music_sequence";
    private String sequenceFile;

    private transient boolean hasSeenActive = false;
    private transient int graceTicks = 0;

    public WaitForMusicSequenceAction() {
        this("");
    }

    public WaitForMusicSequenceAction(String sequenceFile) {
        this.type = "wait_for_music_sequence";
        this.sequenceFile = sequenceFile != null ? sequenceFile : "";
    }

    public String getSequenceFile() {
        return sequenceFile != null ? sequenceFile : "";
    }

    public void setSequenceFile(String sequenceFile) {
        this.sequenceFile = sequenceFile != null ? sequenceFile : "";
    }

    @Override
    public ActionResult execute(SequenceInstance instance, MinecraftServer server) {
        if (server == null) {
            return ActionResult.SUCCESS;
        }

        MusicSequenceManager mgr = MusicSequenceManager.getInstance();
        boolean hasTarget = (sequenceFile != null && !sequenceFile.trim().isEmpty());
        boolean isActive = hasTarget ? mgr.isSequenceActive(sequenceFile) : mgr.hasActiveSequence();

        if (isActive) {
            hasSeenActive = true;
            return ActionResult.BLOCK;
        }

        if (hasSeenActive) {
            // Sequence was actively executing and has now completed at its OUT marker
            hasSeenActive = false;
            graceTicks = 0;
            return ActionResult.SUCCESS;
        }

        // Check if the sequence finished recently at its OUT marker within the last 1500ms
        if (mgr.hasReachedOutMarker(sequenceFile, 1500L)) {
            graceTicks = 0;
            return ActionResult.SUCCESS;
        }

        // If not seen active yet, allow a short grace window (10 ticks = 0.5s) in case it was triggered in this pass
        graceTicks++;
        if (graceTicks < 10) {
            return ActionResult.BLOCK;
        }

        graceTicks = 0;
        return ActionResult.SUCCESS;
    }

    @Override
    public String getType() {
        return "wait_for_music_sequence";
    }

    @Override
    public OrchestratorAction copy() {
        return new WaitForMusicSequenceAction(this.sequenceFile);
    }
}
