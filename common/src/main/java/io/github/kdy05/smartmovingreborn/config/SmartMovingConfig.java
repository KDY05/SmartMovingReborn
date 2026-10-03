package io.github.kdy05.smartmovingreborn.config;

import java.util.function.BooleanSupplier;

/**
 * Movement rules shared by client and server, ported from the original {@code SmartMovingConfig}.
 * <p>
 * Key names and comments follow the original. Defaults are the original's Easy preset, the only one ported.
 * The original's presets differed only in exhaustion and hunger, which Easy left off, so those options are
 * missing along with the other parts out of scope: in-game speed manipulation, preset cycling and per-user
 * presets, lava swimming, and other-mod compatibility.
 */
public abstract class SmartMovingConfig extends PropertySet {
    public static final String CLIMB_FREE = "free";
    public static final String CLIMB_SMART = "smart";
    public static final String CLIMB_SIMPLE = "simple";
    public static final String CLIMB_STANDARD = "standard";

    /** Runtime master switch toggled in-game (F9). Not persisted. */
    public boolean enabled = true;

    // Global speed

    public final FloatProperty speedFactor = section("Global Speed",
            "Below you find the options to manipulate the global speed applied to all speeds.",
            factor("move.speed.factor", "Global player speed factor (>= 0)"));

    // Climbing

    public final StringProperty climbBase = section("Climbing",
            "Below you find all free and ladder climbing options except those for ceiling climbing.",
            choice("move.climb.base", CLIMB_FREE, new String[]{CLIMB_FREE, CLIMB_SMART, CLIMB_SIMPLE, CLIMB_STANDARD},
                    "To manipulate the ladder and vine climbing mode (possible values are \"free\", \"smart\", \"simple\" and \"standard\")"));
    public final BooleanProperty climbFree = on("move.climb.free", "To switch on/off free climbing");
    public final BooleanProperty climbFreeBaseLadder = off("move.climb.free.base.ladder",
            "To switch on/off remaining base climbing behavior on ladders while free climbing is enabled for ladders (also see \"move.climb.base\")");
    public final BooleanProperty climbFreeBaseVine = off("move.climb.free.base.vine",
            "To switch on/off remaining base climbing behavior on vines while free climbing is enabled for vines (also see \"move.climb.base\")");
    private final BooleanSupplier isFreeBaseClimb = () -> climbBase.is(CLIMB_FREE) && climbFree.get();
    public final FloatProperty climbFreeUpSpeedFactor = factor("move.climb.free.up.speed.factor",
            "Climbing up speed factor relative to default climbing up speed (>= 0)");
    public final FloatProperty climbFreeDownSpeedFactor = factor("move.climb.free.down.speed.factor",
            "Climbing down speed factor relative to default climbing down speed (>= 0)");
    public final FloatProperty climbFreeHorizontalSpeedFactor = factor("move.climb.free.horizontal.speed.factor",
            "Climbing horizontal speed factor relative to default climbing horizontal speed (>= 0)");
    public final FloatProperty climbFreeOrthogonalAngle = positive("move.climb.free.direction.orthogonal.angle", 90,
            "Climbing N,S,E,W grabbing angle in degrees (>= 90, <= 180)").range(90, 180);
    public final FloatProperty climbFreeDiagonalAngle = positive("move.climb.free.direction.diagonal.angle", 80,
            "Climbing NW,SW,SE,NE grabbing angle in degrees (>= 45, <= 180)").range(45, 180);
    public final BooleanProperty climbFreeLadderAuto = on("move.climb.free.ladder.auto",
            "Whether the \"grab\" button will automatically be triggered while being on ladders and looking in the right direction")
            .dependsOn(isFreeBaseClimb);
    public final BooleanProperty climbFreeVineAuto = on("move.climb.free.vine.auto",
            "Whether the \"grab\" button will automatically be triggered while being on standard climbable vines and looking in the right direction")
            .dependsOn(isFreeBaseClimb);
    public final FloatProperty climbFreeLadderOneUpSpeedFactor = factor("move.climb.free.ladder.one.up.speed.factor", 1.0153f,
            "Additional speed factor when climbing straight up on one ladder block (>= 0)");
    public final FloatProperty climbFreeLadderTwoUpSpeedFactor = increasing("move.climb.free.ladder.two.up.speed.factor", 1.43f,
            "Additional speed factor when climbing straight up on two ladder blocks (>= 1)");
    public final BooleanProperty climbFreeFence = on("move.climb.free.fence", "Climbing over fences");
    public final FloatProperty climbFallDamageStartDistance = positive("move.climb.fall.damage.start.distance", 2,
            "Distance in blocks to fall before suffering fall damage when starting to climb (>= 1, <= 3)").range(1, 3);
    public final FloatProperty climbFallDamageFactor = increasing("move.climb.fall.damage.factor", 2,
            "Damage factor applied to the remaining distance (>= 1)");
    public final FloatProperty climbFallMaximumDistance = positive("move.climb.fall.maximum.distance", 3,
            "Distance in blocks to fall to block all climbing attempts (>= \"move.climb.fall.damage.start.distance\")")
            .min(climbFallDamageStartDistance::get);

