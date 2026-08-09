package io.github.crossbowplus.mixin.client;

import io.github.crossbowplus.client.AxShulkersArrowRefill;
import io.github.crossbowplus.config.CrossbowPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
	@Shadow
	@Final
	public Options options;

	@Shadow
	public @Nullable LocalPlayer player;

	@Shadow
	public @Nullable Screen screen;

	@Shadow
	private void startUseItem() {
	}

	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void crossbowPlus$hideAxShulkersScreen(@Nullable Screen newScreen, CallbackInfo callbackInfo) {
		if (AxShulkersArrowRefill.shouldSuppressScreen((Minecraft)(Object)this, newScreen)) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void crossbowPlus$repeatCrossbowUse(CallbackInfo callbackInfo) {
		ItemStack crossbow = this.player != null ? getHeldCrossbow(this.player) : ItemStack.EMPTY;
		boolean useKeyDown = this.options.keyUse.isDown();
		boolean refillBusy = AxShulkersArrowRefill.tick(
			(Minecraft)(Object)this,
			crossbow,
			CrossbowPlusConfig.isEnabled() && CrossbowPlusConfig.isAxShulkersArrowRefillEnabled(),
			CrossbowPlusConfig.isAxShulkersSingleArrowEnabled(),
			useKeyDown
		);
		if (this.player == null || refillBusy) {
			return;
		}

		if (CrossbowPlusConfig.isEnabled() && this.screen == null && useKeyDown && !crossbow.isEmpty()) {
			this.startUseItem();
		}
	}

	private static ItemStack getHeldCrossbow(LocalPlayer player) {
		ItemStack mainHandItem = player.getMainHandItem();
		if (mainHandItem.getItem() instanceof CrossbowItem) {
			return mainHandItem;
		}

		ItemStack offhandItem = player.getOffhandItem();
		return offhandItem.getItem() instanceof CrossbowItem ? offhandItem : ItemStack.EMPTY;
	}
}
