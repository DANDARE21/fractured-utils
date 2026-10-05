package net.dandare21.fracturedutils.client.camera;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class CameraTransform {
    private final Vec3 position;
    private final Quaternionf rotation;
    private final double fov;

    private float cachedYaw = Float.NaN;
    private float cachedPitch = Float.NaN;
    private float cachedRoll = Float.NaN;

    public CameraTransform(Vec3 position, Quaternionf rotation, double fov) {
        this.position = position != null ? position : Vec3.ZERO;
        this.rotation = rotation != null ? new Quaternionf(rotation) : new Quaternionf();
        this.fov = fov > 0.0 ? fov : 70.0;
    }

    public static CameraTransform fromEuler(double x, double y, double z, float yaw, float pitch, float roll, double fov) {
        Quaternionf q = CameraMath.eulerToQuaternion(yaw, pitch, roll);
        CameraTransform t = new CameraTransform(new Vec3(x, y, z), q, fov);
        t.cachedYaw = yaw;
        t.cachedPitch = pitch;
        t.cachedRoll = roll;
        return t;
    }

    public static CameraTransform fromEuler(Vec3 pos, float yaw, float pitch, float roll, double fov) {
        return fromEuler(pos.x, pos.y, pos.z, yaw, pitch, roll, fov);
    }

    public static CameraTransform fromQuaternion(double x, double y, double z, Quaternionf q, double fov) {
        return new CameraTransform(new Vec3(x, y, z), q, fov);
    }

    public static CameraTransform fromQuaternion(Vec3 pos, Quaternionf q, double fov) {
        return new CameraTransform(pos, q, fov);
    }

    public static CameraTransform lookAt(Vec3 eyePos, Vec3 targetPos, float roll, double fov) {
        Quaternionf q = CameraMath.lookAtQuaternion(eyePos, targetPos, roll);
        return new CameraTransform(eyePos, q, fov);
    }

    public Vec3 getPosition() {
        return position;
    }

    public double getX() {
        return position.x;
    }

    public double getY() {
        return position.y;
    }

    public double getZ() {
        return position.z;
    }

    public Quaternionf getRotation() {
        return new Quaternionf(rotation);
    }

    public double getFov() {
        return fov;
    }

    private void ensureEulerCached() {
        if (Float.isNaN(cachedYaw)) {
            float[] euler = CameraMath.quaternionToEuler(rotation);
            this.cachedYaw = euler[0];
            this.cachedPitch = euler[1];
            this.cachedRoll = euler[2];
        }
    }

    public float getYaw() {
        ensureEulerCached();
        return cachedYaw;
    }

    public float getPitch() {
        ensureEulerCached();
        return cachedPitch;
    }

    public float getRoll() {
        ensureEulerCached();
        return cachedRoll;
    }

    public CameraTransform interpolate(CameraTransform target, double t) {
        if (target == null) return this;
        Vec3 pos = CameraMath.lerp(this.position, target.position, t);
        Quaternionf rot = CameraMath.slerp(this.rotation, target.rotation, t);
        double f = this.fov + (target.fov - this.fov) * Math.max(0.0, Math.min(1.0, t));
        return new CameraTransform(pos, rot, f);
    }

    public CameraTransform withLookAt(Vec3 targetPos, double weight) {
        if (targetPos == null || weight <= 0.0) return this;
        Quaternionf lookAtQ = CameraMath.lookAtQuaternion(this.position, targetPos, getRoll());
        if (weight >= 1.0) {
            return new CameraTransform(this.position, lookAtQ, this.fov);
        }
        Quaternionf blended = CameraMath.slerp(this.rotation, lookAtQ, weight);
        return new CameraTransform(this.position, blended, this.fov);
    }

    public boolean hasSameSpecs(CameraTransform other) {
        if (other == null) return false;
        if (this.position.distanceToSqr(other.position) >= 1e-6) return false;
        if (Math.abs(this.fov - other.fov) >= 1e-3) return false;
        float dot = Math.abs(this.rotation.x * other.rotation.x + this.rotation.y * other.rotation.y
                + this.rotation.z * other.rotation.z + this.rotation.w * other.rotation.w);
        return (1.0f - dot) <= 1e-4f;
    }
}
