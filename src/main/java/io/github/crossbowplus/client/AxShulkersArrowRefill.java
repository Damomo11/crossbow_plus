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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

public final class AxShulkersArrowRefill {
	private static final int OPEN_TIMEOUT_TICKS = 20;
	private static final int EMPTY_CONTENT_WAIT_TICKS = 4;
	private static final int FAILURE_RETRY_TICKS = 100;
	// Player inventory slots in visual order: main inventory rows left to right,
	// top to bottom, then the hotbar.
	private static final int[] VISUAL_ORDER = buildVisualOrder();
	private static State state = State.IDLE;
	private static int nextShulkerVisualIndex;
	private static int openInventorySlot = -1;
	private static int waitTicks;
	private static int retryCooldown;

	private AxShulkersArrowRefill() {
	}

	private static int[] buildVisualOrder() {
		int[] slots = new int[Inventory.INVENTORY_SIZE];
		int index = 0;
		for (int slot = 9; slot < Inventory.INVENTORY_SIZE; slot++) {
			slots[index++] = slot;
		}
		for (int slot = 0; slot < 9; slot++) {
			slots[index++] = slot;
		}
		return slots;
	}

	public static boolean tick(Minecraft minecraft, ItemStack crossbow, boolean enabled, boolean useKeyDown) {
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
			case IDLE -> startIfNeeded(minecraft, player, gameMode, crossbow, triggerActive);
		};
	}

	public static boolean shouldSuppressScreen(Minecraft minecraft, @Nullable Screen newScreen) {
		if ((state != State.WAITING_FOR_CONTAINER && state != State.SEARCHING_CONTAINER)
			|| minecraft.player == null
			|| !(newScreen instanceof AbstractContainerScreen<?> containerScreen)
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
		boolean triggerActive
	) {
		if (!triggerActive) {
			retryCooldown = 0;
			return false;
		}

		if (minecraft.gui.screen() != null
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

		nextShulkerVisualIndex = 0;
		return tryNextShulker(minecraft, player, gameMode);
	}

	private static boolean tryNextShulker(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (minecraft.gui.screen() != null || player.containerMenu != player.inventoryMenu) {
			return true;
		}

		Inventory inventory = player.getInventory();
		while (nextShulkerVisualIndex < VISUAL_ORDER.length) {
			int inventorySlot = VISUAL_ORDER[nextShulkerVisualIndex++];
			if (!inventory.getItem(inventorySlot).is(ItemTags.SHULKER_BOXES)) {
				continue;
			}

			OptionalInt menuSlot = player.inventoryMenu.findSlot(inventory, inventorySlot);
			if (menuSlot.isEmpty()) {
				continue;
			}

			openInventorySlot = inventorySlot;
			waitTicks = 0;
			state = State.WAITING_FOR_CONTAINER;
			gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot.getAsInt(), 1, ContainerInput.PICKUP, player);
			return true;
		}

		// No shulker box held projectiles: fall back to loose firework rockets.
		finish(tryTakeLooseFirework(player, gameMode));
		return true;
	}

	private static boolean waitForContainer(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (getShulkerSlotCount(player.containerMenu, player.getInventory()) == 27) {
			minecraft.gui.setScreen(null);
			state = State.SEARCHING_CONTAINER;
			waitTicks = 0;
			return true;
		}

		if (minecraft.gui.screen() != null) {
			closeOrRollback(player, gameMode);
			finish(false);
		} else if (++waitTicks >= OPEN_TIMEOUT_TICKS) {
			closeOrRollback(player, gameMode);
			finish(false);
		}
		return true;
	}

	private static boolean searchContainer(LocalPlayer player, MultiPlayerGameMode gameMode) {
		AbstractContainerMenu containerMenu = player.containerMenu;
		int shulkerSlotCount = getShulkerSlotCount(containerMenu, player.getInventory());
		if (shulkerSlotCount != 27) {
			if (containerMenu != player.inventoryMenu) {
				player.closeContainer();
			}
			finish(false);
			return true;
		}

		waitTicks++;
		// The first projectile in box slot order decides the type: whichever comes
		// first from left to right, top to bottom.
		int projectileSlot = findProjectileSlot(containerMenu, shulkerSlotCount);
		if (projectileSlot >= 0) {
			boolean movedProjectile = containerMenu.getSlot(projectileSlot).getItem().is(Items.FIREWORK_ROCKET)
				? swapToFreeHand(containerMenu, projectileSlot, player, gameMode)
				: moveSlotToInventory(containerMenu, projectileSlot, player, gameMode);
			player.closeContainer();
			finish(movedProjectile);
			return true;
		}

		// Content arrived but holds no projectile (or stayed empty long enough): move on.
		if (hasAnyShulkerItem(containerMenu, shulkerSlotCount) || waitTicks >= EMPTY_CONTENT_WAIT_TICKS) {
			player.closeContainer();
			state = State.TRYING_NEXT_SHULKER;
			waitTicks = 0;
		}
		return true;
	}

	private static boolean moveSlotToInventory(
		AbstractContainerMenu containerMenu,
		int projectileSlot,
		LocalPlayer player,
		MultiPlayerGameMode gameMode
	) {
		// One whole shulker slot per refill, regardless of the stack size.
		gameMode.handleContainerInput(containerMenu.containerId, projectileSlot, 0, ContainerInput.QUICK_MOVE, player);
		return hasInventoryArrows(player.getInventory());
	}

	private static boolean swapToFreeHand(
		AbstractContainerMenu containerMenu,
		int projectileSlot,
		LocalPlayer player,
		MultiPlayerGameMode gameMode
	) {
		// Vanilla crossbows load fireworks from a hand only, so swap one slot onto the free hand.
		gameMode.handleContainerInput(containerMenu.containerId, projectileSlot, getFreeHandSlot(player), ContainerInput.SWAP, player);
		return isFreeHandHoldingFirework(player);
	}

	private static boolean tryTakeLooseFirework(LocalPlayer player, MultiPlayerGameMode gameMode) {
		Inventory inventory = player.getInventory();
		int swapButton = getFreeHandSlot(player);
		for (int visualIndex = 0; visualIndex < VISUAL_ORDER.length; visualIndex++) {
			int inventorySlot = VISUAL_ORDER[visualIndex];
			if (!inventory.getItem(inventorySlot).is(Items.FIREWORK_ROCKET)) {
				continue;
			}

			OptionalInt menuSlot = player.inventoryMenu.findSlot(inventory, inventorySlot);
			if (menuSlot.isEmpty()) {
				continue;
			}

			gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot.getAsInt(), swapButton, ContainerInput.SWAP, player);
			return isFreeHandHoldingFirework(player);
		}
		return false;
	}

	private static int getFreeHandSlot(LocalPlayer player) {
		// The firing crossbow is in the main hand unless it is only in the offhand.
		if (player.getMainHandItem().getItem() instanceof CrossbowItem) {
			return Inventory.SLOT_OFFHAND;
		}
		return player.getInventory().getSelectedSlot();
	}

	private static boolean isFreeHandHoldingFirework(LocalPlayer player) {
		ItemStack mainHandItem = player.getMainHandItem();
		if (mainHandItem.getItem() instanceof CrossbowItem) {
			return player.getOffhandItem().is(Items.FIREWORK_ROCKET);
		}
		return mainHandItem.is(Items.FIREWORK_ROCKET);
	}

	private static int findProjectileSlot(AbstractContainerMenu menu, int shulkerSlotCount) {
		for (int slot = 0; slot < shulkerSlotCount; slot++) {
			ItemStack stack = menu.getSlot(slot).getItem();
			if (stack.is(ItemTags.ARROWS) || stack.is(Items.FIREWORK_ROCKET)) {
				return slot;
			}
		}
		return -1;
	}

	private static boolean hasAnyShulkerItem(AbstractContainerMenu menu, int shulkerSlotCount) {
		for (int slot = 0; slot < shulkerSlotCount; slot++) {
			if (menu.getSlot(slot).hasItem()) {
				return true;
			}
		}
		return false;
	}

	private static int getShulkerSlotCount(AbstractContainerMenu menu, Inventory inventory) {
		if (menu == null || menu == inventory.player.inventoryMenu || menu.slots.isEmpty()) {
			return 0;
		}

		int containerSize = menu.getSlot(0).container.getContainerSize();
		if (containerSize != 27 || menu.slots.size() < containerSize + Inventory.INVENTORY_SIZE) {
			return 0;
		}

		Object shulkerContainer = menu.getSlot(0).container;
		for (int slot = 0; slot < containerSize; slot++) {
			if (menu.getSlot(slot).container != shulkerContainer) {
				return 0;
			}
		}

		for (int slot = containerSize; slot < menu.slots.size(); slot++) {
			if (menu.getSlot(slot).container == inventory) {
				return containerSize;
			}
		}
		return 0;
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
		if (openInventorySlot < 0 || player.containerMenu != player.inventoryMenu) {
			return;
		}

		OptionalInt menuSlot = player.inventoryMenu.findSlot(player.getInventory(), openInventorySlot);
		if (menuSlot.isPresent()
			&& player.inventoryMenu.getCarried().is(ItemTags.SHULKER_BOXES)
			&& player.inventoryMenu.getSlot(menuSlot.getAsInt()).getItem().isEmpty()) {
			gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot.getAsInt(), 1, ContainerInput.PICKUP, player);
		}
	}

	private static void abort(LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (state == State.WAITING_FOR_CONTAINER || state == State.SEARCHING_CONTAINER) {
			closeOrRollback(player, gameMode);
		}
		resetState();
	}

	private static void closeOrRollback(LocalPlayer player, MultiPlayerGameMode gameMode) {
		if (player.containerMenu != player.inventoryMenu) {
			player.closeContainer();
		} else {
			rollbackInventoryClick(player, gameMode);
		}
	}

	private static void finish(boolean success) {
		state = State.IDLE;
		nextShulkerVisualIndex = 0;
		openInventorySlot = -1;
		waitTicks = 0;
		retryCooldown = success ? 0 : FAILURE_RETRY_TICKS;
	}

	private static void resetState() {
		state = State.IDLE;
		nextShulkerVisualIndex = 0;
		openInventorySlot = -1;
		waitTicks = 0;
		retryCooldown = 0;
	}

	private enum State {
		IDLE,
		WAITING_FOR_CONTAINER,
		SEARCHING_CONTAINER,
		TRYING_NEXT_SHULKER
	}
}
