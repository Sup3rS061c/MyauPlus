package myau.ui.impl.tenacity;


import myau.Myau;
import myau.util.skid.tenacity.RoundedUtil;
import myau.util.skid.tenacity.Tenacity;

import myau.util.ColorUtil;
import myau.util.RenderUtil;

import myau.util.skid.tenacity.Utils;
import myau.util.skid.tenacity.animations.Animation;
import myau.util.skid.tenacity.animations.Direction;

import myau.util.skid.tenacity.animations.impl.DecelerateAnimation;
import myau.util.skid.tenacity.animations.impl.EaseBackIn;
import myau.util.skid.tenacity.font.CustomFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class SplashScreen implements Utils {

    private static int currentProgress;

    // Background texture
    private static ResourceLocation splash;
    private static Framebuffer framebuffer;


    static ResourceLocation image = new ResourceLocation("Tenacity/splashscreen.png");

    private static int count;


    public static void continueCount() {
        continueCount(true);
    }

    public static void continueCount(boolean continueCount) {
        drawSplash();
        if(continueCount){
            count++;
        }
    }

    /**
     * Render the splash screen background
     */
    private static void drawSplash() {

        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        // Create the scale factor
        int scaleFactor = sr.getScaleFactor();
        // Bind the width and height to the framebuffer
        framebuffer = RenderUtil.createFrameBuffer(framebuffer);

        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(true);

        // Create the projected image to be rendered
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, sr.getScaledWidth(), sr.getScaledHeight(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();


        GlStateManager.color(0, 0, 0, 0);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        drawSplashBackground(sr.getScaledWidth(), sr.getScaledHeight(), 1);

        RenderUtil.resetColor();
        GL11.glEnable(GL11.GL_BLEND);
        RenderUtil.setAlphaLimit(0);

        CustomFont fr = tenacityBoldFont80;

        if(count > 3){
            count = 0;
        }
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < count; i++){
            sb.append(".");
        }


        fr.drawCenteredString("Authenticating" + sb, sr.getScaledWidth() / 2f, fr.getMiddleOfBox(sr.getScaledHeight()), -1);
        // Unbind the width and height as it's no longer needed
        framebuffer.unbindFramebuffer();

        // Render the previously used frame buffer
        framebuffer.framebufferRender(sr.getScaledWidth() * scaleFactor, sr.getScaledHeight() * scaleFactor);

        // Update the texture to enable alpha drawing
        RenderUtil.setAlphaLimit(1);

        // Update the users screen
        Minecraft.getMinecraft().updateDisplay();
    }


    private static Animation fadeAnim;
    private static Animation moveAnim;
    private static Animation versionAnim;
    private static Animation progressAnim;
    private static Animation progress2Anim;

    private static void drawScreen(float width, float height) {
        RenderUtil.drawRect(0, 0, width, height, Color.BLACK.getRGB());
        if (fadeAnim == null) {
            fadeAnim = new DecelerateAnimation(600, 1);
            moveAnim = new DecelerateAnimation(600, 1);
        }

        drawSplashBackground(width, height, 1);

        CustomFont fr = tenacityBoldFont80;

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < count; i++){
            sb.append(".");
        }

        fr.drawCenteredString("Authenticating" + sb, width / 2f, fr.getMiddleOfBox(height),
                ColorUtil.applyOpacity(-1, 1 - fadeAnim.getOutput().floatValue()));


        float yMovement = progressAnim != null && progressAnim.getDirection().backwards() ? 1 - progressAnim.getOutput().floatValue() : 0;
        float actualY = (fr.getMiddleOfBox(height) - 40) - (48 * yMovement);
        fr.drawCenteredString("Tenacity", width / 2f, actualY,
                ColorUtil.applyOpacity(-1, moveAnim.getOutput().floatValue()));

        if (moveAnim.isDone() && versionAnim == null) {
            versionAnim = new EaseBackIn(500, 1, 2);
        }


        if (versionAnim != null) {

            float versionWidth = tenacityFont32.getStringWidth(Myau.clientVersion) / 2f;
            float versionX = width / 2f + fr.getStringWidth("Tenacity") / 2f - (versionWidth);
            float versionY = (tenacityFont32.getMiddleOfBox(height) - 57) - (48 * yMovement);

            RenderUtil.scaleStart(versionX + versionWidth, versionY + tenacityFont32.getHeight() / 2f, versionAnim.getOutput().floatValue());

            tenacityFont32.drawSmoothString(Myau.clientVersion, versionX, versionY, ColorUtil.applyOpacity(-1, versionAnim.getOutput().floatValue()));
            RenderUtil.scaleEnd();
        }


        if (progressAnim == null) {
            if (moveAnim.isDone()) {
                progressAnim = new DecelerateAnimation(250, 1);
                progress2Anim = new DecelerateAnimation(1800, 1);
            }
        } else {
            float rectWidth = fr.getStringWidth("Tenacity") + 10;
            float rectHeight = 5;
            float roundX = (width / 2f - rectWidth / 2f);
            float roundY = height / 2f - rectHeight / 2f;
            float roundAlpha = progressAnim.getOutput().floatValue();
            RoundedUtil.drawRound(roundX, height / 2f - rectHeight / 2f, rectWidth, rectHeight,
                    (rectHeight / 2f) - .25f, ColorUtil.tripleColor(50, roundAlpha));

            float progress = progress2Anim.getOutput().floatValue();
            Color color1 = ColorUtil.interpolateColorC(Tenacity.INSTANCE.getClientColor(), Tenacity.INSTANCE.getAlternateClientColor(), progress);
            Color color2 = ColorUtil.interpolateColorC(Tenacity.INSTANCE.getAlternateClientColor(), Tenacity.INSTANCE.getClientColor(), progress);
            RoundedUtil.drawGradientHorizontal(roundX, roundY, rectWidth * progress, rectHeight,
                    (rectHeight / 2f) - .25f, ColorUtil.applyOpacity(color1, roundAlpha), ColorUtil.applyOpacity(color2, roundAlpha));


            float textAlpha = progress2Anim.getDirection().forwards() ? progress2Anim.getOutput().floatValue() : 1;
            tenacityFont18.drawCenteredString("by skidder", width / 2f, actualY + 42, ColorUtil.applyOpacity(-1, textAlpha));


            if (progressAnim.finished(Direction.FORWARDS) && progress2Anim.finished(Direction.FORWARDS)) {
                progressAnim.changeDirection();
            }

        }


    }

    public static void drawScreen() {
        ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
        // Create the scale factor
        int scaleFactor = sr.getScaleFactor();
        // Bind the width and height to the framebuffer
        framebuffer = RenderUtil.createFrameBuffer(framebuffer);


        while (progressAnim == null || !progressAnim.finished(Direction.BACKWARDS)) {
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(true);
            // Create the projected image to be rendered
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.loadIdentity();
            GlStateManager.ortho(0.0D, sr.getScaledWidth(), sr.getScaledHeight(), 0.0D, 1000.0D, 3000.0D);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.loadIdentity();
            GlStateManager.translate(0.0F, 0.0F, -2000.0F);
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.disableDepth();
            GlStateManager.enableTexture2D();


            GlStateManager.color(0, 0, 0, 0);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            drawScreen(sr.getScaledWidth(), sr.getScaledHeight());

            // Unbind the width and height as it's no longer needed
            framebuffer.unbindFramebuffer();

            // Render the previously used frame buffer
            framebuffer.framebufferRender(sr.getScaledWidth() * scaleFactor, sr.getScaledHeight() * scaleFactor);

            // Update the texture to enable alpha drawing
            RenderUtil.setAlphaLimit(1);

            // Update the users screen
            Minecraft.getMinecraft().updateDisplay();
        }
    }


    public static void drawModalRectWithCustomSizedTexture(float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8) {
        float f9 = 1.0f / f7;
        float f10 = 1.0f / f8;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX);
        worldRenderer.pos(f, f2 + f6, 0.0).tex(f3 * f9, (f4 + f6) * f10).endVertex();
        worldRenderer.pos(f + f5, f2 + f6, 0.0).tex((f3 + f5) * f9, (f4 + f6) * f10).endVertex();
        worldRenderer.pos(f + f5, f2, 0.0).tex((f3 + f5) * f9, f4 * f10).endVertex();
        worldRenderer.pos(f, f2, 0.0).tex(f3 * f9, f4 * f10).endVertex();
        tessellator.draw();
    }

    public static void drawSplashBackground(float width, float height, float alpha) {
        RenderUtil.resetColor();
        GlStateManager.color(1, 1, 1, alpha);
        mc.getTextureManager().bindTexture(new ResourceLocation("Tenacity/splashscreen.png"));
        drawModalRectWithCustomSizedTexture(0, 0, 0, 0, width, height, width, height);
    }


}