    // Ceiling climbing

    public final BooleanProperty ceilingClimb = section("Ceiling climbing", "Below you find all ceiling climbing options",
            on("move.climb.ceiling", "To switch on/off climbing along ceilings"));
    public final FloatProperty ceilingClimbSpeedFactor = factor("move.climb.ceiling.speed.factor", 0.2f,
            "Speed factor while climbing along ceilings (>= 0, relative to default movement speed)");
    public final StringListProperty ceilingClimbBlocks = list("move.climb.ceiling.configuration",
            new String[]{"minecraft:iron_bars", "#minecraft:trapdoors[half=bottom,open=false]"},
            "To define which blocks are ceiling climbable (syntax: block id or #tag, optionally followed by [state=value,...]; separator: ',')");

    // Swimming and diving

    public final BooleanProperty swim = section("Swimming", "Below you find all swimming options",
            on("move.swim", "To switch on/off swimming"));
    public final FloatProperty swimSpeedFactor = factor("move.swim.speed.factor",
            "Speed factor while swimming (>= 0, relative to default movement speed)");
    public final BooleanProperty swimDownOnSneak = on("move.swim.down.sneak",
            "To switch on/off diving down instead of swimming slow on sneaking while swimming");
    public final FloatProperty swimParticlePeriodFactor = factor("move.swim.particle.period.factor",
            "Swim particle spawning period factor (>= 0)");

    public final BooleanProperty dive = section("Diving", "Below you find all diving options",
            on("move.dive", "To switch on/off diving"));
    public final FloatProperty diveSpeedFactor = factor("move.dive.speed.factor",
            "Speed factor while diving (>= 0, relative to default movement speed)");
    public final BooleanProperty diveDownOnSneak = on("move.dive.down.sneak",
            "To switch on/off diving down instead of diving slow on sneaking while diving");

    // Running (vanilla sprint) and sprinting (Smart Moving's generic sprint)

    public final BooleanProperty run = section("Standard sprinting",
            "Below you find the options for standard vanilla Minecraft sprinting (sometimes referred as \"running\" here)",
            on("move.run", "To switch on/off standard sprinting"));
    public final FloatProperty runFactor = factor("move.run.factor", 1.3f, "Standard sprinting factor (>= 1.1)").min(1.1f);

    public final BooleanProperty sprint = section("Generic sprinting",
            "Below you find the options for Smart Moving's generic sprinting available for many different smart movings plus standard walking",
            on("move.sprint", "To switch on/off generic sprinting"));
    public final FloatProperty sprintFactor = factor("move.sprint.factor", 1.5f,
            "Generic sprinting factor (>= 1.1 AND >= 'move.run.factor' + 0.1 if relevant)")
            .min(() -> run.get() ? runFactor.get() + 0.1f : 1.1f);

