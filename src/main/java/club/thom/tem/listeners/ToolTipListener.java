package club.thom.tem.listeners;

import club.thom.tem.TEM;
import club.thom.tem.backend.requests.RequestsCache;
import club.thom.tem.backend.requests.dupe_lookup.CombinedDupeRequest;
import club.thom.tem.backend.requests.dupe_lookup.CombinedDupeResponse;
import club.thom.tem.backend.requests.hex_for_id.HexAmount;
import club.thom.tem.backend.requests.hex_for_id.HexFromItemIdRequest;
import club.thom.tem.backend.requests.hex_for_id.HexFromItemIdResponse;
import club.thom.tem.util.HexUtil;
import club.thom.tem.misc.KeyBinds;
import club.thom.tem.models.inventory.item.ArmourPieceData;
import club.thom.tem.models.inventory.item.MiscItemData;
import club.thom.tem.models.inventory.item.PetData;
import club.thom.tem.models.messages.ClientMessages;
import club.thom.tem.util.MessageUtil;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.HashMap;
import java.util.List;

public class ToolTipListener {
    TEM tem;
    long lastCopyTime = System.currentTimeMillis();

    public ToolTipListener(TEM parent) {
        this.tem = parent;
    }

    public static final HashMap<String, List<String>> uuidToLore = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onItemToolTipEvent(ItemTooltipEvent event) {
        ItemStack item = event.itemStack;
        NBTTagCompound itemNbt;
        try {
            itemNbt = item.serializeNBT();
        } catch (NullPointerException e) {
            // Possible bugs where items don't have nbt, ignore the item.
            return;
        }
        if (checkDuped(itemNbt)) {
            event.toolTip.add(1, EnumChatFormatting.RED + "DEFINITELY DUPED");
        }
        if (GameSettings.isKeyDown(KeyBinds.checkDuped)) {
            fetchDuped(itemNbt, event.toolTip);
        }
        if (GameSettings.isKeyDown(KeyBinds.copyUuid) && System.currentTimeMillis() - lastCopyTime > 1000) {
            copyUuidToClipboard(itemNbt);
            lastCopyTime = System.currentTimeMillis();
        }

        if (!ArmourPieceData.isValidItem(itemNbt)) {
            // We're only caring about armour on tooltips, to add colour.
            return;
        }
        ArmourPieceData armour = new ArmourPieceData(tem, "inventory", itemNbt);

        HexUtil.Modifier armourTypeModifier = (armour.getItemId().startsWith("LEATHER_") && item.getItemDamage()>0) ?
                new HexUtil(tem.getItems()).getNullModifier(armour.getItemId(), armour.getHexCode(), armour.getCreationTimestamp()) :
                new HexUtil(tem.getItems()).getModifier(armour.getItemId(), armour.getHexCode(), armour.getCreationTimestamp());

        String colourCode = armourTypeModifier.getColourCode();
        int ownerCount = checkArmourOwners(armour);
        String toolTipString = colourCode + armourTypeModifier;

        if (armour.isCustomDyed()) {
            toolTipString = EnumChatFormatting.DARK_GRAY + "DYED";
        }

        if (ownerCount != -1) {
            toolTipString += EnumChatFormatting.DARK_GRAY + " - " + ownerCount;
        }
        addColourToTooltip(event, toolTipString);
        if (GameSettings.isKeyDown(KeyBinds.getArmourRarityKey)) {
            fetchArmourOwners(armour);
        }
    }

    public void addColourToTooltip(ItemTooltipEvent event, String hexWithColour) {
        if (event.toolTip.size() == 0) {
            return;
        }
        boolean foundColour = false;
        for (int i = 0; i < event.toolTip.size(); i++) {
            String existingTooltip = event.toolTip.get(i);
            if (existingTooltip.startsWith("Color: ")) {
                foundColour = true;
                // Color: #123456 (EXOTIC)
                event.toolTip.set(i, existingTooltip +
                        EnumChatFormatting.DARK_GRAY + " (" + hexWithColour + EnumChatFormatting.DARK_GRAY + ")");
                break;
            }
        }
        if (!foundColour) {
            // Sits just underneath the item name.
            event.toolTip.add(1, hexWithColour);
        }
    }

    public int checkArmourOwners(ArmourPieceData armour) {
        HexFromItemIdResponse response = (HexFromItemIdResponse) RequestsCache.getInstance().getIfExists(
                new HexFromItemIdRequest(tem.getConfig(), armour.getItemId()));
        if (response == null) {
            return -1;
        }
        String hexCode = armour.getHexCode();

        if (armour.isCustomDyed()) {
            hexCode = new HexUtil(tem.getItems()).getOriginalHex(armour.getItemId());
        }

        for (HexAmount amountData : response.amounts) {
            if (amountData.hex.equals(hexCode)) {
                return amountData.count;
            }
        }
        return -1;
    }

    public void fetchArmourOwners(ArmourPieceData armour) {
        RequestsCache.getInstance().addToQueue(new HexFromItemIdRequest(tem.getConfig(), armour.getItemId()));
    }

    public void fetchDuped(NBTTagCompound itemNbt, List<String> tooltip) {
        String uuid = itemNbtToUuid(itemNbt);
        if (uuid == null) {
            return;
        }
        uuidToLore.put(uuid, tooltip);
        RequestsCache.getInstance().addToQueue(new CombinedDupeRequest(tem, uuid, true));
    }

    public boolean checkDuped(NBTTagCompound itemNbt) {
        String uuid = itemNbtToUuid(itemNbt);
        if (uuid == null) {
            return false;
        }

        CombinedDupeResponse response = (CombinedDupeResponse) RequestsCache.getInstance().getIfExists(
                new CombinedDupeRequest(tem, uuid, true));
        if (response == null) {
            return false;
        }
        return response.verifiedOwners.size() > 1;
    }

    public void copyUuidToClipboard(NBTTagCompound itemNbt) {
        String uuid = itemNbtToUuid(itemNbt);
        if (uuid == null) {
            return;
        }

        StringSelection uuidSelection = new StringSelection(uuid);
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(uuidSelection, null);
        } catch (IllegalStateException ignored) {
            return;
        }
        MessageUtil.sendMessage(new ChatComponentText(EnumChatFormatting.GREEN + "Copied uuid (" + uuid + ") to clipboard!"));
    }

    public String itemNbtToUuid(NBTTagCompound itemNbt) {
        String uuid;
        if (MiscItemData.isValidItem(itemNbt)) {
            MiscItemData itemData = new MiscItemData(tem, "", itemNbt);
            ClientMessages.InventoryItem item = itemData.toInventoryItem();
            if (!item.hasUuid() || item.getUuid().length() == 0) {
                return null;
            }
            uuid = item.getUuid();

        } else if (PetData.isValidItem(itemNbt)) {
            PetData petData = new PetData("", itemNbt);
            ClientMessages.InventoryItem item = petData.toInventoryItem();
            if (!item.hasUuid() || item.getUuid().length() == 0) {
                return null;
            }
            uuid = item.getUuid();
        } else {
            return null;
        }
        return uuid;
    }

}
