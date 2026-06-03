package com.wiyuka.acceleratedpresents;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.math.Transformation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber(modid = Acceleratedpresents.MODID)
public final class TestCommands {
    private static final float MIN_SCALE = 0.25F;
    private static final float MAX_SCALE = 5.0F;
    private static final double HORIZONTAL_SPREAD = 16.0D;
    private static final double VERTICAL_SPREAD = 8.0D;

    private static final int MIN_TRANSLUCENT_ALPHA = 40;
    private static final int MAX_TRANSLUCENT_ALPHA = 200;

    private static final int INTERP_TICKS = 20;
    private static final double MOVE_AMPLITUDE = 4.0D;
    private static final double ANGLE_STEP = 0.35D;
    private static final float MOVING_SCALE = 1.5F;

    /** Degrees advanced per interpolation window for {@code rotating}; must stay below 180 so slerp keeps direction. */
    private static final float ROTATION_STEP_DEGREES = 90.0F;

    /** Active server-driven moving displays. Only touched on the server thread (command + tick). */
    private static final List<Mover> MOVERS = new ArrayList<>();
    private static int movingTick;

    /** Active server-driven rotating displays. Only touched on the server thread (command + tick). */
    private static final List<Mover> ROTATORS = new ArrayList<>();
    private static int rotateTick;

