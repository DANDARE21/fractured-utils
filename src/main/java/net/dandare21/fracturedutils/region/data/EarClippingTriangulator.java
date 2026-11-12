package net.dandare21.fracturedutils.region.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Robust O(n^2) Ear Clipping Triangulator for simple 2D polygons.
 * Converts ordered polygon vertex loops into triangle indices for translucent mesh rendering.
 */
public class EarClippingTriangulator {

    /**
     * Triangulates a polygon into a list of triangle index triples: [i0, i1, i2, j0, j1, j2, ...].
     */
    public static int[] triangulate(List<Vec2d> vertices) {
        int n = vertices.size();
        if (n < 3) {
            return new int[0];
        }
        if (n == 3) {
            return new int[]{0, 1, 2};
        }

        // Determine polygon winding order (true if counter-clockwise)
        double area = computeSignedArea(vertices);
        List<Integer> indices = new ArrayList<>(n);
        if (area > 0) {
            for (int i = 0; i < n; i++) indices.add(i);
        } else {
            for (int i = n - 1; i >= 0; i--) indices.add(i);
        }

        int[] result = new int[(n - 2) * 3];
        int resultIdx = 0;

        int iterations = 0;
        int maxIterations = n * n * 2;

        while (indices.size() > 3 && iterations++ < maxIterations) {
            boolean earFound = false;
            int count = indices.size();

            for (int i = 0; i < count; i++) {
                int prevIdx = indices.get((i - 1 + count) % count);
                int currIdx = indices.get(i);
                int nextIdx = indices.get((i + 1) % count);

                Vec2d prev = vertices.get(prevIdx);
                Vec2d curr = vertices.get(currIdx);
                Vec2d next = vertices.get(nextIdx);

                if (isConvex(prev, curr, next) && containsNoOtherVertex(prev, curr, next, indices, vertices)) {
                    result[resultIdx++] = prevIdx;
                    result[resultIdx++] = currIdx;
                    result[resultIdx++] = nextIdx;
                    indices.remove(i);
                    earFound = true;
                    break;
                }
            }

            // Fallback for slightly non-simple or collinear polygons
            if (!earFound) {
                int prevIdx = indices.get(0);
                int currIdx = indices.get(1);
                int nextIdx = indices.get(2);
                result[resultIdx++] = prevIdx;
                result[resultIdx++] = currIdx;
                result[resultIdx++] = nextIdx;
                indices.remove(1);
            }
        }

        if (indices.size() == 3) {
            result[resultIdx++] = indices.get(0);
            result[resultIdx++] = indices.get(1);
            result[resultIdx++] = indices.get(2);
        }

        return result;
    }

    private static double computeSignedArea(List<Vec2d> vertices) {
        double area = 0.0;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Vec2d v1 = vertices.get(i);
            Vec2d v2 = vertices.get((i + 1) % n);
            area += (v1.x() * v2.z() - v2.x() * v1.z());
        }
        return area * 0.5;
    }

    private static boolean isConvex(Vec2d prev, Vec2d curr, Vec2d next) {
        // Cross product of (curr - prev) and (next - curr)
        double cross = (curr.x() - prev.x()) * (next.z() - curr.z()) - (curr.z() - prev.z()) * (next.x() - curr.x());
        return cross > 1e-7;
    }

    private static boolean containsNoOtherVertex(Vec2d a, Vec2d b, Vec2d c, List<Integer> indices, List<Vec2d> vertices) {
        for (int idx : indices) {
            Vec2d p = vertices.get(idx);
            if (p.equals(a) || p.equals(b) || p.equals(c)) {
                continue;
            }
            if (isPointInsideTriangle(p, a, b, c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPointInsideTriangle(Vec2d p, Vec2d a, Vec2d b, Vec2d c) {
        double d1 = sign(p, a, b);
        double d2 = sign(p, b, c);
        double d3 = sign(p, c, a);

        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);

        return !(hasNeg && hasPos);
    }

    private static double sign(Vec2d p1, Vec2d p2, Vec2d p3) {
        return (p1.x() - p3.x()) * (p2.z() - p3.z()) - (p2.x() - p3.x()) * (p1.z() - p3.z());
    }
}

