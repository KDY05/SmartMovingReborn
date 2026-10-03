package io.github.kdy05.smartmovingreborn.render;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * One joint of the Smart Render skeleton, ported from {@code ModelRotationRenderer}: a rotation point (pixels,
 * relative to the parent), rotations applied in a chosen order, a scale and an offset (blocks, after the
 * scale). Only the transform math is kept; drawing is left to vanilla's {@link ModelPart}s.
 */
final class ModelJoint {
    /** The order in which the three rotations apply to a vertex, e.g. {@code XYZ}: X first, Z last. */
    enum RotationOrder { XYZ, XZY, YXZ, YZX, ZXY, ZYX }

    private final ModelJoint parent;

    float x;
    float y;
    float z;
    float xRot;
    float yRot;
    float zRot;
    RotationOrder order;
    float xScale;
    float yScale;
    float zScale;
    float xOffset;
    float yOffset;
    float zOffset;
    /**
     * Turns from model space instead of the parent's rotation, keeping only where the parent puts the rotation
     * point ({@code ignoreSuperRotation}).
     */
    boolean ignoreParentRotation;

    ModelJoint(ModelJoint parent) {
        this.parent = parent;
        reset();
    }

    void reset() {
        x = y = z = 0;
        xRot = yRot = zRot = 0;
        order = RotationOrder.XYZ;
        xScale = yScale = zScale = 1;
        xOffset = yOffset = zOffset = 0;
        ignoreParentRotation = false;
    }

    void setRotationPoint(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Transform from this joint's space to model space. */
    Matrix4f world() {
        Matrix4f matrix = parent == null ? new Matrix4f() : parent.world();
        matrix.translate(x / 16, y / 16, z / 16);
        if (ignoreParentRotation) {
            matrix.translation(matrix.getTranslation(new Vector3f()));
        }
        rotate(matrix);
        return matrix.scale(xScale, yScale, zScale).translate(xOffset, yOffset, zOffset);
    }

    private void rotate(Matrix4f matrix) {
        switch (order) {
            case XYZ -> matrix.rotateZ(zRot).rotateY(yRot).rotateX(xRot);
            case XZY -> matrix.rotateY(yRot).rotateZ(zRot).rotateX(xRot);
            case YXZ -> matrix.rotateZ(zRot).rotateX(xRot).rotateY(yRot);
            case YZX -> matrix.rotateX(xRot).rotateZ(zRot).rotateY(yRot);
            case ZXY -> matrix.rotateY(yRot).rotateX(xRot).rotateZ(zRot);
            case ZYX -> matrix.rotateX(xRot).rotateY(yRot).rotateZ(zRot);
        }
    }

    /**
     * Gives {@code part} this joint's composed transform. {@link ModelPart} applies translation, then rotation
     * in ZYX Euler angles, then scale, so the scale must belong to this joint alone: joints with children keep
     * a scale of 1 (Smart Moving only scales arms and legs).
     */
    void applyTo(ModelPart part) {
        Matrix4f matrix = world();
        Vector3f translation = matrix.getTranslation(new Vector3f());
        Vector3f angles = eulerAnglesZYX(matrix.normalize3x3());
        part.x = translation.x * 16;
        part.y = translation.y * 16;
        part.z = translation.z * 16;
        part.xRot = angles.x;
        part.yRot = angles.y;
        part.zRot = angles.z;
        part.xScale = xScale;
        part.yScale = yScale;
        part.zScale = zScale;
    }

    /**
     * The angles {@code x, y, z} with {@code rotation = Rz(z) Ry(y) Rx(x)}, as {@link ModelPart} applies them.
     * Unlike JOML's {@code getEulerAnglesZYX}, this handles a Y angle of ±90° (e.g. a leg turned sideways in a
     * side jump), where X and Z turn about the same axis: Z is then 0 and X takes the whole turn. JOML takes
     * both from near-zero entries there, so the turn could vanish.
     */
    static Vector3f eulerAnglesZYX(Matrix4f rotation) {
        float sinY = -rotation.m02();
        if (Math.abs(sinY) < 1 - 1.0E-6f) {
            return new Vector3f((float) Math.atan2(rotation.m12(), rotation.m22()), (float) Math.asin(sinY),
                    (float) Math.atan2(rotation.m01(), rotation.m00()));
        }
        return new Vector3f((float) Math.atan2(-rotation.m21(), rotation.m11()),
                Math.copySign((float) Math.PI / 2, sinY), 0);
    }
}
