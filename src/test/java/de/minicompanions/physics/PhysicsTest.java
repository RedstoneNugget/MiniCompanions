package de.minicompanions.physics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Physik und Plattform-Graph: Landen, bewegte Plattformen, nie eingeklemmt, große Figuren meiden Lücken. */
class PhysicsTest {

    private static PlatformSet inventoryLike() {
        PlatformSet ps = new PlatformSet(400, 300);
        ps.add(new Ledge("win", 100, 280, 100, 260, Ledge.Kind.WINDOW, true));
        // drei Slot-Reihen im Abstand von 18 px
        ps.add(new Ledge("row1", 110, 270, 150, 168, Ledge.Kind.SLOT, false));
        ps.add(new Ledge("row2", 110, 270, 168, 186, Ledge.Kind.SLOT, false));
        ps.add(new Ledge("row3", 110, 270, 186, 204, Ledge.Kind.SLOT, false));
        return ps;
    }

    private static Body body(float x, float y, float h) {
        Body b = new Body();
        b.x = x;
        b.y = y;
        b.h = h;
        b.w = h / 4f;
        return b;
    }

    @Test
    void fallsAndLandsOnPlatform() {
        PlatformSet ps = inventoryLike();
        Body b = body(150, 10, 16);
        for (int i = 0; i < 200 && !b.onGround; i++) {
            Physics.step(b, ps, 0.016f, 0.5f, 6);
        }
        assertTrue(b.onGround);
        assertEquals("win", b.ledgeId);
        assertEquals(100f, b.y, 0.01f);
    }

    @Test
    void followsMovingPlatformAndFallsWhenItVanishes() {
        PlatformSet ps = inventoryLike();
        Body b = body(150, 100, 16);
        b.setGround(ps.get("win"));
        PlatformSet moved = new PlatformSet(400, 300);
        moved.add(new Ledge("win", 120, 300, 90, 250, Ledge.Kind.WINDOW, true));
        Physics.step(b, moved, 0.016f, 0.5f, 6);
        assertEquals(170f, b.x, 0.01f);
        assertEquals(90f, b.y, 0.01f);
        PlatformSet gone = new PlatformSet(400, 300);
        Physics.step(b, gone, 0.016f, 0.5f, 6);
        assertFalse(b.onGround, "Plattform weg -> fällt");
        for (int i = 0; i < 300 && !b.onGround; i++) {
            Physics.step(b, gone, 0.016f, 0.5f, 6);
        }
        assertEquals("ground", b.ledgeId);
        assertEquals(300f, b.y, 0.01f);
    }

    @Test
    void neverLeavesScreen() {
        PlatformSet ps = new PlatformSet(200, 100);
        Body b = body(10, 50, 16);
        b.vx = -5000;
        b.vy = -5000;
        for (int i = 0; i < 100; i++) {
            Physics.step(b, ps, 0.05f, 0.5f, 6);
            assertTrue(b.x >= b.w / 2 - 0.01f && b.x <= 200 - b.w / 2 + 0.01f);
            assertTrue(b.y - b.h >= -0.01f && b.y <= 100.01f);
        }
        // Fenster wird kleiner: Figur wird zurückgeholt
        PlatformSet small = new PlatformSet(50, 40);
        b.x = 190;
        Physics.step(b, small, 0.016f, 0.5f, 6);
        assertTrue(b.x <= 50);
        assertTrue(b.y <= 40);
    }

    @Test
    void smallFigureReachesLowerSlotRowBigFigureDoesNot() {
        PlatformSet ps = inventoryLike();
        Ledge start = ps.get("win");
        Ledge goal = ps.get("row3");
        PlatformGraph.Spec small = new PlatformGraph.Spec(2, 16, 28, 20, false);
        List<PlatformGraph.Step> path = PlatformGraph.findPath(ps, start, 150, goal, 200, small, 500);
        assertNotNull(path, "kleine Figur passt zwischen die Slot-Reihen");
        PlatformGraph.Spec big = new PlatformGraph.Spec(8, 40, 64, 48, false);
        assertFalse(PlatformGraph.fits(ps, goal, 200, big), "große Figur passt nicht in die Lücke");
        assertNull(PlatformGraph.findPath(ps, start, 150, goal, 200, big, 500));
        // große Figur kann aber auf dem Fensterrand bleiben
        assertTrue(PlatformGraph.fits(ps, start, 150, big));
    }

