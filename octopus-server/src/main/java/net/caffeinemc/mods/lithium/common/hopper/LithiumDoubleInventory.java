/*
 * This file is part of Lithium
 *
 * Lithium is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Lithium is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Lithium. If not, see <https://www.gnu.org/licenses/>.
 */

package net.caffeinemc.mods.lithium.common.hopper;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.caffeinemc.mods.lithium.api.inventory.LithiumInventory;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeEmitter;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_change_tracking.InventoryChangeListener;
import net.caffeinemc.mods.lithium.common.block.entity.inventory_comparator_tracking.ComparatorTracking;
import net.minecraft.core.NonNullList;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class LithiumDoubleInventory extends CompoundContainer implements LithiumInventory, InventoryChangeEmitter, InventoryChangeListener {

    private final LithiumInventory first;
    private final LithiumInventory second;

    private LithiumStackList doubleStackList;

    ReferenceOpenHashSet<InventoryChangeListener> inventoryChangeListeners = null;
    ReferenceOpenHashSet<InventoryChangeListener> inventoryHandlingTypeListeners = null;

    /**
     * This method returns the same LithiumDoubleInventory instance for equal (same children in same order)
     * doubleInventory parameters until {@link #lithium$emitRemoved()} is called. After that a new LithiumDoubleInventory object
     * may be in use.
     *
     * @param doubleInventory A double inventory
     * @return The only non-removed LithiumDoubleInventory instance for the double inventory. Null if not compatible
     */
    public static LithiumDoubleInventory getLithiumInventory(CompoundContainer doubleInventory) {
        Container vanillaFirst = doubleInventory.container1;
        Container vanillaSecond = doubleInventory.container2;
        if (vanillaFirst != vanillaSecond && vanillaFirst instanceof LithiumInventory first && vanillaFirst instanceof InventoryChangeEmitter
            && vanillaSecond instanceof LithiumInventory second && vanillaSecond instanceof InventoryChangeEmitter) {
            LithiumDoubleStackList doubleStackList = LithiumDoubleStackList.getOrCreate(
                first, second,
                InventoryHelper.getLithiumStackList(first),
                InventoryHelper.getLithiumStackList(second)
            );
            return doubleStackList.doubleInventory;
        }
        return null;
    }

    LithiumDoubleInventory(LithiumInventory first, LithiumInventory second) {
        super(first, second);
        this.first = first;
        this.second = second;
    }

    @Override
    public void lithium$emitContentModified() {
        ReferenceOpenHashSet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null) {
            for (InventoryChangeListener inventoryChangeListener : inventoryChangeListeners) {
                inventoryChangeListener.lithium$handleInventoryContentModified(this);
            }
            inventoryChangeListeners.clear();
        }
    }

    @Override
    public void lithium$emitStackListReplaced() {
        this.invalidateChangeListening();
    }

    @Override
    public void lithium$emitRemoved() {
        this.invalidateChangeListening();
    }

    private void invalidateChangeListening() {
        //Invalidate listeners to this inventory
        ReferenceOpenHashSet<InventoryChangeListener> listeners = this.inventoryHandlingTypeListeners;
        this.inventoryHandlingTypeListeners = null; //Prevent concurrent modification
        if (listeners != null && !listeners.isEmpty()) {
            listeners.forEach(listener -> listener.lithium$handleInventoryRemoved(this));
            listeners.clear();
            this.inventoryHandlingTypeListeners = listeners;
        }

        if (this.inventoryChangeListeners != null) {
            this.inventoryChangeListeners.clear();
        }

        //Invalidate own listening
        ((InventoryChangeEmitter) this.first).lithium$stopForwardingMajorInventoryChanges(this);
        ((InventoryChangeEmitter) this.second).lithium$stopForwardingMajorInventoryChanges(this);

        LithiumStackList lithiumStackList = this.doubleStackList;
        if (lithiumStackList != null) {
            lithiumStackList.removeInventoryModificationCallback(this);
        }
    }

    @Override
    public void lithium$emitFirstComparatorAdded() {
        ReferenceOpenHashSet<InventoryChangeListener> inventoryChangeListeners = this.inventoryChangeListeners;
        if (inventoryChangeListeners != null && !inventoryChangeListeners.isEmpty()) {
            inventoryChangeListeners.removeIf(inventoryChangeListener -> inventoryChangeListener.lithium$handleComparatorAdded(this));
        }
    }

    @Override
    public void lithium$forwardContentChangeOnce(InventoryChangeListener inventoryChangeListener, LithiumStackList stackList) {
        if (this.inventoryChangeListeners == null) {
            this.inventoryChangeListeners = new ReferenceOpenHashSet<>(1);
        }
        if (this.inventoryChangeListeners.isEmpty()) {
            ((InventoryChangeEmitter) this.first).lithium$forwardContentChangeOnce(this, InventoryHelper.getLithiumStackList(this.first));
            ((InventoryChangeEmitter) this.second).lithium$forwardContentChangeOnce(this, InventoryHelper.getLithiumStackList(this.second));
        }
        this.inventoryChangeListeners.add(inventoryChangeListener);
    }

    @Override
    public void lithium$forwardMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners == null) {
            this.inventoryHandlingTypeListeners = new ReferenceOpenHashSet<>(1);
        }
        if (this.inventoryHandlingTypeListeners.isEmpty()) {
            ((InventoryChangeEmitter) this.first).lithium$forwardMajorInventoryChanges(this);
            ((InventoryChangeEmitter) this.second).lithium$forwardMajorInventoryChanges(this);
        }
        this.inventoryHandlingTypeListeners.add(inventoryChangeListener);
    }

    @Override
    public void lithium$stopForwardingMajorInventoryChanges(InventoryChangeListener inventoryChangeListener) {
        if (this.inventoryHandlingTypeListeners != null) {
            this.inventoryHandlingTypeListeners.remove(inventoryChangeListener);
            if (this.inventoryHandlingTypeListeners.isEmpty()) {
                ((InventoryChangeEmitter) this.first).lithium$stopForwardingMajorInventoryChanges(this);
                ((InventoryChangeEmitter) this.second).lithium$stopForwardingMajorInventoryChanges(this);
            }
        }
    }

    @Override
    public NonNullList<ItemStack> getInventoryLithium() {
        return this.doubleStackList;
    }

    @Override
    public void setInventoryLithium(NonNullList<ItemStack> inventory) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void lithium$handleInventoryContentModified(Container inventory) {
        this.lithium$emitContentModified();
    }

    @Override
    public void lithium$handleInventoryRemoved(Container inventory) {
        this.lithium$emitRemoved();
    }

    @Override
    public boolean lithium$handleComparatorAdded(Container inventory) {
        this.lithium$emitFirstComparatorAdded();
        return this.inventoryChangeListeners.isEmpty();
    }

    public boolean lithium$hasAnyComparatorNearby() {
        return ComparatorTracking.hasAnyComparatorNearby(this.first) || ComparatorTracking.hasAnyComparatorNearby(this.second);
    }

    public void setDoubleStackList(LithiumStackList doubleStackList) {
        if (this.doubleStackList != null) {
            throw new IllegalStateException("DoubleStackList already set!");
        }
        this.doubleStackList = doubleStackList;
    }
}