    // Sneaking, crawling, sliding

    public final BooleanProperty sneak = section("Generic sneaking",
            "Below you find the options for Smart Moving's generic sneaking available for many different smart movings. These options also apply to standard sneaking!",
            on("move.sneak", "To switch on/off standard sneaking"));
    public final FloatProperty sneakFactor = decreasing("move.sneak.factor", 0.3f,
            "Speed factor while sneaking (>= 0, <= 1, relative to default movement speed)");
    public final BooleanProperty sneakNameTag = off("move.sneak.name", "Whether to display a name tag above other standard sneaking players");

    public final BooleanProperty crawl = section("Crawling", "Below you find all crawling options.",
            on("move.crawl", "To switch on/off crawling"));
    public final FloatProperty crawlFactor = decreasing("move.crawl.factor", 0.15f,
            "Speed factor while crawling (>= 0, <= 1, relative to default movement speed)");
    public final BooleanProperty crawlNameTag = off("move.crawl.name", "Whether to display a name tag above other crawling players");
    public final BooleanProperty crawlOverEdge = on("move.crawl.edge", "Whether to allow crawling over edges");

    public final BooleanProperty slide = section("Sliding", "Below you find all sliding options.",
            on("move.slide", "To switch on/off sliding"));
    public final FloatProperty slideControlAngle = factor("move.slide.control.angle",
            "Sliding control movement factor (>= 0, in degrees per tick)");
    public final FloatProperty slideGlideFactor = factor("move.slide.glide.factor", "Slipperiness factor while sliding (>= 0)");
    public final FloatProperty slideSpeedStopFactor = factor("move.slide.speed.stop.factor",
            "Sliding to crawling transition speed factor (>= 0)");
    public final FloatProperty slideParticlePeriodFactor = factor("move.slide.particle.period.factor", 0.5f,
            "Sliding particle spawning period factor (>= 0)");

    // Flying and falling

    public final BooleanProperty fly = section("Smart flying", "Below you find all options for Smart Moving's own flying mode.",
            on("move.fly", "To switch on/off smart flying"));
    public final FloatProperty flySpeedFactor = factor("move.fly.speed.factor", "To manipulate smart flying speed (>= 0)");

    public final BooleanProperty levitateSmall = section("Standard flying",
            "Below you find the options for standard vanilla Minecraft flying (sometimes referred as \"levitating\" here)",
            on("move.levitate.small", "To switch on/off standard flying small size"));
    public final BooleanProperty levitateAnimation = on("move.levitate.animation", "To switch on/off standard flying animation");

    public final FloatProperty fallDistanceMinimum = section("Falling", "Below you find the options for Smart Moving's falling",
            positive("move.fall.distance.minimum", 3,
                    "Minimum fall distance for stopping ground based moves like crawling or sliding (>= 0)"));
    public final BooleanProperty fallAnimation = on("move.fall.animation", "To switch on/off smart falling animation");
    public final FloatProperty fallAnimationDistanceMinimum = positive("move.fall.animation.distance.minimum", fallDistanceMinimum::get,
            "Minimum fall distance for the smart falling animation (>= \"move.fall.distance.minimum\")")
            .min(fallDistanceMinimum::get);

    // Jumping

    public final BooleanProperty jump = section("Jumping",
            "Below you find the options for all Smart Moving's different jump types. These options also apply to standard jumping!",
            on("move.jump", "To switch on/off jumping"));
    public final FloatProperty jumpControlFactor = decreasing("move.jump.control.factor",
            "Jumping control movement factor (>= 0, <= 1, relative to default air movement speed)");
    public final FloatProperty jumpHorizontalFactor = increasing("move.jump.horizontal.factor",
            "Horizontal jumping factor relative to actual horizontal movement (>= 1)");
    public final FloatProperty jumpVerticalFactor = factor("move.jump.vertical.factor",
            "Vertical jumping factor relative to default jump height (>= 0)");

