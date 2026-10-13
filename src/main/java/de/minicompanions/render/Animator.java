package de.minicompanions.render;

import de.minicompanions.ai.Activity;
import de.minicompanions.figure.Figure;
import de.minicompanions.figure.Pose;

/**
 * Berechnet aus Zustand und Zeit die Pose einer Figur.
 * Konvention: Arm-/Beinwinkel negativ = nach oben/außen bzw. nach vorne.
 */
public final class Animator {
    private Animator() {
    }

    public static void animate(Figure f, Pose p) {
        float t = f.animTime;
        float at = f.activityTime;
        Pose.View side = f.facing < 0 ? Pose.View.LEFT : Pose.View.RIGHT;
        float speedK = Math.max(0.6f, Math.min(2f, Math.abs(f.body.vx) / 24f));
        switch (f.activity) {
            case WALK -> {
                p.reset(side);
                float s = (float) Math.sin(t * 9 * speedK);
                p.legR = s * 0.6f;
                p.legL = -s * 0.6f;
                p.armR = -s * 0.5f;
                p.armL = s * 0.5f;
                p.bobY = -Math.abs((float) Math.cos(t * 9 * speedK)) * 0.6f;
            }
            case RUN -> {
                p.reset(side);
                float s = (float) Math.sin(t * 14);
                p.legR = s * 0.95f;
                p.legL = -s * 0.95f;
                p.armR = -s * 0.9f;
                p.armL = s * 0.9f;
                p.body = 0.18f;
                p.bobY = -Math.abs((float) Math.cos(t * 14)) * 1.1f;
            }
            case JUMP -> {
                p.reset(Math.abs(f.body.vx) > 4 ? side : Pose.View.FRONT);
                p.legR = 0.5f;
                p.legL = -0.3f;
                p.armR = -2.3f;
                p.armL = -2.3f;
                p.squash = 1.04f;
            }
            case FALL -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.5f + (float) Math.sin(t * 22) * 0.35f;
                p.armL = -2.5f - (float) Math.sin(t * 22) * 0.35f;
                p.legR = (float) Math.sin(t * 18) * 0.3f;
                p.legL = -p.legR;
            }
            case CLIMB -> {
                p.reset(Pose.View.BACK);
                float s = (float) Math.sin(t * 8);
                p.armR = -2.8f + s * 0.35f;
                p.armL = -2.8f - s * 0.35f;
                p.legR = s * 0.35f;
                p.legL = -s * 0.35f;
            }
            case HANG -> {
                p.reset(f.body.climbing ? Pose.View.BACK : Pose.View.FRONT);
                p.armR = -3.0f;
                p.armL = -3.0f;
                float s = (float) Math.sin(t * 3);
                p.legR = s * 0.25f;
                p.legL = -s * 0.25f;
                if (!f.body.climbing) {
                    p.bobY = 26;
                }
            }
            case SIT -> {
                p.reset(Pose.View.FRONT);
                p.legScale = 0.35f;
                p.bobY = 7.8f;
                p.armR = 0.15f;
                p.armL = 0.15f;
                p.head = (float) Math.sin(t * 0.8) * 0.06f;
                p.eyesClosed = (t % 4.5f) < 0.12f;
            }
            case SIT_EDGE -> {
                p.reset(Pose.View.FRONT);
                p.bobY = 12;
                float s = (float) Math.sin(t * 3);
                p.legR = s * 0.45f;
                p.legL = -s * 0.45f;
                p.armR = 0.25f;
                p.armL = 0.25f;
                p.eyesClosed = (t % 5f) < 0.12f;
            }
            case SLEEP -> {
                p.reset(Pose.View.FRONT);
                p.roll = (float) (Math.PI / 2);
                p.eyesClosed = true;
                p.armR = 0.1f;
                p.armL = 0.1f;
                p.bobY = (float) Math.sin(t * 1.5) * 0.2f;
            }
            case DANCE -> {
                p.reset(Pose.View.FRONT);
                float s = (float) Math.sin(t * 8);
                p.armR = -2.4f + s * 0.8f;
                p.armL = -2.4f - s * 0.8f;
                p.legR = s * 0.3f;
                p.legL = s * 0.3f;
                p.head = s * 0.2f;
                p.body = s * 0.12f;
                p.bobY = -Math.abs((float) Math.sin(t * 8)) * 1.5f;
            }
            case WAVE -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.6f + (float) Math.sin(t * 12) * 0.45f;
                p.armL = 0.1f;
                p.head = 0.08f;
            }
            case CHEER, PLAY -> {
                p.reset(Pose.View.FRONT);
                float s = (float) Math.sin(t * 10);
                p.armR = -2.8f + s * 0.3f;
                p.armL = -2.8f - s * 0.3f;
                p.bobY = -Math.abs((float) Math.sin(t * 10)) * 2f;
            }
            case SCARED -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.1f;
                p.armL = -2.1f;
                p.shiftX = (float) Math.sin(t * 40) * 0.5f;
                p.squash = 0.95f;
            }
            case SAD -> {
                p.reset(Pose.View.FRONT);
                p.headY = 0.6f;
                p.head = 0.12f;
                p.armR = 0.05f;
                p.armL = 0.05f;
                p.bobY = 0.6f;
            }
            case EAT -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.0f + (float) Math.sin(t * 10) * 0.25f;
                p.armL = 0.1f;
                p.headY = (float) Math.abs(Math.sin(t * 10)) * 0.4f;
            }
            case TALK -> {
                p.reset(f.facing == 0 ? Pose.View.FRONT : side);
                p.head = (float) Math.sin(t * 6) * 0.08f;
                p.armR = -0.4f + (float) Math.sin(t * 3) * 0.4f;
                p.armL = 0.1f;
            }
            case FIGHT -> {
                p.reset(side);
                float s = (float) Math.sin(t * 16);
                p.armR = -1.5f + s * 0.9f;
                p.armL = -1.5f - s * 0.9f;
                p.legR = 0.3f;
                p.legL = -0.3f;
                p.shiftX = s * 0.7f;
            }
            case HUG -> {
                p.reset(side);
                p.armR = -1.4f;
                p.armL = -1.4f;
                p.shiftX = 1.2f;
                p.eyesClosed = true;
            }
            case CARRIED -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.9f + (float) Math.sin(t * 9) * 0.2f;
                p.armL = -2.9f - (float) Math.sin(t * 9) * 0.2f;
                p.legR = (float) Math.sin(t * 7) * 0.4f;
                p.legL = -p.legR;
            }
            case THROWN -> {
                p.reset(Pose.View.FRONT);
                p.roll = f.throwSpin;
                p.armR = -1.5f;
                p.armL = -1.5f;
                p.legR = 0.4f;
                p.legL = -0.4f;
            }
            case PETTED -> {
                p.reset(Pose.View.FRONT);
                p.eyesClosed = true;
                p.head = (float) Math.sin(t * 3) * 0.15f;
                p.bobY = (float) Math.sin(t * 6) * 0.3f;
                p.armR = 0.2f;
                p.armL = 0.2f;
            }
            case TICKLED -> {
                p.reset(Pose.View.FRONT);
                float s = (float) Math.sin(t * 25);
                p.armR = -1.5f + s * 0.8f;
                p.armL = -1.5f - s * 0.8f;
                p.shiftX = s * 0.6f;
                p.squash = 0.96f + Math.abs(s) * 0.04f;
                p.eyesClosed = true;
            }
            case WORK -> {
                p.reset(f.facing == 0 ? Pose.View.FRONT : side);
                p.armR = -1.6f;
                p.armL = -1.6f;
            }
            case HIDE -> {
                p.reset(Pose.View.FRONT);
                p.squash = 0.6f;
                p.armR = -2.2f;
                p.armL = -2.2f;
            }
            case POSE -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.4f;
                p.armL = 0.5f;
                p.head = 0.1f;
            }
            case SPAWN -> {
                p.reset(Pose.View.FRONT);
                p.squash = Math.min(1f, at / Math.max(0.1f, f.activityDuration));
                p.armR = -2.5f;
                p.armL = -2.5f;
            }
            case BOOST -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.95f;
                p.armL = -2.95f;
                p.legR = 0.2f;
                p.legL = -0.2f;
            }
            case RIDE -> {
                p.reset(Pose.View.FRONT);
                p.legScale = 0.35f;
                p.bobY = f.minecart ? 4f : 7.8f;
                p.armR = -2.5f + (float) Math.sin(t * 6) * 0.3f;
                p.armL = 0.2f;
            }
            case YAWN -> {
                p.reset(Pose.View.FRONT);
                p.armR = -2.8f;
                p.armL = -2.8f;
                p.eyesClosed = true;
                p.head = -0.1f;
            }
            case LOOK -> {
                p.reset(f.facing == 0 ? Pose.View.FRONT : side);
                p.head = (float) Math.sin(t * 1.3) * 0.12f;
            }
            default -> {
                p.reset(Pose.View.FRONT);
                float s = (float) Math.sin(t * 2);
                p.bobY = s * 0.3f;
                p.armR = (float) Math.sin(t * 1.5) * 0.05f + 0.05f;
                p.armL = p.armR;
                p.head = f.facing * 0.08f;
                p.eyesClosed = (t % 4f) < 0.12f;
            }
        }
        if (f.activity == Activity.SPAWN && p.squash < 0.05f) {
            p.squash = 0.05f;
        }
    }
}
