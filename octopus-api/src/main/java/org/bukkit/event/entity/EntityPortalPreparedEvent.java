package org.bukkit.event.entity;

import org.bukkit.PortalType;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class EntityPortalPreparedEvent extends EntityEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final PortalType portalType;
    private boolean cancel = false;

    public EntityPortalPreparedEvent(final Entity entity, final PortalType portalType) {
        super(entity);
        this.portalType = portalType;
    }

    @NotNull
    public PortalType getPortalType() {
        return portalType;
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
