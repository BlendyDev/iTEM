package club.thom.tem.commands.subcommands;

import club.thom.tem.TEM;
import club.thom.tem.storage.TEMConfig;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SetKeyCommand implements SubCommand {
    private static final Logger logger = LogManager.getLogger(SetKeyCommand.class);

    @Override
    public String getName() {
        return "setkey";
    }

    @Override
    public String getDescription() {
        return "Set HYPIXEL API key.";
    }

    @Override
    public void execute(ICommandSender sender, String[] args) {
        try {
            TEMConfig.setHypixelKey(args[1]).join();
        } catch (InterruptedException e) {
            logger.error("Error setting hypixel key from command", e);
            return;
        }
        TEMConfig.enableExotics = true;
        TEM.sendMessage(new ChatComponentText(EnumChatFormatting.GREEN + "API key set to " + args[1] + "!"));
    }
}