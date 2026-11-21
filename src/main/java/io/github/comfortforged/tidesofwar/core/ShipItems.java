package io.github.comfortforged.tidesofwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** The ship item: a vanilla dark oak boat tagged with which ship it summons. */
public final class ShipItems {
	public static final String TYPE_KEY = "tidesofwar_type";
	/** Item data key used before the rename; ship items from older versions still work. */
	public static final String LEGACY_TYPE_KEY = "pirateships_type";

	private ShipItems() {}

	public static ItemStack create(ShipType type) {
		ItemStack stack = new ItemStack(Items.DARK_OAK_BOAT);
		CompoundTag tag = new CompoundTag();
		tag.putString(TYPE_KEY, type.id());
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
				Component.literal(type.displayName).withStyle(ChatFormatting.GOLD));
		stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(net.minecraft.core.component.DataComponents.RARITY, Rarity.RARE);
		stack.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Right-click on open water to launch").withStyle(ChatFormatting.GRAY),
				Component.literal(type.cargoSlots() + " cargo slots, " + type.passengerSeats + " passenger seats")
						.withStyle(ChatFormatting.DARK_GRAY))));
		return stack;
	}

	/** Returns the ship type this item summons, or null if it isn't a ship item. */
	public static ShipType typeOf(ItemStack stack) {
		if (stack.isEmpty()) return null;
		CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		String id = tag.getStringOr(TYPE_KEY, "");
		if (id.isEmpty()) id = tag.getStringOr(LEGACY_TYPE_KEY, "");
		return id.isEmpty() ? null : ShipType.byId(id);
	}

	public static void storeCargo(ItemStack stack, List<ItemStack> cargo) {
		boolean any = cargo.stream().anyMatch(s -> !s.isEmpty());
		if (any) {
			stack.set(net.minecraft.core.component.DataComponents.CONTAINER, ItemContainerContents.fromItems(cargo));
		}
	}

	public static List<ItemStack> readCargo(ItemStack stack) {
		ItemContainerContents c = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
		List<ItemStack> out = new ArrayList<>();
		if (c != null) c.itemCopies().forEach(out::add);
		return out;
	}
}
