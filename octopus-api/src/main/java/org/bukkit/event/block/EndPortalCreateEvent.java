package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class EndPortalCreateEvent extends BlockEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Block portalBlock;
    private boolean cancel = false;

    public EndPortalCreateEvent(@NotNull final Block block, @NotNull final Block portalBlock) {
        super(block);
        this.portalBlock = portalBlock;
    }

    @NotNull
    public Block getPortalBlock() {
        return portalBlock;
    }

    @Override
    public boolean isCancelled() {
        return cancel;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancel = cancel;
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
