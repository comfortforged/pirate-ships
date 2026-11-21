package io.github.comfortforged.tidesofwar.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;

import java.util.stream.Stream;

/**
 * All Minecraft-version differences live here, so the ship code itself has no version conditionals.
 * (`//? if` comments are Stonecutter conditionals.)
 */
final class Compat {
	private Compat() {}

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
