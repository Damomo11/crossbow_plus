package io.github.crossbowplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CrossbowItem;
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

	@Inject(method = "tick", at = @At("RETURN"))
	private void crossbowPlus$repeatCrossbowUse(CallbackInfo callbackInfo) {
		if (this.player == null
			|| this.screen != null
			|| !this.options.keyUse.isDown()) {
			return;
		}

		if (this.player.getMainHandItem().getItem() instanceof CrossbowItem
			|| this.player.getOffhandItem().getItem() instanceof CrossbowItem) {
			this.startUseItem();
		}
	}
}
