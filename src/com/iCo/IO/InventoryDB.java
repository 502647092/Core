package com.iCo.IO;

import com.iCo.Constants;
import com.iCo.iConomy;
import com.iCo.util.nbt.ByteTag;
import com.iCo.util.nbt.CompoundTag;
import com.iCo.util.nbt.ListTag;
import com.iCo.util.nbt.NBTInputStream;
import com.iCo.util.nbt.NBTOutputStream;
import com.iCo.util.nbt.ShortTag;
import com.iCo.util.nbt.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Controls inventory for monetary use.
 *
 * @author SpaceManiac
 * @author Nijikokun
 * @author RasTaIARI
 */
public class InventoryDB {

    private File dataDir;

    public InventoryDB() {
        dataDir = new File(iConomy.Server.getWorlds().get(0).getName(), "playerdata");
    }

    public List<String> getAllPlayers() {
        ArrayList<String> result = new ArrayList<String>();

        for (String file : dataDir.list())
            if (file.endsWith(".dat")) {
                UUID uuid = UUID.fromString(file.substring(0, file.length() - 4));
        		result.add(Bukkit.getOfflinePlayer(uuid).getName());
            }

        return result;
    }

    public boolean dataExists(UUID uuid) {
        return new File(dataDir, uuid.toString() + ".dat").exists();
    }

    public void setBalance(String name, double balance) {
    	UUID uuid = iConomy.Server.getOfflinePlayer(name).getUniqueId();
        if (iConomy.Server.getPlayer(name) != null && iConomy.Server.getPlayer(name).isOnline()) {

            ItemStack[] stacks = iConomy.Server.getPlayer(name).getInventory().getContents().clone();
            setBalance(stacks, balance);

            iConomy.Server.getPlayer(name).getInventory().setContents(stacks);
            iConomy.Server.getPlayer(name).updateInventory();
            return;
        }

        if (uuid == null || !dataExists(uuid))
            return;

        ItemStack[] stacks = readInventory(uuid);
        if (stacks != null) {
            setBalance(stacks, balance);
            writeInventory(uuid, stacks);
        }
        
    }

    public double getBalance(String name) {
        if (iConomy.Server.getPlayer(name) != null && iConomy.Server.getPlayer(name).isOnline()) {
            return getBalance(iConomy.Server.getPlayer(name).getInventory().getContents());
        } else {
        	UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
            ItemStack[] stacks = readInventory(uuid);
            if (stacks != null) {
                return getBalance(stacks);
            } else {
                return Constants.Nodes.Balance.getDouble();
            }
        }
    }

    private ItemStack[] readInventory(UUID uuid) {
        try {
            NBTInputStream in = new NBTInputStream(new FileInputStream(new File(dataDir, uuid.toString() + ".dat")));
            CompoundTag tag = (CompoundTag) in.readTag();
            in.close();

            ListTag inventory = (ListTag) tag.getValue().get("Inventory");

            ItemStack[] stacks = new ItemStack[40];
            for (int i = 0; i < inventory.getValue().size(); ++i) {
                CompoundTag item = (CompoundTag) inventory.getValue().get(i);
                byte count = ((ByteTag) item.getValue().get("Count")).getValue();
                byte slot = ((ByteTag) item.getValue().get("Slot")).getValue();
                short damage = ((ShortTag) item.getValue().get("Damage")).getValue();
                short id = ((ShortTag) item.getValue().get("id")).getValue();

                stacks[slot] = new ItemStack(id, count, damage);
            }
            return stacks;
        } catch (IOException ex) {
            iConomy.Server.getLogger().log(Level.WARNING, "[iCo/InvDB] error reading inventory {0}: {1}", new Object[]{uuid.toString(), ex.getMessage()});
            return null;
        }
    }

    private void writeInventory(UUID uuid, ItemStack[] stacks) {
        try {
            NBTInputStream in = new NBTInputStream(new FileInputStream(new File(dataDir, uuid.toString() + ".dat")));
            CompoundTag tag = (CompoundTag) in.readTag();
            in.close();

            ArrayList tagList = new ArrayList<Tag>();

            for (int i = 0; i < stacks.length; ++i) {
                if (stacks[i] == null) continue;

                ByteTag count = new ByteTag("Count", (byte) stacks[i].getAmount());
                ByteTag slot = new ByteTag("Slot", (byte) i);
                ShortTag damage = new ShortTag("Damage", stacks[i].getDurability());
                ShortTag id = new ShortTag("id", (short) stacks[i].getTypeId());

                HashMap<String, Tag> tagMap = new HashMap<String, Tag>();
                tagMap.put("Count", count);
                tagMap.put("Slot", slot);
                tagMap.put("Damage", damage);
                tagMap.put("id", id);

                tagList.add(new CompoundTag("", tagMap));
            }

            ListTag inventory = new ListTag("Inventory", CompoundTag.class, tagList);

            HashMap<String, Tag> tagCompound = new HashMap<String, Tag>(tag.getValue());
            tagCompound.put("Inventory", inventory);
            tag = new CompoundTag("Player", tagCompound);

            NBTOutputStream out = new NBTOutputStream(new FileOutputStream(new File(dataDir, uuid.toString() + ".dat")));
            out.writeTag(tag);
            out.close();
        } catch (IOException ex) {
            iConomy.Server.getLogger().log(Level.WARNING, "[iCo/InvDB] error writing inventory {0}: {1}", new Object[]{uuid.toString(), ex.getMessage()});
        }
    }

    private void setBalance(ItemStack[] contents, double balance) {
        int major = Constants.Nodes.DatabaseMajorItem.getInteger();
        int minor = Constants.Nodes.DatabaseMinorItem.getInteger();

        // Remove all existing items
        for (int i = 0; i < contents.length; ++i) {
            ItemStack item = contents[i];

            if (item != null)
                if (item.getTypeId() == major || item.getTypeId() == minor)
                    contents[i] = null;
        }

        // Re-add balance to inventory
        for (int i = 0; i < contents.length; ++i) {
            if (contents[i] == null) {
                if (balance >= 1) {
                    int add = (int) balance;

                    if (add > Material.getMaterial(major).getMaxStackSize())
                        add = Material.getMaterial(major).getMaxStackSize();

                    contents[i] = new ItemStack(major, add);
                    balance -= add;
                } else if (balance >= 0.01) {
                    int add = (int) (roundTwoDecimals(balance) * 100);

                    if (add > Material.getMaterial(minor).getMaxStackSize())
                        add = Material.getMaterial(minor).getMaxStackSize();

                    contents[i] = new ItemStack(minor, add);
                    balance -= 0.01 * add;
                } else {
                    balance = 0;
                    break;
                }
            }
        }

        // Make sure nothing is left.
        if (balance > 0) {
            throw new RuntimeException("Unable to set balance, inventory is overfull");
        }
    }

    private double getBalance(ItemStack[] contents) {
        double balance = 0;
        int major = Constants.Nodes.DatabaseMajorItem.getInteger();
        int minor = Constants.Nodes.DatabaseMinorItem.getInteger();

        for (ItemStack item : contents)
            if (item != null)
                if (item.getTypeId() == major)
                    balance += item.getAmount();
                else if (item.getTypeId() == minor)
                    balance += 0.01 * item.getAmount();

        return balance;
    }

    private double roundTwoDecimals(double d) {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        return Double.valueOf(twoDForm.format(d));
    }
}