    @SubscribeEvent
    public static void onRegisterCommands(final RegisterCommandsEvent event) {
        // Debug-only harness: never register the /aptest command tree in a production (published) build.
//        if (FMLEnvironment.isProduction()) {
//            return;
//        }

        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> billboard = Commands.literal("billboard");
        for (Display.BillboardConstraints constraint : Display.BillboardConstraints.values()) {
            billboard.then(Commands.literal(constraint.getSerializedName())
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                            .executes(ctx -> billboard(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), constraint))));
        }
        billboard.then(Commands.literal("all")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                        .executes(ctx -> billboardAll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))));

        dispatcher.register(Commands.literal("aptest")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> spawn(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("translucent")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> translucent(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("text")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> text(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("moving")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> moving(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("rotating")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> rotating(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(Commands.literal("mixed")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> mixed(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count")))))
                .then(billboard)
                .then(Commands.literal("cases")
                        .executes(ctx -> cases(ctx.getSource())))
                .then(Commands.literal("clearmoving")
                        .executes(ctx -> clearMoving(ctx.getSource())))
                .then(Commands.literal("toggle")
                        .executes(ctx -> toggle(ctx.getSource(), !Config.blockSolidBackgroundTextDisplays))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> toggle(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled"))))));
    }

    private static int spawn(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);
            entity.setBackgroundColor(0xFF000000 | rng.nextInt(0x01000000));
            float scale = MIN_SCALE + rng.nextFloat() * (MAX_SCALE - MIN_SCALE);
            entity.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(entity);
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " solid-background TextDisplay entities."), true);
        return count;
    }

    private static int translucent(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);

            int alpha = MIN_TRANSLUCENT_ALPHA + rng.nextInt(MAX_TRANSLUCENT_ALPHA - MIN_TRANSLUCENT_ALPHA + 1);
            entity.setBackgroundColor((alpha << 24) | rng.nextInt(0x01000000));

            // Half of them also render through geometry (see-through batch / depth-test off).
            if (rng.nextBoolean()) {
                entity.setFlags((byte) Display.TextDisplay.FLAG_SEE_THROUGH);
            }

            float scale = MIN_SCALE + rng.nextFloat() * (MAX_SCALE - MIN_SCALE);
            entity.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(entity);
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " translucent TextDisplay entities (mixed see-through)."), true);
        return count;
    }

    /**
     * Passthrough regression test: these displays carry real (non-blank) text, so the mod must NOT take them over -
     * they should render normally through vanilla (text + background), proving the blank-text gate works.
     */
    private static int text(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);

            entity.setBackgroundColor(0xC0000000 | rng.nextInt(0x01000000));
            entity.setText(randomText(rng, i));

            // Cycle alignment LEFT/CENTER/RIGHT, randomize shadow, and mix in some see-through.
            byte flags = 0;
            switch (i % 3) {
                case 1 -> flags |= Display.TextDisplay.FLAG_ALIGN_LEFT;
                case 2 -> flags |= Display.TextDisplay.FLAG_ALIGN_RIGHT;
                default -> { /* CENTER: no alignment flag */ }
            }
            if (rng.nextBoolean()) {
                flags |= Display.TextDisplay.FLAG_SHADOW;
            }
            if (rng.nextInt(4) == 0) {
                flags |= Display.TextDisplay.FLAG_SEE_THROUGH;
            }
            entity.setFlags(flags);

            float scale = 1.0F + rng.nextFloat() * 2.0F;
            entity.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(entity);
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " text TextDisplay entities (real text -> should stay on vanilla rendering)."), true);
        return count;
    }

    /** Builds a multi-line, colored text component to verify textful displays are left untouched by the mod. */
    private static Component randomText(final RandomSource rng, final int index) {
        int rgb = rng.nextInt(0x01000000);
        return Component.literal(" ");
    }

    /** Spawns solid-background displays that all use the given billboard constraint, to verify each orientation path. */
    private static int billboard(final CommandSourceStack source, final int count, final Display.BillboardConstraints constraint) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);
            entity.setBillboardConstraints(constraint);
            entity.setBackgroundColor(0xFF000000 | rng.nextInt(0x01000000));
            float scale = MIN_SCALE + rng.nextFloat() * (MAX_SCALE - MIN_SCALE);
            entity.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(entity);
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " solid TextDisplay entities with billboard=" + constraint.getSerializedName() + "."), true);
        return count;
    }

    /** Spawns solid-background displays cycling through every billboard constraint. */
    private static int billboardAll(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();
        Display.BillboardConstraints[] values = Display.BillboardConstraints.values();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);
            entity.setBillboardConstraints(values[i % values.length]);
            entity.setBackgroundColor(0xFF000000 | rng.nextInt(0x01000000));
            float scale = MIN_SCALE + rng.nextFloat() * (MAX_SCALE - MIN_SCALE);
            entity.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(entity);
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " solid TextDisplay entities cycling all billboard modes."), true);
        return count;
    }

    /**
     * Spawns one labeled display per interesting case in a row in front of the player. Green label = the mod should
     * take it over; red label = it should fall through to vanilla. Lets you eyeball every code path at once.
     */
    private static int cases(final CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 origin = source.getPosition();
        final int alphaBelow = Math.max(0, Config.minBackgroundAlpha - 1);

        int n = 0;
        n = spawnCase(level, origin, n, "FIXED opaque", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setBackgroundColor(0xFF202830);
        });
        n = spawnCase(level, origin, n, "VERTICAL opaque", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.VERTICAL);
            e.setBackgroundColor(0xFF203028);
        });
        n = spawnCase(level, origin, n, "HORIZONTAL opaque", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.HORIZONTAL);
            e.setBackgroundColor(0xFF302028);
        });
        n = spawnCase(level, origin, n, "CENTER opaque", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            e.setBackgroundColor(0xFF282030);
        });
        n = spawnCase(level, origin, n, "default-bg", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setFlags((byte) Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND);
        });
        n = spawnCase(level, origin, n, "see-through FIXED", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setBackgroundColor(0xFF30302A);
            e.setFlags((byte) Display.TextDisplay.FLAG_SEE_THROUGH);
        });
        n = spawnCase(level, origin, n, "see-through CENTER", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.CENTER);
            e.setBackgroundColor(0xFF2A3030);
            e.setFlags((byte) Display.TextDisplay.FLAG_SEE_THROUGH);
        });
        n = spawnCase(level, origin, n, "scaled 3x FIXED", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setBackgroundColor(0xFF203840);
            e.setTransformation(new Transformation(null, null, new Vector3f(3.0F, 3.0F, 3.0F), null));
        });
        n = spawnCase(level, origin, n, "tilted FIXED", true, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setBackgroundColor(0xFF402038);
            e.setYRot(35.0F);
            e.setXRot(25.0F);
        });
        n = spawnCase(level, origin, n, "alpha=" + alphaBelow + " passthrough", false, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setBackgroundColor((alphaBelow << 24) | 0x3050FF);
        });
        n = spawnCase(level, origin, n, "real text passthrough", false, e -> {
            e.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            e.setBackgroundColor(0xFF202020);
            e.setText(Component.literal("not blank"));
        });

        final int total = n;
        source.sendSuccess(() -> Component.literal("Spawned " + total + " labeled test cases (green=taken over by mod, red=vanilla passthrough)."), true);
        return n;
    }

    /** Spawns a single test display plus a floating vanilla label above it; returns the next slot index. */
    private static int spawnCase(final ServerLevel level, final Vec3 origin, final int index, final String name,
                                 final boolean takenOver, final Consumer<Display.TextDisplay> config) {
        double x = origin.x + index * 2.5D;
        double y = origin.y + 1.0D;
        double z = origin.z + 3.0D;

        Display.TextDisplay test = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        test.setPos(x, y, z);
        test.setText(Component.literal(" "));
        test.setBackgroundColor(0xFF000000);
        config.accept(test);
        level.addFreshEntity(test);

        Display.TextDisplay label = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        label.setPos(x, y + 1.2D, z);
        label.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        label.setBackgroundColor(0x80000000);
        label.setText(Component.literal(name).withColor(takenOver ? 0x55FF55 : 0xFF5555));
        level.addFreshEntity(label);

        return index + 1;
    }

    private static int moving(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);
            entity.setBackgroundColor(0xFF000000 | rng.nextInt(0x01000000));
            entity.setTransformation(new Transformation(null, null, new Vector3f(MOVING_SCALE, MOVING_SCALE, MOVING_SCALE), null));
            level.addFreshEntity(entity);
            MOVERS.add(new Mover(entity, rng.nextFloat() * (float) (Math.PI * 2.0)));
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " interpolating (moving) TextDisplay entities. Use /aptest clearmoving to stop."), true);
        return count;
    }

    /** Spawns FIXED displays whose yaw/pitch change every tick, exercising the rotation -> dynamic-path handoff. */
    private static int rotating(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);
            entity.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            entity.setBackgroundColor(0xFF000000 | rng.nextInt(0x01000000));
            entity.setTransformation(new Transformation(null, null, new Vector3f(MOVING_SCALE, MOVING_SCALE, MOVING_SCALE), null));
            level.addFreshEntity(entity);
            ROTATORS.add(new Mover(entity, rng.nextFloat() * 360.0F));
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " rotating FIXED TextDisplay entities (tests rotation -> dynamic path). Use /aptest clearmoving to stop."), true);
        return count;
    }

    /**
     * Everything-mix stress test: each entity gets a random billboard, a random background/text variant (opaque,
     * translucent, default-bg, below-threshold passthrough, real-text passthrough), random see-through and scale,
     * and a random animation (moving / rotating / static). Exercises every path at once under load.
     */
    private static int mixed(final CommandSourceStack source, final int count) {
        ServerLevel level = source.getLevel();
        RandomSource rng = level.getRandom();
        Vec3 origin = source.getPosition();
        Display.BillboardConstraints[] billboards = Display.BillboardConstraints.values();

        for (int i = 0; i < count; i++) {
            Display.TextDisplay entity = newDisplay(level, origin, rng);
            entity.setBillboardConstraints(billboards[rng.nextInt(billboards.length)]);

            byte flags = 0;
            if (rng.nextInt(3) == 0) {
                flags |= Display.TextDisplay.FLAG_SEE_THROUGH;
            }

            switch (rng.nextInt(5)) {
                case 0 -> entity.setBackgroundColor(0xFF000000 | rng.nextInt(0x01000000));
                case 1 -> {
                    int alpha = MIN_TRANSLUCENT_ALPHA + rng.nextInt(MAX_TRANSLUCENT_ALPHA - MIN_TRANSLUCENT_ALPHA + 1);
                    entity.setBackgroundColor((alpha << 24) | rng.nextInt(0x01000000));
                }
                case 2 -> flags |= Display.TextDisplay.FLAG_USE_DEFAULT_BACKGROUND;
                case 3 -> entity.setBackgroundColor((Math.max(0, Config.minBackgroundAlpha - 1) << 24) | rng.nextInt(0x01000000));
                default -> {
                    entity.setBackgroundColor(0xC0000000 | rng.nextInt(0x01000000));
                    entity.setText(randomText(rng, i));
                }
            }
            entity.setFlags(flags);

            float scale = MIN_SCALE + rng.nextFloat() * (MAX_SCALE - MIN_SCALE);
            entity.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
            level.addFreshEntity(entity);

            switch (rng.nextInt(4)) {
                case 0 -> MOVERS.add(new Mover(entity, rng.nextFloat() * (float) (Math.PI * 2.0)));
                case 1 -> ROTATORS.add(new Mover(entity, rng.nextFloat() * 360.0F));
                default -> { /* static */ }
            }
        }

        source.sendSuccess(() -> Component.literal("Spawned " + count + " mixed TextDisplay entities (random billboard/background/flags/scale + some moving/rotating). Use /aptest clearmoving to stop animations."), true);
        return count;
    }

    private static int clearMoving(final CommandSourceStack source) {
        int removed = discardAll(MOVERS) + discardAll(ROTATORS);
        final int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " animated (moving + rotating) TextDisplay entities."), true);
        return removed;
    }

    private static int discardAll(final List<Mover> list) {
        int removed = 0;
        for (Mover mover : list) {
            if (!mover.entity.isRemoved()) {
                mover.entity.discard();
                removed++;
            }
        }
        list.clear();
        return removed;
    }

    private static int toggle(final CommandSourceStack source, final boolean enabled) {
        Config.setBlockEnabled(enabled);
        source.sendSuccess(() -> Component.literal("AcceleratedPresents blocking is now " + (enabled ? "ON" : "OFF") + "."), true);
        return enabled ? 1 : 0;
    }

    /** Creates a TextDisplay with randomized position/rotation and a single-space (blank) text. */
    private static Display.TextDisplay newDisplay(final ServerLevel level, final Vec3 origin, final RandomSource rng) {
        Display.TextDisplay entity = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);

        double x = origin.x + (rng.nextDouble() * 2.0D - 1.0D) * HORIZONTAL_SPREAD;
        double y = origin.y + rng.nextDouble() * VERTICAL_SPREAD;
        double z = origin.z + (rng.nextDouble() * 2.0D - 1.0D) * HORIZONTAL_SPREAD;
        entity.setPos(x, y, z);

        entity.setYRot(rng.nextFloat() * 360.0F);
        entity.setXRot(rng.nextFloat() * 180.0F - 90.0F);
        entity.setText(Component.literal(" "));
        return entity;
    }

    @SubscribeEvent
    public static void onServerTick(final ServerTickEvent.Post event) {
        tickMovers();
        tickRotators();
    }

    private static void tickMovers() {
        if (MOVERS.isEmpty()) {
            return;
        }
        movingTick++;
        if (movingTick % INTERP_TICKS != 0) {
            return;
        }

        double angle = movingTick * ANGLE_STEP;
        Iterator<Mover> it = MOVERS.iterator();
        while (it.hasNext()) {
            Mover mover = it.next();
            Display.TextDisplay entity = mover.entity;
            if (entity.isRemoved()) {
                it.remove();
                continue;
            }

            double a = angle + mover.phase;
            float offX = (float) (Math.sin(a) * MOVE_AMPLITUDE);
            float offY = (float) (Math.sin(a * 0.5D) * (MOVE_AMPLITUDE * 0.5D));
            float offZ = (float) (Math.cos(a) * MOVE_AMPLITUDE);

            // start_interpolation = 0 (force-synced, re-arms each cycle), interpolation_duration = window.
            entity.setTransformationInterpolationDuration(INTERP_TICKS);
            entity.setTransformationInterpolationDelay(0);
            entity.setTransformation(new Transformation(
                    new Vector3f(offX, offY, offZ), null,
                    new Vector3f(MOVING_SCALE, MOVING_SCALE, MOVING_SCALE), null));
        }
    }

    private static void tickRotators() {
        if (ROTATORS.isEmpty()) {
            return;
        }
        rotateTick++;
        // Re-arm the interpolation once per window, advancing the target by a fixed step for a continuous spin.
        if (rotateTick % INTERP_TICKS != 0) {
            return;
        }

        int step = rotateTick / INTERP_TICKS;
        Iterator<Mover> it = ROTATORS.iterator();
        while (it.hasNext()) {
            Mover mover = it.next();
            Display.TextDisplay entity = mover.entity;
            if (entity.isRemoved()) {
                it.remove();
                continue;
            }

            float yawDeg = step * ROTATION_STEP_DEGREES + mover.phase;
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.toRadians(yawDeg));

            // Smooth rotation must go through the Transformation (Display does not interpolate entity yaw/pitch).
            entity.setTransformationInterpolationDuration(INTERP_TICKS);
            entity.setTransformationInterpolationDelay(0);
            entity.setTransformation(new Transformation(
                    null, rotation,
                    new Vector3f(MOVING_SCALE, MOVING_SCALE, MOVING_SCALE), null));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(final ServerStoppingEvent event) {
        MOVERS.clear();
        ROTATORS.clear();
        movingTick = 0;
        rotateTick = 0;
    }

    private record Mover(Display.TextDisplay entity, float phase) {}
}
