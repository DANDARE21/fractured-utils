package net.dandare21.fracturedutils.region;

import net.dandare21.fracturedutils.region.data.EarClippingTriangulator;
import net.dandare21.fracturedutils.region.data.Polygon2D;
import net.dandare21.fracturedutils.region.data.Vec2d;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Polygon2DTest {

    @Test
    public void testPointContainmentSquare() {
        Polygon2D square = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10)
        ));

        assertTrue(square.contains(5, 5), "Center of square should be inside");
        assertTrue(square.contains(1, 1), "Inner corner should be inside");
        assertTrue(square.contains(9, 9), "Opposite inner corner should be inside");

        assertFalse(square.contains(-1, 5), "Left of square should be outside");
        assertFalse(square.contains(11, 5), "Right of square should be outside");
        assertFalse(square.contains(5, -1), "Below square should be outside");
        assertFalse(square.contains(5, 11), "Above square should be outside");
        assertFalse(square.contains(20, 20), "Far point should be outside");
    }

    @Test
    public void testPointContainmentConcaveL() {
        // Concave L-Shape
        // (0,10)---(5,10)
        //   |        |
        //   |     (5,5)---(10,5)
        //   |               |
        // (0,0)-----------(10,0)
        Polygon2D lShape = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 5),
                new Vec2d(5, 5),
                new Vec2d(5, 10),
                new Vec2d(0, 10)
        ));

        assertTrue(lShape.contains(2, 2), "(2, 2) should be inside");
        assertTrue(lShape.contains(2, 8), "(2, 8) should be inside");
        assertTrue(lShape.contains(8, 2), "(8, 2) should be inside");

        // The cut-out top-right quadrant (5..10, 5..10) must be OUTSIDE
        assertFalse(lShape.contains(8, 8), "(8, 8) in the cut-out corner must be outside");
        assertFalse(lShape.contains(6, 6), "(6, 6) in the cut-out corner must be outside");
    }

    @Test
    public void testNearestEdgeAndOutwardNormal() {
        Polygon2D square = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10)
        ));

        // Test from outside right edge: P = (13, 5)
        Polygon2D.ClosestEdgeResult resOut = square.findNearestEdge(13, 5);
        assertEquals(10.0, resOut.closestPoint().x(), 1e-4);
        assertEquals(5.0, resOut.closestPoint().z(), 1e-4);
        assertEquals(9.0, resOut.distanceSq(), 1e-4);

        // Normal must point towards +X (away from polygon)
        assertTrue(resOut.outwardNormal().x() > 0.9, "Outward normal X should be ~1.0");
        assertEquals(0.0, resOut.outwardNormal().z(), 1e-4);

        // Test from INSIDE near right edge: P = (9, 5)
        Polygon2D.ClosestEdgeResult resIn = square.findNearestEdge(9, 5);
        assertEquals(10.0, resIn.closestPoint().x(), 1e-4);
        assertEquals(5.0, resIn.closestPoint().z(), 1e-4);
        assertEquals(1.0, resIn.distanceSq(), 1e-4);

        // Outward normal must STILL point towards +X (pushing player outside)
        assertTrue(resIn.outwardNormal().x() > 0.9, "Outward normal from inside must still push outward (+X)");
        Vec2d pushed = resIn.closestPoint().add(resIn.outwardNormal().scale(0.1));
        assertFalse(square.contains(pushed.x(), pushed.z()), "Point displaced along outward normal must be outside");
    }

    @Test
    public void testCentroid() {
        Polygon2D square = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10)
        ));

        Vec2d c = square.getCentroid();
        assertEquals(5.0, c.x(), 1e-4);
        assertEquals(5.0, c.z(), 1e-4);
    }

    @Test
    public void testVertexSnapping() {
        Polygon2D poly = new Polygon2D(List.of(
                new Vec2d(100, 200),
                new Vec2d(150, 200),
                new Vec2d(150, 250),
                new Vec2d(100, 250)
        ));

        // 0.8 blocks away -> within 1.5 blocks threshold
        Vec2d snapped = poly.snapVertex(100.5, 200.6, 1.5);
        assertNotNull(snapped);
        assertEquals(100.0, snapped.x());
        assertEquals(200.0, snapped.z());

        // 3.0 blocks away -> outside threshold
        Vec2d missed = poly.snapVertex(103.0, 200.0, 1.5);
        assertNull(missed);
    }

    @Test
    public void testEarClippingTriangulator() {
        // Quad (4 vertices) -> 2 triangles (6 indices)
        List<Vec2d> quad = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10)
        );
        int[] quadTriangles = EarClippingTriangulator.triangulate(quad);
        assertEquals(6, quadTriangles.length);
        for (int idx : quadTriangles) {
            assertTrue(idx >= 0 && idx < 4);
        }

        // L-shape (6 vertices) -> 4 triangles (12 indices)
        List<Vec2d> lShape = List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 5),
                new Vec2d(5, 5),
                new Vec2d(5, 10),
                new Vec2d(0, 10)
        );
        int[] lTriangles = EarClippingTriangulator.triangulate(lShape);
        assertEquals(12, lTriangles.length);
        for (int idx : lTriangles) {
            assertTrue(idx >= 0 && idx < 6);
        }
    }

    @Test
    public void testIsSimplePolygon() {
        Polygon2D simple = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10)
        ));
        assertTrue(simple.isSimplePolygon(), "Standard square must be simple");

        // Bowtie / Self-intersecting polygon
        Polygon2D selfIntersecting = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 10),
                new Vec2d(10, 0),
                new Vec2d(0, 10)
        ));
        assertFalse(selfIntersecting.isSimplePolygon(), "Bowtie polygon must not be simple");
    }

    @Test
    public void testPolygonArea() {
        Polygon2D square = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(10, 10),
                new Vec2d(0, 10)
        ));
        assertEquals(100.0, square.getArea(), 1e-4);

        Polygon2D triangle = new Polygon2D(List.of(
                new Vec2d(0, 0),
                new Vec2d(10, 0),
                new Vec2d(0, 10)
        ));
        assertEquals(50.0, triangle.getArea(), 1e-4);
    }
}

