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

package net.caffeinemc.mods.lithium.api.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Gives the hopper optimization direct access to an inventory's backing stack list.
 *
 * @author 2No2Name
 */
public interface LithiumInventory extends Container {

    /**
     * Getter for the inventory stack list of this inventory.
     *
     * @return inventory stack list
     */
    NonNullList<ItemStack> getInventoryLithium();

    /**
     * Setter for the inventory stack list of this inventory.
     * Used to replace the stack list with Lithium's custom stack list.
     *
     * @param inventory inventory stack list
     */
    void setInventoryLithium(NonNullList<ItemStack> inventory);

    /**
     * Generates the loot like a hopper access would do in vanilla.
     * <p>
     */
    default void generateLootLithium() {
        if (this instanceof RandomizableContainer) {
            ((RandomizableContainer) this).unpackLootTable(null);
        }
        if (this instanceof ContainerEntity) {
            ((ContainerEntity) this).unpackChestVehicleLootTable(null);
        }
    }
}
