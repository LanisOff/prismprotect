package dev.lanis.prismprotect.handler;

public final class BlockChangeContext {

    public static final ThreadLocal<Boolean> IN_FIRE_TICK =
            ThreadLocal.withInitial(() -> false);

    public static final ThreadLocal<Boolean> EXPLOSION_HANDLED =
            ThreadLocal.withInitial(() -> false);

    private BlockChangeContext() {}
}
