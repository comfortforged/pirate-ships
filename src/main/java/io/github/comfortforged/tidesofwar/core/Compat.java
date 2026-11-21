package io.github.comfortforged.tidesofwar.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * All Minecraft-version differences live here, so the ship code itself has no version conditionals.
 * (`//? if` comments are Stonecutter conditionals.)
 */
final class Compat {
	private Compat() {}

	// Display and Interaction setters are private in vanilla. Fabric API widens them, NeoForge/Forge don't.
	// Minecraft 26.x runs with official (Mojang) names on every loader, so plain reflection works everywhere.
	private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

	private static void invoke(Class<?> owner, Object target, String name, Class<?> type, Object arg) {
		try {
			Method m = METHODS.computeIfAbsent(owner.getName() + "#" + name, k -> {
				try {
					Method found = owner.getDeclaredMethod(name, type);
					found.setAccessible(true);
					return found;
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException("Missing method " + owner.getName() + "." + name, e);
				}
			});
			m.invoke(target, arg);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Couldn't call " + owner.getName() + "." + name, e);
		}
	}

	static void setBlockState(Display.BlockDisplay d, net.minecraft.world.level.block.state.BlockState state) {
		invoke(Display.BlockDisplay.class, d, "setBlockState", net.minecraft.world.level.block.state.BlockState.class, state);
	}

	static void setTransformation(Display d, com.mojang.math.Transformation t) {
		invoke(Display.class, d, "setTransformation", com.mojang.math.Transformation.class, t);
	}

	static void setPosRotInterpolationDuration(Display d, int ticks) {
		invoke(Display.class, d, "setPosRotInterpolationDuration", int.class, ticks);
	}

	static void setViewRange(Display d, float range) {
		invoke(Display.class, d, "setViewRange", float.class, range);
	}

	static void setWidth(Interaction i, float w) {
		invoke(Interaction.class, i, "setWidth", float.class, w);
	}

	static void setHeight(Interaction i, float h) {
		invoke(Interaction.class, i, "setHeight", float.class, h);
	}

	static void setResponse(Interaction i, boolean response) {
		invoke(Interaction.class, i, "setResponse", boolean.class, response);
	}

	static EntityType<Display.BlockDisplay> blockDisplay() {
		//? if >=26.2 {
		return net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY;
		//?} else {
		/*return EntityType.BLOCK_DISPLAY;
		*///?}
	}

	static EntityType<Display.ItemDisplay> itemDisplay() {
		//? if >=26.2 {
		return net.minecraft.world.entity.EntityTypes.ITEM_DISPLAY;
		//?} else {
		/*return EntityType.ITEM_DISPLAY;
		*///?}
	}

	static EntityType<Interaction> interaction() {
		//? if >=26.2 {
		return net.minecraft.world.entity.EntityTypes.INTERACTION;
		//?} else {
		/*return EntityType.INTERACTION;
		*///?}
	}

	static Block whiteWool() {
		//? if >=26.2 {
		return net.minecraft.world.level.block.Blocks.WOOL.white();
		//?} else {
		/*return net.minecraft.world.level.block.Blocks.WHITE_WOOL;
		*///?}
	}

	static Block blackWool() {
		//? if >=26.2 {
		return net.minecraft.world.level.block.Blocks.WOOL.black();
		//?} else {
		/*return net.minecraft.world.level.block.Blocks.BLACK_WOOL;
		*///?}
	}

	static Block whiteConcrete() {
		//? if >=26.2 {
		return net.minecraft.world.level.block.Blocks.CONCRETE.white();
		//?} else {
		/*return net.minecraft.world.level.block.Blocks.WHITE_CONCRETE;
		*///?}
	}

	/** Copies of every stack in a container component. */
	static Stream<ItemStack> itemCopies(ItemContainerContents c) {
		//? if >=26.3 {
		return c.itemCopies();
		//?} else {
		/*return c.allItemsCopyStream();
		*///?}
	}

	/** Puts the item in the player's inventory, or drops it if there is no room. */
	static void giveBack(ServerPlayer player, ItemStack item) {
		//? if >=26.3 {
		player.getInventory().placeItemBackInInventory(item, net.minecraft.util.Prediction.SERVER_ONLY);
		//?} else {
		/*player.getInventory().placeItemBackInInventory(item);
		*///?}
	}
}
