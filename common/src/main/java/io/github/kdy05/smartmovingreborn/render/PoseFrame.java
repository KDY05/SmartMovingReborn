package io.github.kdy05.smartmovingreborn.render;

import io.github.kdy05.smartmovingreborn.state.MovingState;
import net.minecraft.world.entity.HumanoidArm;

/**
 * What posing one player's model with a Smart Moving pose needs from the player and the world, taken once per
 * frame before the model is posed ({@link SmartMovingRender#beforeRender}). Posing then reads only this and the
 * model's own animation values, not the entity.
 */
final class PoseFrame {
    /** The pose drawn, in the original's order of precedence. */
    enum Kind {
        CLIMB, CLIMB_JUMP, CEILING_CLIMB, SWIM, DIVE, CRAWL, SLIDE, FLY, HEAD_JUMP, FALL, ANGLE_JUMP,
        /** Vanilla's pose. */
        NONE
    }

    final MovingState state;
    final Kind kind;

    // Movement
    /** The eased movement statistics ({@link MotionStatistics}) at this frame. */
    float verticalSpeed;
    float verticalDistance;
    float distance;
    float speed;
    /** The angle of the last tick's movement above the horizontal ({@code currentVerticalAngle}). */
    float verticalAngle;

    // The whole-body rotation ({@link OuterFade}) after this frame's update
    float tilt;
    float headOffset;
    float viewOffset;

    // The arms' work
    /** Whether the player is the own one, whose arms face its view minus the stored body yaw. */
    boolean local;
    /** Whether the body faces the view ({@code rotatePlayer}). */
    boolean facesView;
    /** The arm that swings when attacking. */
    HumanoidArm attackArm;

    // Only for some kinds
    /** {@link Kind#CLIMB}: the hands type to draw, and the eased limb settings ({@link ClimbFade}). */
    int handsType;
    float[] climbLimbs;
    /** {@link Kind#CLIMB} while crawl climbing: the height above the ground; NaN otherwise. */
    float overGroundHeight = Float.NaN;
    /** {@link Kind#FLY}: the tilt the body eases towards. */
    float flyTarget;
    /** {@link Kind#HEAD_JUMP}: how far the arms may close in. */
    float armLimit;

    PoseFrame(MovingState state, Kind kind) {
        this.state = state;
        this.kind = kind;
    }
}