    public final BooleanProperty standJump = on("move.jump.stand",
            "To switch on/off jumping while standing (Relevant only if \"move.jump\" is true)").dependsOn(jump::get);
    public final FloatProperty standJumpVerticalFactor = factor("move.jump.stand.vertical.factor",
            "Vertical stand jumping factor relative to default jump height (>= 0)");

    public final BooleanProperty sneakJump = on("move.jump.sneak",
            "To switch on/off jumping while sneaking (Relevant only if neither \"move.sneak\" nor \"move.jump\" are false)")
            .dependsOn(sneak::get, jump::get);
    public final FloatProperty sneakJumpHorizontalFactor = increasing("move.jump.sneak.horizontal.factor",
            "Horizontal sneak jumping factor relative to actual horizontal movement (>= 1)");
    public final FloatProperty sneakJumpVerticalFactor = factor("move.jump.sneak.vertical.factor",
            "Vertical sneak jumping factor relative to default jump height (>= 0)");

    public final BooleanProperty walkJump = on("move.jump.walk",
            "To switch on/off jumping while walking (Relevant only if \"move.jump\" is true)").dependsOn(jump::get);
    public final FloatProperty walkJumpHorizontalFactor = increasing("move.jump.walk.horizontal.factor",
            "Horizontal walk jumping factor relative to actual horizontal movement (>= 1)");
    public final FloatProperty walkJumpVerticalFactor = factor("move.jump.walk.vertical.factor",
            "Vertical walk jumping factor relative to default jump height (>= 0)");

    public final BooleanProperty runJump = on("move.jump.run",
            "To switch on/off jumping while running (Relevant only if neither \"move.run\" nor \"move.jump\" are false)")
            .dependsOn(run::get, jump::get);
    public final FloatProperty runJumpHorizontalFactor = increasing("move.jump.run.horizontal.factor", 2,
            "Horizontal run jumping factor relative to actual horizontal movement (>= 1)");
    public final FloatProperty runJumpVerticalFactor = factor("move.jump.run.vertical.factor",
            "Vertical run jumping factor relative to default jump height (>= 0)");

    public final BooleanProperty sprintJump = on("move.jump.sprint",
            "To switch on/off jumping while sprinting (Relevant only if neither \"move.sprint\" nor \"move.jump\" are false)")
            .dependsOn(sprint::get, jump::get);
    public final FloatProperty sprintJumpHorizontalFactor = increasing("move.jump.sprint.horizontal.factor", 2,
            "Horizontal sprint jumping factor relative to actual horizontal movement (>= 1)");
    public final FloatProperty sprintJumpVerticalFactor = factor("move.jump.sprint.vertical.factor",
            "Vertical sprint jumping factor relative to default jump height (>= 0)");

    public final BooleanProperty jumpCharge = section("Charged jumping",
            "Below you find all charged jump specific options.",
            on("move.jump.charge", "Relevant only if \"move.jump\" is not false")).dependsOn(jump::get);
    public final FloatProperty jumpChargeMaximum = positive("move.jump.charge.maximum", 20,
            "Maximum jump charge (counts up one per tick) (>= 0)");
    public final FloatProperty jumpChargeFactor = increasing("move.jump.charge.factor", 1.3f,
            "Jump speed factor when completely charged (>= 1)");
    public final BooleanProperty jumpChargeCancelOnSneakRelease = off("move.jump.charge.sneak.release.cancel",
            "To switch between charged jump and charge cancel on sneak button release while jump charging");

