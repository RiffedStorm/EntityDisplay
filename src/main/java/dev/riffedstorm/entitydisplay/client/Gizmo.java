package dev.riffedstorm.entitydisplay.client;

import com.mojang.math.Transformation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Gizmo geometry, handle picking and drag math. Pure math: no rendering, no networking. */
public final class Gizmo {
    public enum Mode {
        MOVE, ROTATE, SCALE;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** A grabbable part of the gizmo. {@code axis} is 0..2, or 3 for the uniform-scale center cube. */
    public record Handle(Mode mode, int axis) {}

    public static final int UNIFORM = 3;
    private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

    // Handle proportions, relative to the gizmo size
    static final double ARROW_LENGTH = 1.0;
    static final double TIP_HALF = 0.06;
    static final double CENTER_HALF = 0.08;
    static final double RING_RADIUS = 0.9;
    private static final double PICK_TOLERANCE = 0.09;

    private Gizmo() {}

    /** World size of the gizmo, so it stays about the same size on screen. */
    public static double size(Vec3 center, Vec3 camera) {
        return Math.max(0.4, center.distanceTo(camera) * 0.15);
    }

    /** Move and rotate use world axes; scale uses the display's own (rotated) axes. */
    public static Vec3[] axes(Mode mode, float yRot, float xRot, CompoundTag data) {
        if (mode != Mode.SCALE) return WORLD_AXES;
        Quaternionf q = DisplayData.entityRotation(yRot, xRot)
                .mul(new Quaternionf(DisplayData.transformation(data).getLeftRotation()));
        Vec3[] out = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            Vector3f v = q.transform(new Vector3f(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0));
            out[i] = new Vec3(v.x, v.y, v.z).normalize();
        }
        return out;
    }

    /** The handle under the ray (closest to the camera), or null. */
    public static Handle pick(Mode mode, Vec3 center, double size, Vec3[] axes, Vec3 origin, Vec3 dir) {
        Handle best = null;
        double bestT = Double.MAX_VALUE;
        double tolerance = size * PICK_TOLERANCE;
        for (int i = 0; i < 3; i++) {
            if (mode == Mode.ROTATE) {
                Vec3 hit = rayPlane(origin, dir, center, axes[i]);
                if (hit == null) continue;
                double t = hit.distanceTo(origin);
                if (Math.abs(hit.distanceTo(center) - size * RING_RADIUS) < tolerance && t < bestT) {
                    bestT = t;
                    best = new Handle(mode, i);
                }
            } else {
                double[] r = raySegment(origin, dir, center, axes[i], size * (ARROW_LENGTH + TIP_HALF));
                if (r[0] < tolerance && r[1] < bestT) {
                    bestT = r[1];
                    best = new Handle(mode, i);
                }
            }
        }
        if (mode == Mode.SCALE) {
            Vec3 toCenter = center.subtract(origin);
            double t = toCenter.dot(dir);
            if (t > 0 && origin.add(dir.scale(t)).distanceTo(center) < size * CENTER_HALF * 1.5 && t <= bestT) {
                best = new Handle(mode, UNIFORM);
            }
        }
        return best;
    }

    /** {distance, rayParam, segmentParam} for the closest points between a ray and a segment [c, c + u*length]. */
    static double[] raySegment(Vec3 o, Vec3 d, Vec3 c, Vec3 u, double length) {
        Vec3 w0 = o.subtract(c);
        double b = d.dot(u), dd = d.dot(w0), e = u.dot(w0);
        double denom = 1 - b * b;
        double seg = denom < 1e-6 ? 0 : (e - b * dd) / denom;
        seg = Math.max(0, Math.min(length, seg));
        double ray = Math.max(0, seg * b - dd);
        double dist = o.add(d.scale(ray)).distanceTo(c.add(u.scale(seg)));
        return new double[]{dist, ray, seg};
    }

    /** Parameter along the infinite line c + u*t closest to the ray, or NaN when they are parallel. */
    static double lineParam(Vec3 o, Vec3 d, Vec3 c, Vec3 u) {
        Vec3 w0 = o.subtract(c);
        double b = d.dot(u);
        double denom = 1 - b * b;
        if (denom < 1e-6) return Double.NaN;
        return (u.dot(w0) - b * d.dot(w0)) / denom;
    }

    static Vec3 rayPlane(Vec3 o, Vec3 d, Vec3 c, Vec3 n) {
        double denom = d.dot(n);
        if (Math.abs(denom) < 1e-6) return null;
        double t = c.subtract(o).dot(n) / denom;
        return t < 0 ? null : o.add(d.scale(t));
    }

    private static double snap(double value, double step) {
        return Math.round(value / step) * step;
    }

    /** One mouse drag on one handle. Produces the new position / display NBT for the selected display. */
    public static final class Drag {
        public final Handle handle;
        public final int entityId;
        public final Vec3 startPos;
        public final float yRot, xRot;
        public final CompoundTag startData;
        private final Transformation startT;
        private final Vec3 axis;
        private final double size;
        private final double startParam;
        private final Vec3 startVec;

        public Vec3 pos;
        public CompoundTag data;
        public boolean dirty;