    @Test
    void climbsWindowFromGround() {
        PlatformSet ps = inventoryLike();
        PlatformGraph.Spec spec = new PlatformGraph.Spec(2, 16, 28, 20, false);
        List<PlatformGraph.Step> path = PlatformGraph.findPath(ps, ps.ground(), 50, ps.get("win"), 150, spec, 500);
        assertNotNull(path);
        assertEquals(PlatformGraph.MoveType.CLIMB, path.get(0).type());
    }

    @Test
    void hideZonesBlockPlatforms() {
        PlatformSet ps = inventoryLike();
        ps.addBlockedZone(100, 50, 300, 120);
        PlatformGraph.Spec spec = new PlatformGraph.Spec(2, 16, 28, 20, false);
        assertFalse(PlatformGraph.fits(ps, ps.get("win"), 150, spec));
    }

    @Test
    void jumpVelocityReachesTarget() {
        float[] v = Physics.jumpVelocity(0, 100, 30, 80, 6);
        PlatformSet ps = new PlatformSet(400, 300);
        ps.add(new Ledge("t", 25, 60, 80, 90, Ledge.Kind.WIDGET, false));
        Body b = body(0.5f, 100, 16);
        b.vx = v[0];
        b.vy = v[1];
        for (int i = 0; i < 300 && !(b.onGround && "t".equals(b.ledgeId)); i++) {
            Physics.step(b, ps, 0.005f, 0f, 0);
        }
        assertEquals("t", b.ledgeId);
    }

    @Test
    void jumpsLandAtAnyFrameRate() {
        // gleiche Sprungbahn bei 20, 60 und 144 Bildern pro Sekunde: die Figur landet immer auf der Kante
        for (float dt : new float[] {0.05f, 0.016f, 0.007f}) {
            PlatformSet ps = new PlatformSet(400, 300);
            ps.add(new Ledge("high", 200, 260, 270, 300, Ledge.Kind.HUD, false));
            Body b = body(150, 300, 16);
            b.setGround(ps.get("ground"));
            float[] v = Physics.jumpVelocity(b.x, b.y, 220, 270, 7 + 16 * 0.35f);
            b.leaveGround();
            b.vx = v[0];
            b.vy = v[1];
            for (int i = 0; i < 2000 && !b.onGround; i++) {
                Physics.step(b, ps, dt, 0, 6);
            }
            assertEquals("high", b.ledgeId, "Landung bei dt=" + dt);
        }
    }

    @Test
    void superJumpReachesHigherLedges() {
        PlatformSet ps = new PlatformSet(400, 300);
        ps.add(new Ledge("shelf", 150, 250, 240, 300, Ledge.Kind.WIDGET, false));
        Ledge ground = ps.get("ground");
        PlatformGraph.Spec normal = new PlatformGraph.Spec(2, 16, 36, 24, false);
        PlatformGraph.Spec superSpec = new PlatformGraph.Spec(2, 16, 72, 36, false);
        assertNull(PlatformGraph.findPath(ps, ground, 200, ps.get("shelf"), 200, normal, 600), "60 px ist zu hoch für einen normalen Sprung");
        List<PlatformGraph.Step> path = PlatformGraph.findPath(ps, ground, 200, ps.get("shelf"), 200, superSpec, 600);
        assertNotNull(path, "mit Super-Sprung erreichbar");
        // Super-Sprung: doppelte Höhe über dem Start
        float apex = 7 + 16 * 0.35f;
        float rise = 60 + apex;
        float[] v = Physics.jumpVelocity(200, 300, 200, 240, 2 * rise - 60);
        assertEquals(2 * rise, Physics.jumpHeightFor(v[1]), 0.5f);
    }
}
