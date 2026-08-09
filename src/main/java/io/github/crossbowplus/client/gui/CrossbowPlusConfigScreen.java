package io.github.crossbowplus.client.gui;

import io.github.crossbowplus.config.CrossbowPlusConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public final class CrossbowPlusConfigScreen extends Screen {
	private static final Component TITLE = Component.translatable("crossbow_plus.config.title");
	private static final int OPTION_WIDTH = 260;
	private final Screen parent;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

	public CrossbowPlusConfigScreen(Screen parent) {
		super(TITLE);
		this.parent = parent;
	}

	@Override
	protected void init() {
		this.layout.addTitleHeader(this.title, this.font);
		LinearLayout options = this.layout.addToContents(LinearLayout.vertical().spacing(8));
		options.addChild(
			CycleButton.onOffBuilder(CrossbowPlusConfig.isEnabled())
				.create(0, 0, OPTION_WIDTH, 20, Component.translatable("crossbow_plus.config.enabled"), (button, value) -> {
					CrossbowPlusConfig.setEnabled(value);
					CrossbowPlusConfig.save();
				})
		);
		options.addChild(
			CycleButton.onOffBuilder(CrossbowPlusConfig.isAxShulkersArrowRefillEnabled())
				.create(0, 0, OPTION_WIDTH, 20, Component.translatable("crossbow_plus.config.ax_shulkers_arrow_refill"), (button, value) -> {
					CrossbowPlusConfig.setAxShulkersArrowRefillEnabled(value);
					CrossbowPlusConfig.save();
				})
		);
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
		this.layout.visitWidgets(this::addRenderableWidget);
		this.repositionElements();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		CrossbowPlusConfig.save();
		this.minecraft.setScreen(this.parent);
	}
}
