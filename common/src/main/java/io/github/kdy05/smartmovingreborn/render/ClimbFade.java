package io.github.kdy05.smartmovingreborn.render;

/**
 * One climber's limb settings as last drawn, easing towards the ones of the current hands and feet types by a
 * fifth per tick like the body's turn ({@link OuterFade}). The original switched them at once, so the arms
 * jumped by about 50 degrees when the hands found room above. Not in the original.
 */
final class ClimbFade {
    /** The settings by type: how far the arms swing up and their resting angle, the same for the legs, and how far they step aside. */
    static final int HANDS_UP = 0;
    static final int HANDS_UP_OFFSET = 1;
    static final int FEET_UP = 2;
    static final int FEET_UP_OFFSET = 3;
    static final int FEET_SIDE = 4;

    private final float[] values = new float[5];
    private float time = Float.NaN;

    /** The settings of a hands and a feet type ({@code SmartMovingModel.setRotationAngles} 143-189). */
    static float[] targets(int handsType, int feetType) {
        float[] targets = new float[5];
        targets[HANDS_UP] = handsType == 0 ? 0 : 2;
        targets[HANDS_UP_OFFSET] = switch (handsType) {
            case 1 -> -2.5f;
            case 2 -> -(float) Math.PI / 2;
            default -> -0.5f;
        };
        targets[FEET_UP] = feetType == 1 ? 0.3f : 0;
        targets[FEET_UP_OFFSET] = feetType == 1 ? -0.3f : 0;
        targets[FEET_SIDE] = feetType == 1 ? 0.5f : 0;
        return targets;
    }

    /**
     * Moves towards {@code targets} for the frame at {@code time} (ticks) and returns the eased settings. After a
     * gap of more than two ticks, or going back in time, they jump to the targets.
     */
    float[] update(float[] targets, float time) {
        float elapsed = time - this.time;
        boolean recent = elapsed >= 0 && elapsed <= 2;
        for (int i = 0; i < values.length; i++) {
            values[i] = recent ? values[i] + (targets[i] - values[i]) * Math.min(1, elapsed * 0.2f) : targets[i];
        }
        this.time = time;
        return values;
    }
}
