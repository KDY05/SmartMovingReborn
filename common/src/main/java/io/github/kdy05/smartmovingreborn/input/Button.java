package io.github.kdy05.smartmovingreborn.input;

/** Tracks one button across ticks, like the original {@code Button}: held, just pressed, just released. */
public final class Button {
    public boolean pressed;
    public boolean wasPressed;
    public boolean startPressed;
    public boolean stopPressed;

    /** Call once per tick with the current held state. */
    public void update(boolean pressed) {
        wasPressed = this.pressed;
        this.pressed = pressed;
        startPressed = !wasPressed && pressed;
        stopPressed = wasPressed && !pressed;
    }
}
