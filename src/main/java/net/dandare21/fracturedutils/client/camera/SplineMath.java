package net.dandare21.fracturedutils.client.camera;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class SplineMath {
    private static final double EPSILON = 1e-6;

    /**
     * Evaluates a Centripetal Catmull-Rom spline segment between p1 and p2 at normalized time u in [0, 1].
     * Uses alpha = 0.5 (centripetal), which guarantees no cusps, self-intersections, or overshoot loops.
     *
     * @param p0 Control point before segment (or virtual reflected point)
     * @param p1 Start of current segment
     * @param p2 End of current segment
     * @param p3 Control point after segment (or virtual reflected point)
     * @param u  Normalized parameter [0, 1]
     * @return Interpolated 3D coordinate along the spline
     */
    public static Vec3 evaluateCentripetalCatmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u) {
        if (p1 == null && p2 == null) return Vec3.ZERO;
        if (p1 == null) return p2;
        if (p2 == null) return p1;
        double clampedU = Mth.clamp(u, 0.0, 1.0);
        if (clampedU <= 0.0) return p1;
        if (clampedU >= 1.0) return p2;

        // If start and end points are identical (stationary hold segment), position is stationary
        if (p1.distanceToSqr(p2) < 1e-6) {
            return p1;
        }

        // If control point p0 is null, reflect backward
        if (p0 == null) {
            p0 = p1.add(p1.subtract(p2)); // Reflect backward
        }
        // If control point p3 is null, reflect forward
        if (p3 == null) {
            p3 = p2.add(p2.subtract(p1)); // Reflect forward
        }

        double d01 = Math.max(EPSILON, Math.pow(p0.distanceToSqr(p1), 0.25));
        double d12 = Math.max(EPSILON, Math.pow(p1.distanceToSqr(p2), 0.25));
        double d23 = Math.max(EPSILON, Math.pow(p2.distanceToSqr(p3), 0.25));

        double t0 = 0.0;
        double t1 = t0 + d01;
        double t2 = t1 + d12;
        double t3 = t2 + d23;

        double t = t1 + clampedU * (t2 - t1);

        // Barry & Goldman's pyramidal formulation
        Vec3 a1 = interpolate(p0, p1, (t1 - t) / (t1 - t0), (t - t0) / (t1 - t0));
        Vec3 a2 = interpolate(p1, p2, (t2 - t) / (t2 - t1), (t - t1) / (t2 - t1));
        Vec3 a3 = interpolate(p2, p3, (t3 - t) / (t3 - t2), (t - t2) / (t3 - t2));

        Vec3 b1 = interpolate(a1, a2, (t2 - t) / (t2 - t0), (t - t0) / (t2 - t0));
        Vec3 b2 = interpolate(a2, a3, (t3 - t) / (t3 - t1), (t - t1) / (t3 - t1));

        return interpolate(b1, b2, (t2 - t) / (t2 - t1), (t - t1) / (t2 - t1));
    }

    private static Vec3 interpolate(Vec3 v1, Vec3 v2, double w1, double w2) {
        return new Vec3(
                v1.x * w1 + v2.x * w2,
                v1.y * w1 + v2.y * w2,
                v1.z * w1 + v2.z * w2
        );
    }

    /**
     * Evaluates a Cubic Bézier curve between p0 and p3 with tangent control points p1 and p2.
     *
     * @param p0 Start point
     * @param p1 First control point (p0 + outTangent)
     * @param p2 Second control point (p3 + inTangent)
     * @param p3 End point
     * @param u  Normalized parameter [0, 1]
     * @return Interpolated 3D coordinate along the curve
     */
    public static Vec3 evaluateCubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double u) {
        if (p0 == null) p0 = Vec3.ZERO;
        if (p3 == null) p3 = p0;
        if (p1 == null) p1 = p0;
        if (p2 == null) p2 = p3;

        double t = Mth.clamp(u, 0.0, 1.0);
        double oneMinusT = 1.0 - t;
        double oneMinusTSq = oneMinusT * oneMinusT;
        double tSq = t * t;

        double c0 = oneMinusTSq * oneMinusT;
        double c1 = 3.0 * oneMinusTSq * t;
        double c2 = 3.0 * oneMinusT * tSq;
        double c3 = tSq * t;

        double x = c0 * p0.x + c1 * p1.x + c2 * p2.x + c3 * p3.x;
        double y = c0 * p0.y + c1 * p1.y + c2 * p2.y + c3 * p3.y;
        double z = c0 * p0.z + c1 * p1.z + c2 * p2.z + c3 * p3.z;

        return new Vec3(x, y, z);
    }

    /**
     * Evaluates linear interpolation (Lerp) between p0 and p1.
     */
    public static Vec3 evaluateLinear(Vec3 p0, Vec3 p1, double u) {
        return CameraMath.lerp(p0, p1, u);
    }
}
