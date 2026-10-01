package io.github.kdy05.smartmovingreborn.logic.jump;

/**
 * Double tap detection for side and back jumps ({@code updateEntityActionState} 2724-2799). Each direction
 * counts down from its first tap; a second tap before zero arms it (-1). While a side and the back direction
 * may still combine into a diagonal jump, an armed direction waits (-2) for the other one.
 */
public final class AngleJumpInput {
    private int left;
    private int right;
    private int back;

    public void reset() {
        left = right = back = 0;
    }

    /**
     * Once per tick.
     *
     * @param doubleClickTicks the ticks a second tap may follow the first
     * @param canLeft          whether a left jump is possible now (otherwise its count is cleared)
     */
    public void update(int doubleClickTicks, boolean canLeft, boolean leftStarted, boolean canRight,
                       boolean rightStarted, boolean canBack, boolean backStarted) {
        left = count(left, doubleClickTicks, canLeft, leftStarted);
        right = count(right, doubleClickTicks, canRight, rightStarted);
        back = count(back, doubleClickTicks, canBack, backStarted);

        if (right == -2 && back <= 0) {
            right = -1;
        }
        if (left == -2 && back <= 0) {
            left = -1;
        }
        if (back == -2 && (left <= 0 || right <= 0)) {
            back = -1;
        }
        if (right == -1 && back > 0) {
            right = -2;
        }
        if (left == -1 && back > 0) {
            left = -2;
        }
        if (back == -1 && (left > 0 || right > 0)) {
            back = -2;
        }
    }

    private static int count(int count, int doubleClickTicks, boolean can, boolean started) {
        if (!can) {
            return 0;
        }
        if (started) {
            return count == 0 ? doubleClickTicks : -1;
        }
        return count > 0 ? count - 1 : count;
    }

    /**
     * The yaw offset of the armed jump direction ({@code handleJumping} 1912-1934): 270 left, 90 right,
     * 180 back, 225 and 135 diagonally back, or NaN when none is armed. Clears all counts when one is.
     */
    public float take() {
        int leftness = (left == -1 ? 1 : 0) - (right == -1 ? 1 : 0);
        boolean backward = back == -1;
        if (leftness == 0 && !backward) {
            return Float.NaN;
        }
        reset();
        if (leftness > 0) {
            return backward ? 225 : 270;
        }
        if (leftness < 0) {
            return backward ? 135 : 90;
        }
        return 180;
    }

    /** The angle jump type sent to other players for the animation ({@code handleJumping} 1937). */
    public static int animationType(float angle) {
        return (360 - (int) angle) / 45 % 8;
    }
}