    public final BooleanProperty headJump = section("Head jumping",
            "Below you find all head jump and fall specific options.",
            on("move.jump.head.charge", "Relevant only if \"move.jump\" is not false")).dependsOn(jump::get);
    public final FloatProperty headJumpControlFactor = decreasing("move.jump.head.control.factor", 0.2f,
            "Head jump control movement factor (>= 0, <= 1, relative to default air movement speed)");
    public final FloatProperty headJumpChargeMaximum = positive("move.jump.head.charge.maximum", 10,
            "Maximum head jump charge (counts up one per tick) (>= 0)");
    public final FloatProperty headFallDamageStartDistance = positive("move.fall.head.damage.start.distance", 2,
            "Distance in blocks to fall head ahead before suffering fall damage (>= 1, <= 3)").range(1, 3);
    public final FloatProperty headFallDamageFactor = increasing("move.fall.head.damage.factor", 2,
            "Damage factor applied to the remaining distance when impacting head ahead (>= 1)");

    public final BooleanProperty angleJumpSide = section("Side and Back jumping",
            "Below you find all side and back jump specific options.",
            on("move.jump.angle.side", "To switch on/off side jumping"));
    public final BooleanProperty angleJumpBack = on("move.jump.angle.back", "To switch on/off back jumping");
    public final FloatProperty angleJumpHorizontalFactor = factor("move.jump.angle.horizontal.factor", 0.3f,
            "Horizontal jump speed factor for side and back jumps (>= 0)");
    public final FloatProperty angleJumpVerticalFactor = factor("move.jump.angle.vertical.factor", 0.2f,
            "Vertical jump speed factor for side and back jumps (>= 0)");

    public final BooleanProperty climbUpJump = section("Climb jumping",
            "Below you find all climb up jump specific options.",
            on("move.jump.climb.up", "To switch on/off jumping up while climbing"));
    public final FloatProperty climbUpJumpVerticalFactor = decreasing("move.jump.climb.up.vertical.factor",
            "Vertical jump speed factor for jumping while climbing (>= 0, <= 1)");
    public final FloatProperty climbUpJumpHandsOnlyVerticalFactor = decreasing("move.jump.climb.up.hands.only.vertical.factor", 0.8f,
            "Additional vertical jump speed factor for jumping while climbing with hands only (>= 0, <= 1)");

    public final BooleanProperty climbBackUpJump = section("Climb back jumping",
            "Below you find all climb back jump specific options.",
            on("move.jump.climb.back.up", "To switch on/off jumping back while climbing"));
    public final FloatProperty climbBackUpJumpVerticalFactor = decreasing("move.jump.climb.back.up.vertical.factor", 0.2f,
            "Vertical jump speed factor for jumping back while climbing (>= 0, <= 1)");
    public final FloatProperty climbBackUpJumpHorizontalFactor = decreasing("move.jump.climb.back.up.horizontal.factor", 0.3f,
            "Horizontal jump speed factor for jumping back while climbing (>= 0, <= 1)");
    public final FloatProperty climbBackUpJumpHandsOnlyVerticalFactor = decreasing("move.jump.climb.back.up.hands.only.vertical.factor", 0.8f,
            "Additional vertical jump speed factor for jumping back while climbing with hands only (>= 0, <= 1)");
    public final FloatProperty climbBackUpJumpHandsOnlyHorizontalFactor = decreasing("move.jump.climb.back.up.hands.only.horizontal.factor",
            "Additional horizontal jump speed factor for jumping back while climbing with hands only (>= 0, <= 1)");

    public final BooleanProperty climbBackHeadJump = section("Climb back head jumping",
            "Below you find all climb back head jump specific options.",
            on("move.jump.climb.back.head", "To switch on/off head jumping back while climbing"));
    public final FloatProperty climbBackHeadJumpVerticalFactor = decreasing("move.jump.climb.back.head.vertical.factor", 0.2f,
            "Additional vertical jump speed factor for head jumping back while climbing (>= 0, <= 1)");
    public final FloatProperty climbBackHeadJumpHorizontalFactor = decreasing("move.jump.climb.back.head.horizontal.factor", 0.3f,
            "Additional horizontal jump speed factor for head jumping back while climbing (>= 0, <= 1)");
    public final FloatProperty climbBackHeadJumpHandsOnlyVerticalFactor = decreasing("move.jump.climb.back.head.hands.only.vertical.factor", 0.8f,
            "Additional vertical jump speed factor for head jumping while climbing with hands only (>= 0, <= 1)");
    public final FloatProperty climbBackHeadJumpHandsOnlyHorizontalFactor = decreasing("move.jump.climb.back.head.hands.only.horizontal.factor",
            "Additional horizontal jump speed factor for head jumping while climbing with hands only (>= 0, <= 1)");

