package io.github.comfortforged.tidesofwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which connected players have the Tides of War mod on their own PC.
 * The mod tells the server by running the hidden command {@code /tidesofwar hello <version>} when it joins a world,
 * so no loader-specific networking is needed. Players without it are asked once to install it when they board a ship.
 */
public final class ClientMods {
	private static final Map<UUID, String> WITH_MOD = new ConcurrentHashMap<>();
	private static final Set<UUID> ASKED = ConcurrentHashMap.newKeySet();

	private ClientMods() {}

	public static void mark(UUID player, String version) {
		WITH_MOD.put(player, version);
	}

	public static boolean has(UUID player) {
		return WITH_MOD.containsKey(player);
	}

	public static String version(UUID player) {
		return WITH_MOD.get(player);
	}

	public static void forget(UUID player) {
		WITH_MOD.remove(player);
		ASKED.remove(player);
	}

	/** Called when a player boards a ship: once per session, tells players without the mod how to walk on decks. */
	public static void askIfMissing(ServerPlayer player) {
		if (has(player.getUUID()) || !ASKED.add(player.getUUID())) return;
		player.sendSystemMessage(Component.literal(
				"Tip: install the Tides of War mod on your PC (the CurseForge profile from the server) to walk around on ship decks.")
				.withStyle(ChatFormatting.GOLD));
	}
}
