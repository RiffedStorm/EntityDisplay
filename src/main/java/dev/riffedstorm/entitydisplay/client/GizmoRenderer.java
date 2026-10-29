package dev.riffedstorm.entitydisplay.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/** Draws selection outlines and the move/rotate/scale gizmo on top of the world. */
public final class GizmoRenderer {
    private static final int[] AXIS_COLORS = {0xFFE5484D, 0xFF46C46A, 0xFF4A80FF};
    private static final int ACTIVE = 0xFFFFD84A;
    private static final int UNIFORM = 0xFFEEEEEE;
    private static final int SELECTED_OUTLINE = 0xFF55E6FF;
    private static final int HOVER_OUTLINE = 0x90FFFFFF;
    private static final int[][] EDGES = {
            {0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    private final BufferBuilder buffer;
    private final Vec3 camera;

    private GizmoRenderer(BufferBuilder buffer, Vec3 camera) {
        this.buffer = buffer;
        this.camera = camera;
    }

    /** Everything here is drawn without depth testing, so the gizmo is never hidden behind blocks. */
    public static void render(Matrix4f viewRotation, Vec3 camera, DisplayTool.View selected, Vec3[] hoveredCorners) {
        if (selected == null && hoveredCorners == null) return;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.mul(viewRotation);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        GizmoRenderer r = new GizmoRenderer(
                Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR), camera);
        if (hoveredCorners != null) r.box(hoveredCorners, HOVER_OUTLINE);
        if (selected != null) {
            r.box(selected.corners(), SELECTED_OUTLINE);
            r.gizmo(selected);
        }
        MeshData mesh = r.buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private void gizmo(DisplayTool.View view) {
        Gizmo.Mode mode = DisplayTool.mode();
        Gizmo.Handle highlight = DisplayTool.highlightedHandle();
        Vec3 c = view.pos();
        double s = Gizmo.size(c, camera);
        Vec3[] axes = Gizmo.axes(mode, view.yRot(), view.xRot(), view.data());

        for (int i = 0; i < 3; i++) {
            int color = highlight != null && highlight.axis() == i ? ACTIVE : AXIS_COLORS[i];
            if (mode == Gizmo.Mode.ROTATE) {
                ring(c, axes[i], s * Gizmo.RING_RADIUS, color);
            } else {
                Vec3 tip = c.add(axes[i].scale(s * Gizmo.ARROW_LENGTH));
                line(c, tip, color);
                cube(tip, s * Gizmo.TIP_HALF, color);
            }
        }
        if (mode == Gizmo.Mode.SCALE) {
            boolean hot = highlight != null && highlight.axis() == Gizmo.UNIFORM;
            cube(c, s * Gizmo.CENTER_HALF, hot ? ACTIVE : UNIFORM);
        } else {
            cube(c, s * 0.03, UNIFORM);
        }
    }

    private void box(Vec3[] corners, int color) {
        for (int[] e : EDGES) line(corners[e[0]], corners[e[1]], color);
    }

    private void ring(Vec3 c, Vec3 n, double radius, int color) {
        Vec3 u = n.cross(Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 v = n.cross(u);
        int segments = 64;
        Vec3 prev = c.add(u.scale(radius));
        for (int i = 1; i <= segments; i++) {
            double a = Math.PI * 2 * i / segments;
            Vec3 next = c.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius));
            line(prev, next, color);
            prev = next;
        }
    }

    /** A line as a camera-facing quad with a roughly constant on-screen thickness. */
    private void line(Vec3 a, Vec3 b, int color) {
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = b.subtract(a).cross(mid.subtract(camera));
        if (side.lengthSqr() < 1e-12) return;
        side = side.normalize().scale(Math.max(0.004, mid.distanceTo(camera) * 0.0025));
        vertex(a.add(side), color);
        vertex(a.subtract(side), color);
        vertex(b.subtract(side), color);
        vertex(b.add(side), color);
    }

    private void cube(Vec3 c, double h, int color) {
        double x0 = c.x - h, y0 = c.y - h, z0 = c.z - h, x1 = c.x + h, y1 = c.y + h, z1 = c.z + h;
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color);
        quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, color);
        quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, color);
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color);
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, color);
        quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, color);
    }

    private void quad(double ax, double ay, double az, double bx, double by, double bz,
                      double cx, double cy, double cz, double dx, double dy, double dz, int color) {
        vertex(new Vec3(ax, ay, az), color);
        vertex(new Vec3(bx, by, bz), color);
        vertex(new Vec3(cx, cy, cz), color);
        vertex(new Vec3(dx, dy, dz), color);
    }

    private void vertex(Vec3 p, int argb) {
        buffer.addVertex((float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z))
                .setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF);
    }
}
