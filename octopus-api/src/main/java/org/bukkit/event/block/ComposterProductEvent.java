package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ComposterProductEvent extends BlockEvent {
    private static final HandlerList handlers = new HandlerList();
    private ItemStack itemStack;

    public ComposterProductEvent(@NotNull final Block block, @NotNull final ItemStack itemStack) {
        super(block);
        this.itemStack = itemStack;
    }

    @NotNull
    public ItemStack getItem() {
        return itemStack;
    }

    public void setItem(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
