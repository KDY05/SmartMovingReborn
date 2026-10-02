package io.github.kdy05.smartmovingreborn.logic;

/**
 * The walking speed, sneak and sprint decisions of the original ({@code SmartMovingSelf.getSpeedFactor}
 * 212-283, the run factor in {@code landMotion} 743-747, the sneak and sprint decisions 2466-2563 and
 * {@code isSneaking} 2995-3002), as pure functions of what the caller measured, so they can be tested against
 * the original.
 * <p>
 * The original replaced vanilla's movement and applied its speed factor to a speed without vanilla's sprint
 * bonus. Here vanilla still moves the player, so {@link #landSpeedFactor} is a multiplier on vanilla's speed
 * that takes the sprint bonus back out. The original also multiplied in the movement speed attribute a second
 * time on the ground (so speed effects counted twice); here the attribute counts once, through vanilla.
 */
public final class SpeedLogic {
    /** Vanilla's sprinting speed bonus, both on the ground (the attribute modifier) and in the air. */
    public static final float VANILLA_SPRINT_FACTOR = 1.3f;
    /** Horizontal collision lasting this many ticks stops sprinting. */
    public static final int SPRINT_COLLISION_TICKS = 3;

    private SpeedLogic() {
    }

    /**
     * The multiplier on vanilla's speed on land and in the air.
     *
     * @param itemFactor  the item usage factor while using an item, otherwise 1
     * @param running     vanilla sprinting without Smart Moving's sprint, on the ground (the original's {@code isRunning})
     * @param sprinting   whether vanilla sprints, i.e. whether vanilla's speed includes its sprint bonus
     */
    public static float landSpeedFactor(float globalFactor, float itemFactor,
                                        boolean crawling, float crawlFactor, boolean slow, float sneakFactor,
                                        boolean fast, float sprintFactor, boolean running, float runFactor,
                                        boolean sprinting) {
        float factor = globalFactor * itemFactor;
        if (crawling) {
            factor *= crawlFactor;
        } else if (slow) {
            factor *= sneakFactor;
        }
        if (fast) {
            factor *= sprintFactor;
        } else if (running) {
            factor *= runFactor;
        }
        return sprinting ? factor / VANILLA_SPRINT_FACTOR : factor;
    }

    /**
     * Whether the input asks for sneaking regardless of the switch ({@code wouldWantSneak}): held, or toggled
     * (which a fresh press also starts), unless crawling, sliding, head jumping, flying with Smart Moving or
     * holding grab. Swimming and diving join with those moves.
     */
    public static boolean wouldWantSneak(boolean toggleMode, boolean toggled, boolean sneakPressed,
                                         boolean sneakStarted, boolean wantCrawl, boolean mustCrawl,
                                         boolean crawlEnabled, boolean grabPressed, boolean flying,
                                         boolean slidingOrHeadJumping) {
        boolean continueInput = toggleMode ? toggled || sneakStarted : sneakPressed;
        return !flying && !slidingOrHeadJumping && continueInput && !wantCrawl && !mustCrawl
                && (!crawlEnabled || !grabPressed);
    }

    /**
     * The jump part of the speed factor ({@code landMotion} 715-738): holding jump while sprinting on the
     * ground uses the sprint jump's vertical factor, and in the air {@code move.jump.control.factor} applies,
     * or {@code move.jump.head.control.factor} while head jumping.
     *
     * @param onGround            on the ground and not jumping this tick
     * @param sprintJumpEnabled   whether sprint jumps are switched on
     */
    public static float jumpFactor(boolean onGround, boolean jumpInput, boolean fast, boolean sprintJumpEnabled,
                                   float sprintJumpVerticalFactor, float jumpControlFactor, boolean headJumping,
                                   float headJumpControlFactor) {
        if (headJumping) {
            return headJumpControlFactor;
        }
        if (onGround) {
            return jumpInput && fast && sprintJumpEnabled ? sprintJumpVerticalFactor : 1;
        }
        return jumpControlFactor;
    }

