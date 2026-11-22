package io.github.comfortforged.tidesofwar.core;

/** Flags the mixins set, so the self-test can prove they were applied. */
public final class ShipHooks {
	public static volatile boolean collideMixinRan;

	private ShipHooks() {}
}