    public final BooleanProperty wallUpJump = section("Wall jumping",
            "Below you find all wall jump specific options.",
            on("move.jump.wall", "To switch on/off wall jumping"));
    public final FloatProperty wallUpJumpVerticalFactor = decreasing("move.jump.wall.vertical.factor", 0.4f,
            "Vertical jump speed factor for wall jumping (>= 0, <= 1)");
    public final FloatProperty wallUpJumpHorizontalFactor = decreasing("move.jump.wall.horizontal.factor", 0.15f,
            "Horizontal jump speed factor for wall jumping (>= 0, <= 1)");
    public final FloatProperty wallUpJumpFallMaximumDistance = positive("move.jump.wall.fall.maximum.distance", 2,
            "Distance in blocks to fall to block all wall jumping attempts");
    public final FloatProperty wallUpJumpOrthogonalTolerance = positive("move.jump.wall.orthogonal.tolerance", 5,
            "Tolerance angle in degree for wall jumping orthogonally (>= 0, <= 45)");

    public final BooleanProperty wallHeadJump = section("Wall head jumping",
            "Below you find all wall head jump specific options.",
            on("move.jump.wall.head", "To switch on/off wall head jumping"));
    public final FloatProperty wallHeadJumpVerticalFactor = decreasing("move.jump.wall.head.vertical.factor", 0.3f,
            "Vertical jump speed factor for wall head jumping (>= 0, <= 1)");
    public final FloatProperty wallHeadJumpHorizontalFactor = decreasing("move.jump.wall.head.horizontal.factor", 0.15f,
            "Horizontal jump speed factor for wall head jumping (>= 0, <= 1)");
    public final FloatProperty wallHeadJumpFallMaximumDistance = positive("move.jump.wall.head.fall.maximum.distance", 3,
            "Distance in blocks to fall to block all wall head jumping attempts (>= \"move.jump.wall.fall.maximum.distance\")")
            .min(wallUpJumpFallMaximumDistance::get);

    // Item usage

    public final FloatProperty usageSpeedFactor = section("Item Usage",
            "Below you find the options to manipulate the speed factors additionally applied while using an item.",
            decreasing("move.usage.speed.factor", 0.2f,
                    "Speed factor while using an item, if not defined otherwise (>= 0 AND <= 1)"));
    public final FloatProperty usageSwordSpeedFactor = decreasing("move.usage.sword.speed.factor", usageSpeedFactor::get,
            "Speed factor while blocking with a sword (>= 0 AND <= 1, defaults to \"move.usage.speed.factor\" when not present)");
    public final FloatProperty usageBowSpeedFactor = decreasing("move.usage.bow.speed.factor", usageSpeedFactor::get,
            "Speed factor while pulling back a bow (>= 0 AND <= 1, defaults to \"move.usage.speed.factor\" when not present)");
    public final FloatProperty usageFoodSpeedFactor = decreasing("move.usage.food.speed.factor", usageSpeedFactor::get,
            "Speed factor while eating food (>= 0 AND <= 1, defaults to \"move.usage.speed.factor\" when not present)");
    public final BooleanProperty usageSprint = off("move.usage.sprint", "To switch on/off generic sprinting while using an item");

    /** Ladders and vines are free climbed instead of with a base climbing mode ({@code isFreeBaseClimb}). */
    public boolean isFreeBaseClimb() {
        return isFreeBaseClimb.getAsBoolean();
    }
}
