package myau.util;

import org.lwjgl.util.glu.GLU;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

public class GLUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
    private static final FloatBuffer modelview = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer projection = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer vector = org.lwjgl.BufferUtils.createFloatBuffer(4);

    public static float[] project2D(float x, float y, float z, int sr) {
        try {
            // 获取当前的OpenGL矩阵和视口
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

            // 打印调试信息
            System.out.println("GLUtil - Projecting point [" + x + ", " + y + ", " + z + "]");
            System.out.println("GLUtil - Viewport: [" +
                    viewport.get(0) + ", " + viewport.get(1) + ", " +
                    viewport.get(2) + ", " + viewport.get(3) + "]");

            // 重置缓冲区位置
            modelview.rewind();
            projection.rewind();
            viewport.rewind();
            vector.rewind();

            // 使用GLU投影
            boolean result = GLU.gluProject(x, y, z, modelview, projection, viewport, vector);

            if (result) {
                float screenX = vector.get(0);
                float screenY = vector.get(1);
                float depth = vector.get(2);

                // 调整Y坐标（OpenGL坐标系原点是左下角，Minecraft是左上角）
                float adjustedY = viewport.get(3) - screenY;

                // 转换为ScaledResolution坐标
                float scaledX = screenX / sr;
                float scaledY = adjustedY / sr;

                System.out.println("GLUtil - Projection result: [" +
                        screenX + ", " + screenY + ", " + depth +
                        "] -> [" + scaledX + ", " + scaledY + "]");

                return new float[]{scaledX, scaledY, depth};
            } else {
                System.err.println("GLUtil - gluProject failed for point [" + x + ", " + y + ", " + z + "]");
                return null;
            }
        } catch (Exception e) {
            System.err.println("GLUtil - Error in project2D: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // 备用方法：简单的3D到2D投影
    public static float[] simpleProject2D(float x, float y, float z, ScaledResolution sr) {
        try {
            // 简单的投影逻辑
            float screenX = (x + 1.0f) * 0.5f * sr.getScaledWidth();
            float screenY = (1.0f - (y + 1.0f) * 0.5f) * sr.getScaledHeight();

            // 简单的深度计算（距离相机的Z距离）
            float depth = z / 100.0f; // 简化深度计算

            return new float[]{screenX, screenY, depth};
        } catch (Exception e) {
            System.err.println("GLUtil - Error in simpleProject2D: " + e.getMessage());
            return null;
        }
    }
}