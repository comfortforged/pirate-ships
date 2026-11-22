package io.github.comfortforged.tidesofwar.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Collection;

/** The /tidesofwar command (and the old /pirateships alias). */
public final class ShipCommands {
	private ShipCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		String[] typeIds = Arrays.stream(ShipType.values()).map(ShipType::id).toArray(String[]::new);
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("tidesofwar")
				.then(Commands.literal("give")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("type", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(typeIds, b))
										.executes(c -> {
											ShipType type = ShipType.byId(StringArgumentType.getString(c, "type"));
											if (type == null) {
												c.getSource().sendFailure(Component.literal("Unknown ship type. Use: " + String.join(", ", typeIds)));
												return 0;
											}
											Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
											for (ServerPlayer p : targets) ShipInteractions.giveOrDrop(p, ShipItems.create(type));
											c.getSource().sendSuccess(() -> Component.literal("Gave a " + type.displayName + " to "
													+ targets.size() + " player(s)"), true);
											return targets.size();
										}))))
				.then(Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							Config.load();
							c.getSource().sendSuccess(() -> Component.literal("Tides of War config reloaded."), true);
							return 1;
						}))
				.then(Commands.literal("list")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(c -> {
							ShipManager m = ShipManager.get();
							if (m == null || m.ships().isEmpty()) {
								c.getSource().sendSuccess(() -> Component.literal("No ships are afloat."), false);
								return 0;
							}
							for (Ship s : m.ships()) {
								c.getSource().sendSuccess(() -> Component.literal(String.format("%s owned by %s at %d %d %d (%s)",
										s.type.displayName, s.ownerName, (int) s.x, (int) s.y, (int) s.z, s.dimension)), false);
							}
							return m.ships().size();
						}))
				.then(Commands.literal("recall")
						.executes(c -> {
							ServerPlayer p = c.getSource().getPlayerOrException();
							ShipManager m = ShipManager.get();
							Ship best = null;
							double bestDist = Double.MAX_VALUE;
							for (Ship s : m.ships()) {
								if (!p.getUUID().equals(s.owner)) continue;
								double d = s.dimension.equals(ShipManager.dimensionId(p.level())) ? p.distanceToSqr(s.x, s.y, s.z) : 1e18;
								if (d < bestDist) {
									bestDist = d;
									best = s;
								}
							}
							if (best == null) {
								c.getSource().sendFailure(Component.literal("You don't have any ships afloat."));
								return 0;
							}
							String name = best.type.displayName;
							ShipInteractions.giveOrDrop(p, m.packUp(best));
							c.getSource().sendSuccess(() -> Component.literal("Your " + name + " has been returned to you, cargo and all."), false);
							return 1;
						})));
		// Old name still works.
		dispatcher.register(Commands.literal("pirateships").redirect(root));
	}
}
