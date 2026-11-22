package io.github.comfortforged.tidesofwar.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Upgrade items: a vanilla item tagged with which part and tier it installs. Crafted, then used on a ship's tiller. */
public final class UpgradeItems {
	public static final String PART_KEY = "tidesofwar_upgrade";
	public static final String TIER_KEY = "tidesofwar_tier";

	private UpgradeItems() {}

	public static ItemStack create(Upgrades.Part part, int tier) {
		ItemStack stack = new ItemStack(part.baseItem());
		CompoundTag tag = new CompoundTag();
		tag.putString(PART_KEY, part.id);
		tag.putInt(TIER_KEY, tier);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
				Component.literal(part.nameOfTier(tier)).withStyle(ChatFormatting.AQUA));
		stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		stack.set(net.minecraft.core.component.DataComponents.RARITY, tier >= 3 || part == Upgrades.Part.FLIGHT ? Rarity.EPIC : Rarity.RARE);
		List<Component> lore = new ArrayList<>();
		lore.add(Component.literal("Right-click a ship's tiller to install").withStyle(ChatFormatting.GRAY));
		if (tier > 1) {
			lore.add(Component.literal("Needs " + part.nameOfTier(tier - 1) + " installed first").withStyle(ChatFormatting.DARK_GRAY));
		}
		stack.set(net.minecraft.core.component.DataComponents.LORE, new ItemLore(lore));
		return stack;
	}

	public static Upgrades.Part partOf(ItemStack stack) {
		if (stack.isEmpty()) return null;
		CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		String id = data.copyTag().getStringOr(PART_KEY, "");
		return id.isEmpty() ? null : Upgrades.Part.byId(id);
	}

	public static int tierOf(ItemStack stack) {
		CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) return 0;
		return data.copyTag().getIntOr(TIER_KEY, 0);
	}
}
