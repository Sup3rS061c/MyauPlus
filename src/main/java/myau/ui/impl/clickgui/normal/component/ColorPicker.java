package myau.ui.impl.clickgui.normal.component;

import myau.property.properties.ColorProperty;
import myau.ui.impl.clickgui.normal.MaterialTheme;
import myau.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.input.Mouse;

import java.awt.*;

public class ColorPicker extends Component {
    private final ColorProperty colorProperty;

    private boolean draggingHue;
    private boolean draggingSV;

    private float hue;
    private float saturation;
    private float brightness;

    // 新增：用于检测外部颜色变化的缓存
    private int cachedColor;

    public ColorPicker(ColorProperty colorProperty, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.colorProperty = colorProperty;
        // 初始化时强制同步一次
        this.cachedColor = colorProperty.getValue();
        updateHSB();
    }

    public ColorProperty getProperty() {
        return this.colorProperty;
    }

    /**
     * 将 Property 中的 int 颜色转换为本地 HSB 浮点数
     * 仅在外部修改颜色时调用
     */
    private void updateHSB() {
        int color = colorProperty.getValue();
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    /**
     * 将本地 HSB 计算为 int 并写入 Property
     */
    private void updateColor() {
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        colorProperty.setValue(rgb);
        // 关键：更新缓存，防止下一帧 render 误判为外部修改
        this.cachedColor = rgb;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks, float animationProgress, boolean isLast, int scrollOffset, float deltaTime) {
        if (!colorProperty.isVisible()) return;

        int scrolledY = y - scrollOffset;
        float easedProgress = 1.0f - (float) Math.pow(1.0f - animationProgress, 4);
        if (easedProgress <= 0) return;

        int alpha = (int) (255 * easedProgress);

        // 布局参数
        float padding = 4;
        float pickerX = x + padding;
        float pickerY = scrolledY + padding;
        float pickerW = width - (padding * 2);
        float pickerH = height - (padding * 2);

        float hueHeight = 6;
        float svHeight = pickerH - hueHeight - 4;

        // ==========================================
        // 逻辑更新 (核心修复部分)
        // ==========================================

        // 1. 检查鼠标状态，重置拖拽标记
        if (!Mouse.isButtonDown(0)) {
            draggingSV = false;
            draggingHue = false;
        }

        // 2. 检查外部颜色更新
        // 只有当 当前Property值 != 我们上次写入的值 时，才反向同步 HSB。
        // 这解决了精度丢失导致滑块回弹的问题，也解决了白色/黑色导致 Hue 归零的问题。
        if (!draggingSV && !draggingHue) {
            if (colorProperty.getValue() != cachedColor) {
                cachedColor = colorProperty.getValue();
                updateHSB();
            }
        }

        // 3. 处理拖拽逻辑
        if (draggingSV) {
            float s = (mouseX - pickerX) / pickerW;
            float b = 1.0f - ((mouseY - pickerY) / svHeight);
            saturation = Math.max(0, Math.min(1, s));
            brightness = Math.max(0, Math.min(1, b));
            updateColor(); // 更新并写入缓存
        } else if (draggingHue) {
            float h = (mouseX - pickerX) / pickerW;
            hue = Math.max(0, Math.min(1, h));
            updateColor(); // 更新并写入缓存
        }

        // ==========================================
        // 绘图部分
        // ==========================================

        // 1. SV 面板底色 (纯 Hue 色)
        int hueColor = Color.HSBtoRGB(hue, 1.0f, 1.0f);
        RenderUtil.drawRect(pickerX, pickerY, pickerX + pickerW, pickerY + svHeight, hueColor);

        // 2. 饱和度蒙版 (左白 -> 右透明)
        drawGradientRect(pickerX, pickerY, pickerW, svHeight, 0xFFFFFFFF, 0x00FFFFFF, true);

        // 3. 亮度蒙版 (上透明 -> 下黑)
        drawGradientRect(pickerX, pickerY, pickerW, svHeight, 0x00000000, 0xFF000000, false);

        // 4. SV 指示器
        float indicatorX = pickerX + (saturation * pickerW);
        float indicatorY = pickerY + ((1 - brightness) * svHeight);
        RenderUtil.drawCircleOutline(indicatorX, indicatorY, 3, 2.0f, 0xFF000000);
        RenderUtil.drawCircleOutline(indicatorX, indicatorY, 3, 1.0f, 0xFFFFFFFF);

        // 5. Hue 彩虹条
        float hueY = pickerY + svHeight + 4;
        drawRainbowRect(pickerX, hueY, pickerW, hueHeight);

        // 6. Hue 指示器
        float hueIndicatorX = pickerX + (hue * pickerW);
        RenderUtil.drawRect(hueIndicatorX - 1, hueY, hueIndicatorX + 1, hueY + hueHeight, 0xFFFFFFFF);

        // 7. 外边框
        RenderUtil.drawRectOutline(pickerX - 1, pickerY - 1, pickerW + 2, pickerH + 2, 1.0f, MaterialTheme.getRGBWithAlpha(MaterialTheme.OUTLINE_COLOR, alpha));
    }

    private void drawRainbowRect(float x, float y, float width, float height) {
        int[] colors = {
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        float segmentWidth = width / 6.0f;
        for (int i = 0; i < 6; i++) {
            float currentX = x + (i * segmentWidth);
            drawGradientRect(currentX, y, segmentWidth, height, colors[i], colors[i + 1], true);
        }
    }

    private void drawGradientRect(float x, float y, float width, float height, int startColor, int endColor, boolean horizontal) {
        float f = (float) (startColor >> 24 & 255) / 255.0F;
        float f1 = (float) (startColor >> 16 & 255) / 255.0F;
        float f2 = (float) (startColor >> 8 & 255) / 255.0F;
        float f3 = (float) (startColor & 255) / 255.0F;
        float f4 = (float) (endColor >> 24 & 255) / 255.0F;
        float f5 = (float) (endColor >> 16 & 255) / 255.0F;
        float f6 = (float) (endColor >> 8 & 255) / 255.0F;
        float f7 = (float) (endColor & 255) / 255.0F;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(7, DefaultVertexFormats.POSITION_COLOR);

        if (horizontal) {
            worldrenderer.pos(x + width, y, 0).color(f5, f6, f7, f4).endVertex();
            worldrenderer.pos(x, y, 0).color(f1, f2, f3, f).endVertex();
            worldrenderer.pos(x, y + height, 0).color(f1, f2, f3, f).endVertex();
            worldrenderer.pos(x + width, y + height, 0).color(f5, f6, f7, f4).endVertex();
        } else {
            worldrenderer.pos(x + width, y, 0).color(f1, f2, f3, f).endVertex();
            worldrenderer.pos(x, y, 0).color(f1, f2, f3, f).endVertex();
            worldrenderer.pos(x, y + height, 0).color(f5, f6, f7, f4).endVertex();
            worldrenderer.pos(x + width, y + height, 0).color(f5, f6, f7, f4).endVertex();
        }

        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        return false;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, int scrollOffset) {
        if (mouseButton != 0) return false;

        int scrolledY = y - scrollOffset;
        float padding = 4;
        float pickerX = x + padding;
        float pickerY = scrolledY + padding;
        float pickerW = width - (padding * 2);
        float pickerH = height - (padding * 2);
        float hueHeight = 6;
        float svHeight = pickerH - hueHeight - 4;

        // 1. 点击 SV 面板
        if (mouseX >= pickerX && mouseX <= pickerX + pickerW && mouseY >= pickerY && mouseY <= pickerY + svHeight) {
            draggingSV = true;
            float s = (mouseX - pickerX) / pickerW;
            float b = 1.0f - ((mouseY - pickerY) / svHeight);
            saturation = Math.max(0, Math.min(1, s));
            brightness = Math.max(0, Math.min(1, b));
            updateColor();
            return true;
        }

        // 2. 点击 Hue 条
        float hueY = pickerY + svHeight + 4;
        if (mouseX >= pickerX && mouseX <= pickerX + pickerW && mouseY >= hueY && mouseY <= hueY + hueHeight) {
            draggingHue = true;
            float h = (mouseX - pickerX) / pickerW;
            hue = Math.max(0, Math.min(1, h));
            updateColor();
            return true;
        }

        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int mouseButton, int scrollOffset) {
        draggingSV = false;
        draggingHue = false;
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
    }
}