        private Drag(Handle handle, int entityId, Vec3 startPos, float yRot, float xRot, CompoundTag startData,
                     Vec3 axis, double size, double startParam, Vec3 startVec) {
            this.handle = handle;
            this.entityId = entityId;
            this.startPos = startPos;
            this.yRot = yRot;
            this.xRot = xRot;
            this.startData = startData;
            this.startT = DisplayData.transformation(startData);
            this.axis = axis;
            this.size = size;
            this.startParam = startParam;
            this.startVec = startVec;
            this.pos = startPos;
            this.data = startData.copy();
        }

        /** Starts a drag, or returns null if the handle can't be grabbed from this angle. */
        public static Drag begin(Handle handle, int entityId, Vec3 pos, float yRot, float xRot, CompoundTag data,
                                 Vec3 origin, Vec3 dir, Vec3 cameraUp) {
            double size = size(pos, origin);
            Vec3 axis = handle.axis() == UNIFORM ? cameraUp : axes(handle.mode(), yRot, xRot, data)[handle.axis()];
            if (handle.mode() == Mode.ROTATE) {
                Vec3 hit = rayPlane(origin, dir, pos, axis);
                if (hit == null || hit.distanceToSqr(pos) < 1e-8) return null;
                return new Drag(handle, entityId, pos, yRot, xRot, data, axis, size, 0, hit.subtract(pos).normalize());
            }
            double t = lineParam(origin, dir, pos, axis);
            if (Double.isNaN(t)) return null;
            return new Drag(handle, entityId, pos, yRot, xRot, data, axis, size, t, null);
        }

        public void update(Vec3 origin, Vec3 dir, boolean snap) {
            switch (handle.mode()) {
                case MOVE -> updateMove(origin, dir, snap);
                case ROTATE -> updateRotate(origin, dir, snap);
                case SCALE -> updateScale(origin, dir, snap);
            }
        }

        private void updateMove(Vec3 origin, Vec3 dir, boolean snap) {
            double t = lineParam(origin, dir, startPos, axis);
            if (Double.isNaN(t)) return;
            Vec3 p = startPos.add(axis.scale(t - startParam));
            if (snap) {
                double step = 1 / 16.0;
                p = switch (handle.axis()) {
                    case 0 -> new Vec3(snap(p.x, step), p.y, p.z);
                    case 1 -> new Vec3(p.x, snap(p.y, step), p.z);
                    default -> new Vec3(p.x, p.y, snap(p.z, step));
                };
            }
            if (!p.equals(pos)) {
                pos = p;
                dirty = true;
            }
        }

        private void updateRotate(Vec3 origin, Vec3 dir, boolean snap) {
            Vec3 hit = rayPlane(origin, dir, startPos, axis);
            if (hit == null) return;
            Vec3 v = hit.subtract(startPos);
            double angle = Math.atan2(axis.dot(startVec.cross(v)), startVec.dot(v));
            if (snap) angle = snap(angle, Math.toRadians(15));
            Quaternionf world = new Quaternionf().rotationAxis((float) angle, (float) axis.x, (float) axis.y, (float) axis.z);
            // Rotate in world space around the entity origin: local = Re^-1 * world * Re
            Quaternionf re = DisplayData.entityRotation(yRot, xRot);
            Quaternionf local = new Quaternionf(re).conjugate().mul(world).mul(re);
            Quaternionf left = new Quaternionf(local).mul(new Quaternionf(startT.getLeftRotation())).normalize();
            Vector3f translation = local.transform(new Vector3f(startT.getTranslation()));
            setTransformation(new Transformation(translation, left, new Vector3f(startT.getScale()),
                    new Quaternionf(startT.getRightRotation())));
        }

        private void updateScale(Vec3 origin, Vec3 dir, boolean snap) {
            double t = lineParam(origin, dir, startPos, axis);
            if (Double.isNaN(t)) return;
            Vector3f s0 = new Vector3f(startT.getScale());
            Vector3f k = new Vector3f(1, 1, 1);
            if (handle.axis() == UNIFORM) {
                double ratio = Math.max(0.02, 1 + (t - startParam) / size);
                if (snap) ratio = Math.max(1 / 16.0, snap(ratio, 1 / 16.0));
                k.set((float) ratio);
            } else {
                if (Math.abs(startParam) < 1e-4) return;
                int i = handle.axis();
                float start = s0.get(i);
                if (Math.abs(start) < 1e-6) return;
                double scaled = Math.max(1 / 64.0, start * (t / startParam));
                if (snap) scaled = Math.max(1 / 64.0, snap(scaled, 1 / 16.0));
                k.setComponent(i, (float) (scaled / start));
            }
            // Scale around the entity origin inside the left-rotation frame, so the pivot stays put.
            Quaternionf left = new Quaternionf(startT.getLeftRotation());
            Vector3f translation = new Quaternionf(left).conjugate().transform(new Vector3f(startT.getTranslation()));
            left.transform(translation.mul(k));
            setTransformation(new Transformation(translation, left, s0.mul(k), new Quaternionf(startT.getRightRotation())));
        }

        private void setTransformation(Transformation t) {
            CompoundTag next = startData.copy();
            DisplayData.setTransformation(next, t);
            if (!next.equals(data)) {
                data = next;
                dirty = true;
            }
        }
    }
}
