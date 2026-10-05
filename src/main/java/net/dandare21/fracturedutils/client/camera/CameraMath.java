package net.dandare21.fracturedutils.client.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CameraMath {
    public static final float DEG_TO_RAD = (float) (Math.PI / 180.0);
    public static final float RAD_TO_DEG = (float) (180.0 / Math.PI);

    /**
     * Converts Euler angles (yaw, pitch, roll in degrees) to a rotation Quaternion
     * matching Minecraft's camera coordinate system convention.
     */
    public static Quaternionf eulerToQuaternion(float yaw, float pitch, float roll) {
        float yawRad = -yaw * DEG_TO_RAD;
        float pitchRad = pitch * DEG_TO_RAD;
        float rollRad = roll * DEG_TO_RAD;
        return new Quaternionf().rotationYXZ(yawRad, pitchRad, rollRad);
    }

    /**
     * Decomposes a camera rotation Quaternion into Euler angles [yaw, pitch, roll] in degrees.
     * Prevents gimbal lock by deriving angles through forward and up reference vectors.
     */
    public static float[] quaternionToEuler(Quaternionf q) {
        if (q == null) return new float[]{0.0f, 0.0f, 0.0f};

        // Forward vector in Minecraft space is (0, 0, 1) rotated by q
        Vector3f forward = new Vector3f(0.0f, 0.0f, 1.0f);
        q.transform(forward);

        float fy = Mth.clamp(forward.y, -1.0f, 1.0f);
        float pitch = (float) -Math.asin(fy) * RAD_TO_DEG;
        float yaw = (float) Math.atan2(-forward.x, forward.z) * RAD_TO_DEG;

        // Extract roll by comparing transformed Up vector to unrolled reference frame
        float yawRad = (float) Math.toRadians(yaw);
        Vector3f unrolledRight = new Vector3f((float) Math.cos(yawRad), 0.0f, (float) Math.sin(yawRad));
        Vector3f unrolledUp = new Vector3f(forward).cross(unrolledRight);

        Vector3f actualUp = new Vector3f(0.0f, 1.0f, 0.0f);
        q.transform(actualUp);

        float xRoll = actualUp.dot(unrolledRight);
        float yRoll = actualUp.dot(unrolledUp);
        float roll = (float) Math.atan2(xRoll, yRoll) * RAD_TO_DEG;

        return new float[]{yaw, pitch, roll};
    }

    /**
     * Constructs a look-at orientation quaternion from camera position to target position,
     * maintaining the desired roll angle around the line of sight.
     */
    public static Quaternionf lookAtQuaternion(Vec3 eyePos, Vec3 targetPos, float roll) {
        if (eyePos == null || targetPos == null) {
            return eulerToQuaternion(0.0f, 0.0f, roll);
        }

        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq < 1e-7) {
            return eulerToQuaternion(0.0f, 0.0f, roll);
        }

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(-dx, dz) * RAD_TO_DEG);
        float pitch = (float) (Math.atan2(-dy, distXZ) * RAD_TO_DEG);

        return eulerToQuaternion(yaw, pitch, roll);
    }

    /**
     * Spherical Linear Interpolation (Slerp) between two rotation quaternions.
     * Guaranteed shortest-arc spherical path without boundary flips or gimbal lock.
     */
    public static Quaternionf slerp(Quaternionf q1, Quaternionf q2, double t) {
        if (q1 == null && q2 == null) return new Quaternionf();
        if (q1 == null) return new Quaternionf(q2);
        if (q2 == null) return new Quaternionf(q1);

        Quaternionf copy1 = new Quaternionf(q1);
        return copy1.slerp(q2, (float) Mth.clamp(t, 0.0, 1.0));
    }

    /**
     * Linear interpolation between two 3D vectors.
     */
    public static Vec3 lerp(Vec3 v1, Vec3 v2, double t) {
        if (v1 == null && v2 == null) return Vec3.ZERO;
        if (v1 == null) return v2;
        if (v2 == null) return v1;
        double clamped = Mth.clamp(t, 0.0, 1.0);
        return new Vec3(
                v1.x + (v2.x - v1.x) * clamped,
                v1.y + (v2.y - v1.y) * clamped,
                v1.z + (v2.z - v1.z) * clamped
        );
    }

    /**
     * Computes the forward directional vector for a camera given its yaw and pitch in degrees.
     */
    public static Vec3 getForwardVector(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double fwdX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fwdY = -Math.sin(pitchRad);
        double fwdZ = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3(fwdX, fwdY, fwdZ);
    }

    /**
     * Computes the right directional vector for a camera given its yaw and roll in degrees.
     */
    public static Vec3 getRightVector(float yaw, float pitch, float roll) {
        Quaternionf q = eulerToQuaternion(yaw, pitch, roll);
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f);
        q.transform(right);
        return new Vec3(right.x, right.y, right.z);
    }

    /**
     * Computes the up directional vector for a camera given its yaw, pitch, and roll in degrees.
     */
    public static Vec3 getUpVector(float yaw, float pitch, float roll) {
        Quaternionf q = eulerToQuaternion(yaw, pitch, roll);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        q.transform(up);
        return new Vec3(up.x, up.y, up.z);
    }
}
