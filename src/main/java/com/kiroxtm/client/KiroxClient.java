package com.kiroxtm.client;

import java.nio.file.Path;

import com.kiroxtm.screen.AtlasScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client behaviour shared by every loader: each one just calls {@link #tick} once per client tick. */
public final class KiroxClient {
	private KiroxClient() {
	}

	public static void tick(Minecraft client) {
		while (KiroxKeys.OPEN_MAP.consumeClick()) {
			if (Screens.current(client) == null && client.player != null) {
				try {
					Screens.open(client, new AtlasScreen());
				} catch (Throwable t) {
					// Includes LinkageError (e.g. ExceptionInInitializerError): report instead of crashing the game.
					Path file = ErrorLog.report("Kirox Map failed to open", t);
					Screens.open(client, null);
					client.player.sendSystemMessage(Component.literal("[Kirox Map] Failed to open the map: " + t
							+ (file != null ? " (details in " + file.getFileName() + ")" : "")));
				}
			}
		}
	}
}
