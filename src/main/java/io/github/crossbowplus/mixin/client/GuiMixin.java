package io.github.crossbowplus.mixin.client;

import io.github.crossbowplus.client.AxShulkersArrowRefill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void crossbowPlus$hideAxShulkersScreen(@Nullable Screen newScreen, CallbackInfo callbackInfo) {
		if (AxShulkersArrowRefill.shouldSuppressScreen(Minecraft.getInstance(), newScreen)) {
			callbackInfo.cancel();
		}
	}
}
