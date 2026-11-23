package net.dandare21.fracturedutils.client;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientCommandHelper {

    public static boolean isValidCommand(Minecraft minecraft, String command) {
        if (command == null || command.isBlank()) return true;
        String cmdToParse = command.trim();
        if (cmdToParse.startsWith("/")) {
            cmdToParse = cmdToParse.substring(1);
        }
        int spaceIdx = cmdToParse.indexOf(' ');
        String rootName = spaceIdx > 0 ? cmdToParse.substring(0, spaceIdx) : cmdToParse;

        try {
            if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
                CommandDispatcher<SharedSuggestionProvider> dispatcher = minecraft.getConnection().getCommands();
                if (dispatcher != null && dispatcher.getRoot() != null) {
                    return dispatcher.getRoot().getChild(rootName) != null;
                }
            }
        } catch (Exception e) {
            return true;
        }
        return true;
    }
}

