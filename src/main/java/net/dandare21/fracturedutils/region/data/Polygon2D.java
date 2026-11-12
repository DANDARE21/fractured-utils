package net.dandare21.fracturedutils.region.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Closed 2D horizontal polygon on the (X, Z) plane with cached AABB bounds,
 * nanosecond ray-cast containment queries, and nearest-edge pushback normal calculations.
 */
public class Polygon2D {

    public record ClosestEdgeResult(Vec2d closestPoint, Vec2d outwardNormal, double distanceSq, int edgeIndex) {}

    private final List<Vec2d> vertices;
    private double minX;
    private double maxX;
    private double minZ;
    private double maxZ;
    private Vec2d cachedCentroid;

    public Polygon2D(List<Vec2d> vertices) {
        this.vertices = new ArrayList<>(vertices);
        recalculateBounds();
    }

    public List<Vec2d> getVertices() {
        return Collections.unmodifiableList(vertices);
    }

    public int vertexCount() {
        return vertices.size();
    }

    public double getMinX() {
        return minX;
    }

    public double getMaxX() {
        return maxX;
    }

    public double getMinZ() {
        return minZ;
    }

    public double getMaxZ() {
        return maxZ;
    }

    public boolean isWithinAABB(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private void recalculateBounds() {
        cachedCentroid = null;
        if (vertices.isEmpty()) {
            minX = maxX = minZ = maxZ = 0;
            return;
        }

        minX = Double.MAX_VALUE;
        maxX = -Double.MAX_VALUE;
        minZ = Double.MAX_VALUE;
        maxZ = -Double.MAX_VALUE;

        for (Vec2d v : vertices) {
            if (v.x() < minX) minX = v.x();
            if (v.x() > maxX) maxX = v.x();
            if (v.z() < minZ) minZ = v.z();
            if (v.z() > maxZ) maxZ = v.z();
        }
    }

    /**
     * Ray-cast Jordan Curve containment check.
     */
    public boolean contains(double px, double pz) {
        if (vertices.size() < 3) {
            return false;
        }
        if (!isWithinAABB(px, pz)) {
            return false;
        }

        boolean inside = false;
        int n = vertices.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Vec2d vi = vertices.get(i);
            Vec2d vj = vertices.get(j);

            double xi = vi.x(), zi = vi.z();
            double xj = vj.x(), zj = vj.z();

            if (((zi > pz) != (zj > pz)) &&
                (px < (xj - xi) * (pz - zi) / (zj - zi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean contains(Vec2d point) {
        return contains(point.x(), point.z());
    }

    /**
     * Calculates the closest point Q on the polygon's perimeter and its outward unit normal vector.
     */
    public ClosestEdgeResult findNearestEdge(double px, double pz) {
        if (vertices.size() < 2) {
            Vec2d p = vertices.isEmpty() ? new Vec2d(px, pz) : vertices.get(0);
            return new ClosestEdgeResult(p, new Vec2d(0, 1), p.distanceSqTo(px, pz), 0);
        }

        double minDistanceSq = Double.MAX_VALUE;
        Vec2d bestClosestPoint = null;
        Vec2d bestOutwardNormal = null;
        int bestEdgeIndex = -1;

        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Vec2d a = vertices.get(i);
            Vec2d b = vertices.get((i + 1) % n);

            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double lenSq = dx * dx + dz * dz;

            Vec2d q;
            if (lenSq < 1e-9) {
                q = a;
            } else {
                double t = ((px - a.x()) * dx + (pz - a.z()) * dz) / lenSq;
                if (t <= 0.0) {
                    q = a;
                } else if (t >= 1.0) {
                    q = b;
                } else {
                    q = new Vec2d(a.x() + t * dx, a.z() + t * dz);
                }
            }

            double distSq = (px - q.x()) * (px - q.x()) + (pz - q.z()) * (pz - q.z());
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq;
                bestClosestPoint = q;
                bestEdgeIndex = i;

                // Perpendicular vector (-dz, dx)
                double edgeLen = Math.sqrt(lenSq);
                Vec2d normal;
                if (edgeLen < 1e-9) {
                    normal = new Vec2d(px - q.x(), pz - q.z()).normalize();
                    if (normal.lengthSq() < 1e-6) normal = new Vec2d(0, 1);
                } else {
                    normal = new Vec2d(-dz / edgeLen, dx / edgeLen);
                    // Test outward direction: q + 0.05 * normal should NOT be inside
                    Vec2d testPoint = q.add(normal.scale(0.05));
                    if (contains(testPoint.x(), testPoint.z())) {
                        normal = normal.scale(-1.0);
                    }
                }
                bestOutwardNormal = normal;
            }
        }

        return new ClosestEdgeResult(bestClosestPoint, bestOutwardNormal, minDistanceSq, bestEdgeIndex);
    }

    /**
     * Computes the geometric centroid of the polygon.
     */
    public Vec2d getCentroid() {
        if (cachedCentroid != null) {
            return cachedCentroid;
        }
        if (vertices.isEmpty()) {
            return Vec2d.ZERO;
        }
        if (vertices.size() < 3) {
            double avgX = 0, avgZ = 0;
            for (Vec2d v : vertices) {
                avgX += v.x();
                avgZ += v.z();
            }
            cachedCentroid = new Vec2d(avgX / vertices.size(), avgZ / vertices.size());
            return cachedCentroid;
        }

        double signedArea = 0.0;
        double cx = 0.0;
        double cz = 0.0;

        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Vec2d v0 = vertices.get(i);
            Vec2d v1 = vertices.get((i + 1) % n);
            double cross = (v0.x() * v1.z() - v1.x() * v0.z());
            signedArea += cross;
            cx += (v0.x() + v1.x()) * cross;
            cz += (v0.z() + v1.z()) * cross;
        }

        signedArea *= 0.5;
        if (Math.abs(signedArea) < 1e-6) {
            double avgX = 0, avgZ = 0;
            for (Vec2d v : vertices) {
                avgX += v.x();
                avgZ += v.z();
            }
            cachedCentroid = new Vec2d(avgX / vertices.size(), avgZ / vertices.size());
        } else {
            cx /= (6.0 * signedArea);
            cz /= (6.0 * signedArea);
            cachedCentroid = new Vec2d(cx, cz);
        }
        return cachedCentroid;
    }

    /**
     * Computes the positive surface area of the polygon using the Shoelace formula.
     */
    public double getArea() {
        if (vertices.size() < 3) {
            return 0.0;
        }
        double signedArea = 0.0;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Vec2d v0 = vertices.get(i);
            Vec2d v1 = vertices.get((i + 1) % n);
            signedArea += (v0.x() * v1.z() - v1.x() * v0.z());
        }
        return Math.abs(signedArea * 0.5);
    }

    /**
     * Checks if coordinates are within threshold of any existing vertex in this polygon.
     */
    public Vec2d snapVertex(double x, double z, double threshold) {
        double thresholdSq = threshold * threshold;
        for (Vec2d v : vertices) {
            if (v.distanceSqTo(x, z) <= thresholdSq) {
                return v;
            }
        }
        return null;
    }

    /**
     * Checks if two non-adjacent edge segments intersect.
     */
    public boolean isSimplePolygon() {
        int n = vertices.size();
        if (n < 4) return true;

        for (int i = 0; i < n; i++) {
            Vec2d a1 = vertices.get(i);
            Vec2d a2 = vertices.get((i + 1) % n);
            for (int j = i + 2; j < n; j++) {
                if (i == 0 && j == n - 1) continue; // Adjacent edge
                Vec2d b1 = vertices.get(j);
                Vec2d b2 = vertices.get((j + 1) % n);
                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean segmentsIntersect(Vec2d p1, Vec2d p2, Vec2d p3, Vec2d p4) {
        double d1 = direction(p3, p4, p1);
        double d2 = direction(p3, p4, p2);
        double d3 = direction(p1, p2, p3);
        double d4 = direction(p1, p2, p4);

        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        return false;
    }

    private static double direction(Vec2d pi, Vec2d pj, Vec2d pk) {
        return (pk.x() - pi.x()) * (pj.z() - pi.z()) - (pj.x() - pi.x()) * (pk.z() - pi.z());
    }

    public Polygon2D copy() {
        return new Polygon2D(new ArrayList<>(vertices));
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Vec2d v : vertices) {
            list.add(v.toNbt());
        }
        tag.put("vertices", list);
        return tag;
    }

    public static Polygon2D fromNbt(CompoundTag tag) {
        List<Vec2d> list = new ArrayList<>();
        if (tag.contains("vertices", Tag.TAG_LIST)) {
            ListTag listTag = tag.getList("vertices", Tag.TAG_COMPOUND);
            for (int i = 0; i < listTag.size(); i++) {
                list.add(Vec2d.fromNbt(listTag.getCompound(i)));
            }
        }
        return new Polygon2D(list);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(vertices.size());
        for (Vec2d v : vertices) {
            v.encode(buf);
        }
    }

    public static Polygon2D decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Vec2d> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(Vec2d.decode(buf));
        }
        return new Polygon2D(list);
    }
}

