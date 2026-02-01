package org.bukkit.event.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerMessagePreSendEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private boolean cancelled = false;
    private Component component;

    public PlayerMessagePreSendEvent(@NotNull Player player, @NotNull Component component) {
        super(player, !Bukkit.isPrimaryThread());
        this.component = component;
    }

    @Deprecated
    public PlayerMessagePreSendEvent(@NotNull Player player, @NotNull BaseComponent[] content) {
        super(player, !Bukkit.isPrimaryThread());
        this.component = GsonComponentSerializer.gson().deserialize(ComponentSerializer.toString(content));
    }

    @NotNull
    public Component getComponent() {
        return component;
    }

    public void setComponent(@NotNull Component component) {
        this.component = component;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
