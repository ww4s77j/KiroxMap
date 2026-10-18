package com.kiroxtm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The current screen and {@code setScreen} moved from {@link Minecraft} to {@code Minecraft.gui} in
 * 26.2. These helpers keep that difference out of the map screens.
 */
public final class Screens {
	private Screens() {
	}

	/** The screen that is currently open, or {@code null} when the player is in game. */
	public static Screen current(Minecraft client) {
//? if >=26.2 {
		return client.gui.screen();
//?}
//? if <26.2 {
		/*return client.screen;
*///?}
	}

	/** Opens {@code screen}, or closes the current one when it is {@code null}. */
	public static void open(Minecraft client, Screen screen) {
//? if >=26.2 {
		client.gui.setScreen(screen);
//?}
//? if <26.2 {
		/*client.setScreen(screen);
*///?}
	}
}
