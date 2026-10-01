package io.github.kdy05.smartmovingreborn.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelJointTest {
    private static final float PI = (float) Math.PI;

    /** Decomposes {@code rotation} and checks that {@code ModelPart}'s rotation from the angles is the same. */
    private static void assertRoundTrip(Matrix4f rotation) {
        Vector3f angles = ModelJoint.eulerAnglesZYX(rotation);
        Matrix4f rebuilt = new Matrix4f().rotationZYX(angles.z, angles.y, angles.x);
        assertTrue(rebuilt.equals(rotation, 1e-5f), () -> "angles " + angles + "\n" + rebuilt + "\n" + rotation);
    }

    @Test
    void generalRotations() {
        assertRoundTrip(new Matrix4f().rotateY(0.3f).rotateX(1.1f).rotateZ(-0.4f));
        assertRoundTrip(new Matrix4f().rotateX(1.37f).rotateZ(0.1f).rotateY(-PI / 2));
    }

    /** The side jump legs: {@code ZXY} order with the Y angle a quarter turn. */
    @Test
    void sidewaysTurnKeepsTheTilt() {
        for (float yRot : new float[] {-PI / 2, -3 * PI / 2, PI / 2}) {
            Matrix4f rotation = new Matrix4f().rotateY(yRot).rotateX(PI / 8).rotateZ(0);
            assertRoundTrip(rotation);
        }
    }
}