    /**
     * What vanilla sees as the sneak key ({@code isSneaking}): slow on the ground, charging a jump with
     * sneaking switched off (so that the player still crouches), or crawling without {@code move.crawl.edge}
     * so that vanilla keeps the player from falling off edges.
     */
    public static boolean shiftKeyDown(boolean slow, boolean onGround, boolean sneakEnabled, boolean wouldSneak,
                                       float jumpCharge, boolean crawling, boolean crawlOverEdge) {
        return slow && onGround || !sneakEnabled && wouldSneak && jumpCharge > 0 || !crawlOverEdge && crawling;
    }

    /**
     * Whether the player wants Smart Moving's sprint ({@code wantSprint}), never while sliding. The swimming,
     * diving, climbing and flying cases join with those moves.
     */
    public static boolean wantSprint(boolean sprintEnabled, boolean sprintPressed, boolean forwardPressed,
                                     boolean sliding, boolean disabled) {
        return sprintEnabled && !sliding && sprintPressed && forwardPressed && !disabled;
    }

    /**
     * Whether the player sprints on the ground ({@code isGroundSprinting}): wants to sprint and not to sneak,
     * does not burn, uses no item (unless {@code move.usage.sprint}) and has not run into a wall for a while.
     */
    public static boolean groundSprinting(boolean wantSprint, boolean wantSneak, boolean burning,
                                          boolean usingItem, boolean sprintWhileUsing,
                                          int collidedHorizontallyTicks, boolean onGround) {
        return canAnySprint(wantSprint, wantSneak, burning, usingItem, sprintWhileUsing)
                && collidedHorizontallyTicks < SPRINT_COLLISION_TICKS && onGround;
    }

    /**
     * Whether the player sprints along a ceiling ({@code isCeilingSprinting}): like on the ground, but while
     * hanging on a ceiling.
     */
    public static boolean ceilingSprinting(boolean wantSprint, boolean wantSneak, boolean burning,
                                           boolean usingItem, boolean sprintWhileUsing,
                                           int collidedHorizontallyTicks, boolean ceilingClimbing) {
        return canAnySprint(wantSprint, wantSneak, burning, usingItem, sprintWhileUsing)
                && collidedHorizontallyTicks < SPRINT_COLLISION_TICKS && ceilingClimbing;
    }

    /** Whether any kind of Smart Moving sprinting may happen ({@code canAnySprint}). */
    public static boolean canAnySprint(boolean wantSprint, boolean wantSneak, boolean burning, boolean usingItem,
                                       boolean sprintWhileUsing) {
        return wantSprint && !wantSneak && !burning && (sprintWhileUsing || !usingItem);
    }

    /**
     * Whether vanilla should sprint once Smart Moving's sprint starts ({@code isStandupSprintingOrRunning}):
     * upright on the ground, sprinting either way.
     */
    public static boolean standupSprintingOrRunning(boolean fast, boolean sprinting, boolean onGround,
                                                    boolean sliding, boolean crawling) {
        return (fast || sprinting) && onGround && !sliding && !crawling;
    }

    /**
     * The movement speed the field of view is computed from ({@code afterOnUpdate} 1773-1786): vanilla's
     * (including its sprint bonus) while walking, or with the sprint or run perspective factor instead of
     * vanilla's bonus.
     */
    public static float perspectiveSpeed(float movementSpeed, boolean fast, boolean sprintJump, boolean running,
                                         boolean sprinting, float sprintPerspectiveFactor, float runPerspectiveFactor) {
        if (!fast && !sprintJump && !running) {
            return movementSpeed;
        }
        float speed = sprinting ? movementSpeed / VANILLA_SPRINT_FACTOR : movementSpeed;
        if (fast || sprintJump) {
            return speed * sprintPerspectiveFactor;
        }
        return speed * VANILLA_SPRINT_FACTOR * runPerspectiveFactor;
    }

    /** Moves the faded perspective speed towards {@code target}; the first value is taken as is. */
    public static float fadePerspective(float faded, float target, float fadeFactor, float initial) {
        return faded < 0 ? initial : faded + (target - faded) * fadeFactor;
    }
}
