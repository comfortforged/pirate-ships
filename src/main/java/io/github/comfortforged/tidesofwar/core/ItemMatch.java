package io.github.comfortforged.tidesofwar.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Matches items by config id: "minecraft:gunpowder" for one item, "#minecraft:planks" for an item tag. */
final class ItemMatch {
	private ItemMatch() {}

	static boolean matches(ItemStack stack, String id) {
		if (stack.isEmpty() || id == null) return false;
		if (id.startsWith("#")) {
			Identifier tag = Identifier.tryParse(id.substring(1));
			return tag != null && stack.is(TagKey.<Item>create(Registries.ITEM, tag));
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id);
	}

	static int count(Container c, String id) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack st = c.getItem(i);
			if (matches(st, id)) n += st.getCount();
		}
		return n;
	}

	/** Removes up to {@code amount} matching items; returns how many were removed. */
	static int take(Container c, String id, int amount) {
		int left = amount;
		for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
			ItemStack st = c.getItem(i);
			if (!matches(st, id)) continue;
			int n = Math.min(left, st.getCount());
			st.shrink(n);
			if (st.isEmpty()) c.setItem(i, ItemStack.EMPTY);
			left -= n;
		}
		c.setChanged();
		return amount - left;
	}
}
