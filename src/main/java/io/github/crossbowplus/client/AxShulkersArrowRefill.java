package io.github.crossbowplus.client;

import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class AxShulkersArrowRefill {
	private static final int OPEN_TIMEOUT_TICKS = 20;
	private static final int EMPTY_CONTENT_WAIT_TICKS = 4;
	private static final int FAILURE_RETRY_TICKS = 100;
	private static State state = State.IDLE;
	private static int nextInventorySlot;
	private static int clickedInventorySlot = -1;
	private static int waitTicks;
	private static int retryCooldown;
	private static boolean singleArrowMode;

	private AxShulkersArrowRefill() {
	}

	public static boolean tick(
		Minecraft minecraft,
		ItemStack crossbow,
		boolean enabled,
		boolean takeSingleArrow,
		boolean useKeyDown
	) {
		LocalPlayer player = minecraft.player;
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		if (player == null || gameMode == null) {
			resetState();
			return false;
		}

		boolean triggerActive = enabled && useKeyDown && !crossbow.isEmpty();
		if (state != State.IDLE && !triggerActive) {
			abort(player, gameMode);
			return true;
		}

		return switch (state) {
			case WAITING_FOR_CONTAINER -> waitForContainer(minecraft, player, gameMode);
			case SEARCHING_CONTAINER -> searchContainer(player, gameMode);
			case TRYING_NEXT_SHULKER -> tryNextShulker(minecraft, player, gameMode);
			case IDLE -> startIfNeeded(minecraft, player, gameMode, crossbow, triggerActive, takeSingleArrow);
		};
	}

	public static boolean shouldSuppressScreen(Minecraft minecraft, @Nullable Screen newScreen) {
		if (state != State.WAITING_FOR_CONTAINER
			|| minecraft.player == null
			|| !(newScreen instanceof AbstractContainerScreen<?> containerScreen)
			|| !(containerScreen.getMenu() instanceof ChestMenu chestMenu)
			|| chestMenu.getRowCount() != 3
			|| minecraft.player.containerMenu != containerScreen.getMenu()) {
			return false;
		}

		state = State.SEARCHING_CONTAINER;
		waitTicks = 0;
		return true;
	}

	private static boolean startIfNeeded(
		Minecraft minecraft,
		LocalPlayer player,
		MultiPlayerGameMode gameMode,
		ItemStack crossbow,
		boolean triggerActive,
		boolean takeSingleArrow
	) {
		if (!triggerActive) {
			retryCooldown = 0;
			return false;
		}

		if (minecraft.screen != null
			|| player.containerMenu != player.inventoryMenu
			|| CrossbowItem.isCharged(crossbow)
			|| !player.getProjectile(crossbow).isEmpty()) {
			retryCooldown = 0;
			return false;
		}

		if (retryCooldown > 0) {
			retryCooldown--;
			return false;
		}

		nextInventorySlot = 0;
		singleArrowMode = takeSingleArrow;
		return tryNextShulker(minecraft, player, gameMode);
	}

	private static boolean tryNextShulker(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (minecraft.screen != null || player.containerMenu != player.inventoryMenu) {
			return true;
		}

		Inventory inventory = player.getInventory();
		for (int inventorySlot = nextInventorySlot; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
			if (!inventory.getItem(inventorySlot).is(ItemTags.SHULKER_BOXES)) {
				continue;
			}

			OptionalInt menuSlot = player.inventoryMenu.findSlot(inventory, inventorySlot);
			if (menuSlot.isEmpty()) {
				continue;
			}

			clickedInventorySlot = inventorySlot;
			nextInventorySlot = inventorySlot + 1;
			waitTicks = 0;
			state = State.WAITING_FOR_CONTAINER;
			gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot.getAsInt(), 1, ContainerInput.PICKUP, player);
			return true;
		}

		finish(false);
		return true;
	}

	private static boolean waitForContainer(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (player.containerMenu instanceof ChestMenu chestMenu && chestMenu.getRowCount() == 3) {
			minecraft.setScreen(null);
			state = State.SEARCHING_CONTAINER;
			waitTicks = 0;
			return true;
		}

		if (minecraft.screen != null || ++waitTicks >= OPEN_TIMEOUT_TICKS) {
			rollbackInventoryClick(player, gameMode);
			finish(false);
		}
		return true;
	}

	private static boolean searchContainer(LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (!(player.containerMenu instanceof ChestMenu chestMenu) || chestMenu.getRowCount() != 3) {
			finish(false);
			return true;
		}

		waitTicks++;
		int arrowSlot = findArrowSlot(chestMenu);
		if (arrowSlot >= 0) {
			boolean movedArrows = singleArrowMode
				? moveSingleArrow(chestMenu, arrowSlot, player, gameMode)
				: moveArrowStack(chestMenu, arrowSlot, player, gameMode);
			boolean resumeUse = singleArrowMode && movedArrows;
			player.closeContainer();
			finish(movedArrows);
			return !resumeUse;
		}

		if (hasAnyShulkerItem(chestMenu) || waitTicks >= EMPTY_CONTENT_WAIT_TICKS) {
			player.closeContainer();
			state = State.TRYING_NEXT_SHULKER;
			waitTicks = 0;
		}
		return true;
	}

	private static boolean moveArrowStack(
		ChestMenu chestMenu,
		int arrowSlot,
		LocalPlayer player,
		MultiPlayerGameMode gameMode
	) {
		gameMode.handleContainerInput(chestMenu.containerId, arrowSlot, 0, ContainerInput.QUICK_MOVE, player);
		return hasInventoryArrows(player.getInventory());
	}

	private static boolean moveSingleArrow(
		ChestMenu chestMenu,
		int arrowSlot,
		LocalPlayer player,
		MultiPlayerGameMode gameMode
	) {
		Inventory inventory = player.getInventory();
		OptionalInt emptyInventorySlot = findEmptyInventorySlot(inventory);
		if (emptyInventorySlot.isEmpty() || !chestMenu.getCarried().isEmpty()) {
			return false;
		}

		OptionalInt emptyMenuSlot = chestMenu.findSlot(inventory, emptyInventorySlot.getAsInt());
		if (emptyMenuSlot.isEmpty()) {
			return false;
		}

		gameMode.handleContainerInput(chestMenu.containerId, arrowSlot, 0, ContainerInput.PICKUP, player);
		gameMode.handleContainerInput(chestMenu.containerId, emptyMenuSlot.getAsInt(), 1, ContainerInput.PICKUP, player);
		gameMode.handleContainerInput(chestMenu.containerId, arrowSlot, 0, ContainerInput.PICKUP, player);

		ItemStack movedArrow = inventory.getItem(emptyInventorySlot.getAsInt());
		return movedArrow.is(ItemTags.ARROWS) && movedArrow.getCount() == 1 && chestMenu.getCarried().isEmpty();
	}

	private static OptionalInt findEmptyInventorySlot(Inventory inventory) {
		for (int inventorySlot = 0; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
			if (inventory.getItem(inventorySlot).isEmpty()) {
				return OptionalInt.of(inventorySlot);
			}
		}
		return OptionalInt.empty();
	}

	private static int findArrowSlot(AbstractContainerMenu menu) {
		for (int slot = 0; slot < 27; slot++) {
			if (menu.getSlot(slot).getItem().is(ItemTags.ARROWS)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean hasAnyShulkerItem(AbstractContainerMenu menu) {
		for (int slot = 0; slot < 27; slot++) {
			if (menu.getSlot(slot).hasItem()) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasInventoryArrows(Inventory inventory) {
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).is(ItemTags.ARROWS)) {
				return true;
			}
		}
		return false;
	}

	private static void rollbackInventoryClick(LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (clickedInventorySlot < 0 || player.containerMenu != player.inventoryMenu) {
			return;
		}

		OptionalInt menuSlot = player.inventoryMenu.findSlot(player.getInventory(), clickedInventorySlot);
		if (menuSlot.isPresent()
			&& player.inventoryMenu.getCarried().is(ItemTags.SHULKER_BOXES)
			&& player.inventoryMenu.getSlot(menuSlot.getAsInt()).getItem().isEmpty()) {
			gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot.getAsInt(), 1, ContainerInput.PICKUP, player);
		}
	}

	private static void abort(LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (state == State.WAITING_FOR_CONTAINER) {
			rollbackInventoryClick(player, gameMode);
		} else if (state == State.SEARCHING_CONTAINER && player.containerMenu != player.inventoryMenu) {
			player.closeContainer();
		}
		resetState();
	}

	private static void finish(boolean success) {
		state = State.IDLE;
		nextInventorySlot = 0;
		clickedInventorySlot = -1;
		waitTicks = 0;
		retryCooldown = success ? 0 : FAILURE_RETRY_TICKS;
		singleArrowMode = false;
	}

	private static void resetState() {
		state = State.IDLE;
		nextInventorySlot = 0;
		clickedInventorySlot = -1;
		waitTicks = 0;
		retryCooldown = 0;
		singleArrowMode = false;
	}

	private enum State {
		IDLE,
		WAITING_FOR_CONTAINER,
		SEARCHING_CONTAINER,
		TRYING_NEXT_SHULKER
	}
}
