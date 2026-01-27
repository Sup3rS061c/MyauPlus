package myau.module.modules;

import myau.Myau;
import myau.events.*;
import myau.module.Category;
import myau.util.animation.DecelerateAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.module.Module;
import myau.property.properties.*;
import myau.util.*;
import myau.mixin.IAccessorEntityRenderer;
import myau.mixin.IAccessorRenderManager;
import myau.mixin.IAccessorMinecraft;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class TargetESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ModeProperty mode = new ModeProperty("Mark Mode", 1, new String[]{
            "Points", "Ghost", "Ghost2", "Image", "Exhi", "Circle", "Scan", "Helix", "Orbit", "Cage", "Star", "Rings",
            // 新增模式
            "Tornado", "Atom", "Sphere", "Heart", "DNA", "Crystal", "Ripple", "Lotus", "Crown", "Flame"
    });
    private final ModeProperty imageMode = new ModeProperty("Image Mode", 0, new String[]{"Rectangle", "QuadStapple", "TriangleStapple", "TriangleStipple", "Aim","Custom"}, () -> mode.getValue() == 3);
    private final BooleanProperty animation = new BooleanProperty("Animation", true, () -> mode.getValue() == 3 && imageMode.getValue() == 5);
    private final BooleanProperty selectImage = new BooleanProperty("Select Image", false, () -> mode.getValue() == 3 && imageMode.getValue() == 5) {
        @Override
        public boolean setValue(Object value) {
            boolean result = super.setValue(value);
            if (result && (Boolean)value) {
                selectCustomImage();
                super.setValue(false);
            }
            return result;
        }
    };
    private final FloatProperty circleSpeed = new FloatProperty("CircleSpeed", 2.0F, 1.0F, 5.0F, () -> mode.getValue() == 5);
    private final BooleanProperty onlyPlayer = new BooleanProperty("OnlyPlayer", false);
    private final BooleanProperty showHurt = new BooleanProperty("ShowHurt", false, () -> mode.getValue() == 3);
    private final BooleanProperty checkVisible = new BooleanProperty("CheckVisible", true);

    private ResourceLocation customImage = null;
    private long lastHurtTime = 0;
    private static final long HURT_DURATION = 500;

    private EntityLivingBase target;
    private final TimerUtil displayTimer = new TimerUtil();
    private final TimerUtil animTimer = new TimerUtil();
    private long lastTime = System.currentTimeMillis();
    private boolean hasFullyFadedIn = false;
    private final Animation alphaAnim = new DecelerateAnimation(400, 1);
    private final ResourceLocation glowCircle = new ResourceLocation("minecraft", "myau/image/glow_circle.png");
    private final ResourceLocation rectangle = new ResourceLocation("minecraft", "myau/image/rectangle.png");
    private final ResourceLocation quadstapple = new ResourceLocation("minecraft", "myau/image/quadstapple.png");
    private final ResourceLocation trianglestapple = new ResourceLocation("minecraft", "myau/image/trianglestapple.png");
    private final ResourceLocation trianglestipple = new ResourceLocation("minecraft", "myau/image/trianglestipple.png");
    private final ResourceLocation aim = new ResourceLocation("minecraft", "myau/image/shenmi.png");
    public double prevCircleStep;
    public double circleStep;

    public TargetESP() {
        super("TargetESP", "Show ESP when u r targeting", Category.RENDER, 0 ,false,false);
    }

    private void selectCustomImage() {
        new Thread(() -> {
            FileDialog fileDialog = new FileDialog((Frame)null, "Select Custom Image", FileDialog.LOAD);
            fileDialog.setFile("*.png");
            fileDialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
            fileDialog.setVisible(true);

            String file = fileDialog.getFile();
            if (file != null) {
                String directory = fileDialog.getDirectory();
                File imageFile = new File(directory, file);
                try {
                    BufferedImage image = ImageIO.read(imageFile);
                    if (image != null) {
                        ResourceLocation newImage = new ResourceLocation("myau", "custom_target_" + System.currentTimeMillis());
                        mc.addScheduledTask(() -> {
                            mc.getTextureManager().loadTexture(newImage, new DynamicTexture(image));
                            customImage = newImage;
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "Image Selector Thread").start();
    }

    @Override
    public void onEnabled() {
        target = null;
        alphaAnim.reset();
        displayTimer.reset();
        animTimer.reset();
        lastTime = System.currentTimeMillis();
        hasFullyFadedIn = false;
        prevCircleStep = 0;
        circleStep = 0;
    }

    @Override
    public void onDisabled() {
        target = null;
        alphaAnim.reset();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                Entity entity = packet.getEntityFromWorld(mc.theWorld);
                if (entity == target) {
                    lastHurtTime = System.currentTimeMillis();
                }
            }
            if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) {
                return;
            }
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase &&
                    (!onlyPlayer.getValue() || entity instanceof EntityPlayer)) {
                EntityLivingBase newTarget = (EntityLivingBase) entity;
                if (target != newTarget) {
                    target = newTarget;
                    lastTime = System.currentTimeMillis();
                    alphaAnim.reset();
                    alphaAnim.setDirection(Animation.Direction.FORWARDS);
                    animTimer.reset();
                    hasFullyFadedIn = false;
                }
                displayTimer.reset();
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event){
        if(target != null && displayTimer.hasTimeElapsed(1000)){
            hasFullyFadedIn = false;
            target = null;
        }
    }

    private float getHurtAlpha() {
        if (!showHurt.getValue()) return 0.0f;

        long timeSinceHurt = System.currentTimeMillis() - lastHurtTime;
        if (timeSinceHurt > HURT_DURATION) return 0.0f;

        float progress = (float) timeSinceHurt / HURT_DURATION;
        if (progress < 0.5f) {
            return progress * 2.0f;
        } else {
            return 2.0f - (progress * 2.0f);
        }
    }

    private float getAlpha() {
        if (target == null) return 0.0f;

        long animElapsed = animTimer.getElapsedTime();
        long displayElapsed = displayTimer.getElapsedTime();

        if (!hasFullyFadedIn) {
            if (animElapsed < 200) {
                return animElapsed / 200.0f;
            } else {
                hasFullyFadedIn = true;
                return 1.0f;
            }
        } else {
            if (displayElapsed > 800) {
                return Math.max(0.0f, (1000 - displayElapsed) / 200.0f);
            } else {
                return 1.0f;
            }
        }
    }

    private boolean isEntityVisible(EntityLivingBase entity) {
        if (!checkVisible.getValue()) return true;
        return mc.thePlayer.canEntityBeSeen(entity);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if(!this.isEnabled() || target == null) return;
        if (!isEntityVisible(target)) return;

        switch (mode.getValue()) {
            case 0: renderPoints(event); break;
            case 1: renderGhost(event); break;
            case 2: renderGhost2(event); break;
            case 3: break; // Image mode uses 2D
            case 4: renderExhi(event); break;
            case 5: renderCircle(event); break;
            case 6: renderScan(event); break;
            case 7: renderHelix(event); break;
            case 8: renderOrbit(event); break;
            case 9: renderCage(event); break;
            case 10: renderStar(event); break;
            case 11: renderRings(event); break;

            // 新增 Case
            case 12: renderTornado(event); break;
            case 13: renderAtom(event); break;
            case 14: renderSphere(event); break;
            case 15: renderHeart(event); break;
            case 16: renderDNA(event); break;
            case 17: renderCrystal(event); break;
            case 18: renderRipple(event); break;
            case 19: renderLotus(event); break;
            case 20: renderCrown(event); break;
            case 21: renderFlame(event); break;
        }
    }

    private void renderPoints(Render3DEvent event) {
        if (target == null) return;

        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        double markerX = MathUtil.interporate(event.getPartialTicks(), target.lastTickPosX, target.posX) - renderPosX;
        double markerY = MathUtil.interporate(event.getPartialTicks(), target.lastTickPosY, target.posY) + target.height / 1.6f - renderPosY;
        double markerZ = MathUtil.interporate(event.getPartialTicks(), target.lastTickPosZ, target.posZ) - renderPosZ;

        float time = (float) ((((System.currentTimeMillis() - lastTime) / 1500F)) + (Math.sin((((System.currentTimeMillis() - lastTime) / 1500F))) / 10f));
        float pl = 0;
        boolean fa = false;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);

        for (int iteration = 0; iteration < 3; iteration++) {
            for (float i = time * 360; i < time * 360 + 90; i += 2) {
                float max = time * 360 + 90;
                float dc = MathUtil.normalize(i, time * 360 - 45, max);
                float rf = 0.6f;
                double radians = Math.toRadians(i);
                double plY = pl + Math.sin(radians * 1.2f) * 0.1f;

                int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();

                GlStateManager.pushMatrix();
                RenderUtil.setupOrientationMatrix(markerX, markerY, markerZ);

                float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
                GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
                GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);

                float q = (!fa ? 0.25f : 0.15f) * (Math.max(fa ? 0.25f : 0.15f, fa ? dc : (1f + (0.4f - dc)) / 2f) + 0.45f);
                float size = q * 2f;

                RenderUtil.drawImage(
                        glowCircle,
                        (float) (Math.cos(radians) * rf - size / 2f),
                        (float) (plY - 0.7),
                        size, size,
                        color);

                GlStateManager.popMatrix();
            }
            time *= -1.025f;
            fa = !fa;
            pl += 0.45f;
        }

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderGhost(Render3DEvent event) {
        if (target == null) return;

        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);

        double radius = 0.67;
        float speed = 45;
        float size = 0.4f;
        double distance = 19;
        int length = 20;

        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());

        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + 0.75f - renderPosY;
        double z = interpolated.zCoord - renderPosZ;

        RenderUtil.setupOrientationMatrix(x, y + 0.5f, z);

        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);

        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();

        for (int i = 0; i < length; i++) {
            double angle = 0.15f * (System.currentTimeMillis() - lastTime - (i * distance)) / speed;
            double s = Math.sin(angle) * radius;
            double c = Math.cos(angle) * radius;

            GlStateManager.pushMatrix();
            GlStateManager.translate(s, c, -c);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(-s, s, -c);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();

            GlStateManager.pushMatrix();
            GlStateManager.translate(-s, -s, c);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderGhost2(Render3DEvent event) {
        if (target == null) return;

        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        float partialTicks = event.getPartialTicks();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), partialTicks);

        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + 0.9f - renderPosY;
        double z = interpolated.zCoord - renderPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);

        RenderUtil.setupOrientationMatrix(x, y, z);

        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);

        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();

        for (int gi = 0; gi < 48; gi++) {
            double timeFactor = (System.currentTimeMillis() - lastTime) / 1000.0;
            double angle = (gi / 48.0) * Math.PI * 2.0 + timeFactor * 1.2;

            double dx = Math.cos(angle) * (target.width * 0.8 + 0.3);
            double dy = Math.sin(timeFactor * 2.0 + gi * 0.1) * 0.1;
            double dz = Math.sin(angle) * (target.width * 0.8 + 0.3);

            GlStateManager.pushMatrix();
            GlStateManager.translate(dx, dy, dz);
            GlStateManager.rotate((float)(timeFactor * 180.0 + gi * 12.0), 0, 0, 1);

            float particleSize = 0.16f + 0.06f * (float)Math.sin(timeFactor * 2.6 + gi * 0.45);
            RenderUtil.drawImage(glowCircle, -particleSize/2, -particleSize/2, particleSize, particleSize, color);

            GlStateManager.popMatrix();
        }

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderScan(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());

        double height = target.height;
        double offset = (Math.sin(System.currentTimeMillis() / 300.0) + 1) / 2.0 * height;

        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + offset - renderPosY;
        double z = interpolated.zCoord - renderPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();

        Color c = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        worldrenderer.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        worldrenderer.pos(0, 0, 0).color(r, g, b, 0.4f).endVertex();

        float radius = 0.6f;
        for(int i = 0; i <= 360; i+=10) {
            double angle = Math.toRadians(i);
            worldrenderer.pos(Math.sin(angle) * radius, 0, Math.cos(angle) * radius).color(r, g, b, 0.0f).endVertex();
        }
        tessellator.draw();

        GL11.glLineWidth(1.5f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glColor4f(r, g, b, 1.0f);
        for (int i = 0; i <= 360; i+=10) {
            double angle = Math.toRadians(i);
            GL11.glVertex3d(Math.sin(angle) * radius, 0, Math.cos(angle) * radius);
        }
        GL11.glEnd();

        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void renderHelix(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());
        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord - renderPosY;
        double z = interpolated.zCoord - renderPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
        RenderUtil.setupOrientationMatrix(x, y, z);
        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        double radius = 0.7;
        float size = 0.25f;
        for (int i = 0; i < 40; i++) {
            double h = i * 0.05;
            double angle = (System.currentTimeMillis() % 2000) / 2000.0 * Math.PI * 2 + i * 0.2;
            double px = Math.cos(angle) * radius;
            double pz = Math.sin(angle) * radius;
            GlStateManager.pushMatrix();
            GlStateManager.translate(px, h, pz);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.translate(-px, h, -pz);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderOrbit(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());
        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + target.height / 2.0 - renderPosY;
        double z = interpolated.zCoord - renderPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
        RenderUtil.setupOrientationMatrix(x, y, z);
        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        double radius = 1.2;
        float size = 0.3f;
        double time = System.currentTimeMillis() / 10.0;
        for (int i = 0; i < 3; i++) {
            GlStateManager.pushMatrix();
            GL11.glRotated(i * 120, 0, 1, 0);
            GL11.glRotated(30, 0, 0, 1);
            for (int j = 0; j < 10; j++) {
                double angle = Math.toRadians(time + j * 36);
                double px = Math.cos(angle) * radius;
                double pz = Math.sin(angle) * radius;
                GlStateManager.pushMatrix();
                GlStateManager.translate(px, 0, pz);
                RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderCage(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());
        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord - renderPosY;
        double z = interpolated.zCoord - renderPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
        RenderUtil.setupOrientationMatrix(x, y, z);
        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        double radius = 1.0;
        float size = 0.25f;
        double time = System.currentTimeMillis() / 5.0;
        for (int i = 0; i < 4; i++) {
            double angle = Math.toRadians(time + i * 90);
            double px = Math.cos(angle) * radius;
            double pz = Math.sin(angle) * radius;
            for(int h=0; h<10; h++){
                GlStateManager.pushMatrix();
                GlStateManager.translate(px, h * 0.2, pz);
                RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderStar(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());
        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord + target.height + 0.5 - renderPosY;
        double z = interpolated.zCoord - renderPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
        RenderUtil.setupOrientationMatrix(x, y, z);
        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        double radius = 0.6;
        float size = 0.3f;
        double time = System.currentTimeMillis() / 2.0;
        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(time + i * 72);
            double px = Math.cos(angle) * radius;
            double pz = Math.sin(angle) * radius;
            GlStateManager.pushMatrix();
            GlStateManager.translate(px, 0, pz);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.translate(px * 0.5, 0.2, pz * 0.5);
            RenderUtil.drawImage(glowCircle, -size/1.5f, -size/1.5f, size/1.5f, size/1.5f, color);
            GlStateManager.popMatrix();
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderRings(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());
        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord - renderPosY;
        double z = interpolated.zCoord - renderPosZ;
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
        RenderUtil.setupOrientationMatrix(x, y, z);
        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        double radius = 0.8;
        float size = 0.25f;
        double time = System.currentTimeMillis() / 1000.0;
        for(int r = 0; r < 3; r++) {
            double offset = r * 0.8;
            double currentY = (time + offset) % 2.0;
            double currentRadius = radius * (1.0 - Math.abs(currentY - 1.0));
            if(currentY > 2.0) currentY -= 2.0;
            for (int i = 0; i < 20; i++) {
                double angle = Math.toRadians(i * 18);
                double px = Math.cos(angle) * currentRadius;
                double pz = Math.sin(angle) * currentRadius;
                GlStateManager.pushMatrix();
                GlStateManager.translate(px, currentY, pz);
                RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
                GlStateManager.popMatrix();
            }
        }
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderExhi(Render3DEvent event) {
        if (target == null) return;

        float alpha = getAlpha();
        int baseAlpha = (int) (75 * alpha);
        int color = target.hurtTime > 3 ? new Color(200, 255, 100, baseAlpha).getRGB() :
                target.hurtTime < 3 ? new Color(235, 40, 40, baseAlpha).getRGB() :
                        new Color(255, 255, 255, baseAlpha).getRGB();

        GlStateManager.pushMatrix();
        GL11.glShadeModel(7425);
        GL11.glHint(3154, 4354);

        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 2);

        double x = target.prevPosX + (target.posX - target.prevPosX) * event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double y = target.prevPosY + (target.posY - target.prevPosY) * event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double z = target.prevPosZ + (target.posZ - target.prevPosZ) * event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        GlStateManager.translate(x, y, z);

        AxisAlignedBB axisAlignedBB = target.getEntityBoundingBox();
        AxisAlignedBB renderBB = new AxisAlignedBB(
                axisAlignedBB.minX - 0.1 - target.posX,
                axisAlignedBB.minY - 0.1 - target.posY,
                axisAlignedBB.minZ - 0.1 - target.posZ,
                axisAlignedBB.maxX + 0.1 - target.posX,
                axisAlignedBB.maxY + 0.2 - target.posY,
                axisAlignedBB.maxZ + 0.1 - target.posZ
        );

        RenderUtil.drawAxisAlignedBB(renderBB, true, color);
        GlStateManager.popMatrix();
    }

    private void renderCircle(Render3DEvent event) {
        if (target == null) return;

        prevCircleStep = circleStep;
        circleStep += circleSpeed.getValue() * RenderUtil.deltaTime() * 0.05;

        float eyeHeight = target.getEyeHeight();
        if (target.isSneaking()) {
            eyeHeight -= 0.2F;
        }

        double cs = prevCircleStep + (circleStep - prevCircleStep) * event.getPartialTicks();
        double sinAnim = Math.abs(1.0D + Math.sin(cs)) / 2.0D;

        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY() + sinAnim * eyeHeight;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        GL11.glPushMatrix();
        GL11.glDisable(2884);
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glDisable(2929);
        GL11.glDisable(3008);
        GL11.glShadeModel(7425);
        GL11.glBegin(8);

        Color color = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        float alpha = getAlpha();

        for (int i = 0; i <= 360; ++i) {
            double angle = Math.toRadians(i);
            double radius = target.width * 0.8D;

            GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 0.6F * alpha);
            GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
            GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 0.01F * alpha);
            GL11.glVertex3d(x + Math.cos(angle) * radius, y - 0.5, z + Math.sin(angle) * radius);
        }

        GL11.glEnd();
        GL11.glEnable(2848);
        GL11.glBegin(2);

        for (int i = 0; i <= 360; ++i) {
            double angle = Math.toRadians(i);
            double radius = target.width * 0.8D;

            GL11.glColor4f(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, 0.8F * alpha);
            GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
        }

        GL11.glEnd();
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glEnable(3008);
        GL11.glEnable(2929);
        GL11.glShadeModel(7424);
        GL11.glDisable(3042);
        GL11.glEnable(2884);
        GL11.glPopMatrix();
    }

    // 1. Tornado: 自下而上的螺旋龙卷风，半径逐渐变大
    private void renderTornado(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.25f;
        double time = (System.currentTimeMillis() % 2000) / 1000.0;

        for (double h = 0; h < 2.2; h += 0.1) {
            double radius = 0.2 + h * 0.4; // 越往上越宽
            double angle = (time * 5.0 + h * 2.0) * Math.PI; // 旋转

            double px = Math.cos(angle) * radius;
            double pz = Math.sin(angle) * radius;

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, h, pz);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // 2. Atom: 模拟原子核外的电子轨道（3个交叉的椭圆）
    private void renderAtom(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.2f;
        double time = System.currentTimeMillis() / 4.0;
        double radius = 0.8;

        for (int i = 0; i < 3; i++) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, target.height / 2.0, 0); // 居中
            GL11.glRotated(i * 60, 1, 0, 0); // 每次旋转60度
            GL11.glRotated(i * 30, 0, 0, 1);

            for (int j = 0; j < 15; j++) { // 绘制轨迹点
                double angle = Math.toRadians(time + j * 24);
                double px = Math.cos(angle) * radius;
                double pz = Math.sin(angle) * radius;

                GlStateManager.pushMatrix();
                GlStateManager.translate(px, 0, pz);
                RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
                GlStateManager.popMatrix();
            }
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // 3. Sphere: 围绕目标旋转的球体点阵
    private void renderSphere(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.15f;
        double radius = 1.0;
        double time = System.currentTimeMillis() / 500.0;

        GlStateManager.translate(0, target.height / 2.0, 0);

        for (int i = 0; i < 40; i++) {
            // Fibonacci Sphere distribution
            double offset = 2.0 / 40;
            double y = i * offset - 1.0 + (offset / 2.0);
            double r = Math.sqrt(1 - y * y);
            double phi = i * 2.39996 + time; // Golden angle + rotation

            double px = Math.cos(phi) * r * radius;
            double pz = Math.sin(phi) * r * radius;
            double py = y * radius;

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            // 让粒子始终朝向玩家
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // 4. Heart: 头顶上方旋转的爱心
    private void renderHeart(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.2f;
        double time = System.currentTimeMillis() / 8.0;

        GlStateManager.translate(0, target.height + 0.8, 0); // 头顶
        GL11.glRotated(time % 360, 0, 1, 0); // 整体旋转

        for (double t = 0; t < Math.PI * 2; t += 0.2) {
            // Heart parametric equation
            double x = 16 * Math.pow(Math.sin(t), 3);
            double y = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);

            double scale = 0.04;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x * scale, y * scale, 0); // Flat heart
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // 5. DNA: 双螺旋结构上升
    private void renderDNA(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.25f;
        double radius = 0.5;
        double time = System.currentTimeMillis() / 1000.0;

        for (double h = 0; h < 2.0; h += 0.1) {
            // Strand 1
            double angle1 = h * 3.0 + time * 2.0;
            double x1 = Math.cos(angle1) * radius;
            double z1 = Math.sin(angle1) * radius;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x1, h, z1);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();

            // Strand 2 (Offset by PI)
            double x2 = Math.cos(angle1 + Math.PI) * radius;
            double z2 = Math.sin(angle1 + Math.PI) * radius;

            GlStateManager.pushMatrix();
            GlStateManager.translate(x2, h, z2);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // 6. Crystal: 头顶悬浮的水晶形状
    private void renderCrystal(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.3f;
        double time = System.currentTimeMillis() / 15.0;

        GlStateManager.translate(0, target.height + 0.6, 0);
        GL11.glRotated(time, 0, 1, 0);

        // Middle ring
        for(int i = 0; i < 4; i++) {
            GlStateManager.pushMatrix();
            double a = Math.toRadians(i * 90);
            GlStateManager.translate(Math.cos(a)*0.4, 0, Math.sin(a)*0.4);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();
        }
        // Top and Bottom points
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0.6, 0);
        RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
        GlStateManager.popMatrix();

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -0.6, 0);
        RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
        GlStateManager.popMatrix();

        finishRender();
    }

    // 7. Ripple: 脚下扩散的波纹
    private void renderRipple(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.2f;
        double time = System.currentTimeMillis() / 1000.0;

        for (int r = 0; r < 3; r++) {
            double expand = (time + r * 0.33) % 1.0; // 0 to 1
            double radius = expand * 1.5;
            double opacity = 1.0 - expand; // Fade out

            int fadeColor = new Color(
                    ((color >> 16) & 0xFF),
                    ((color >> 8) & 0xFF),
                    (color & 0xFF),
                    (int)(255 * opacity * getAlpha())).getRGB();

            for (int i = 0; i < 20; i++) {
                double angle = Math.toRadians(i * 18);
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(angle)*radius, 0.1, Math.sin(angle)*radius);
                RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, fadeColor);
                GlStateManager.popMatrix();
            }
        }
        finishRender();
    }

    // 8. Lotus: 脚下像莲花一样绽放
    private void renderLotus(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.25f;
        double time = System.currentTimeMillis() / 1000.0;

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45 + time * 20);

            // Draw petals (curves going up)
            for(double t = 0; t < 0.5; t+=0.1) {
                double r = 0.5 + t * 0.5;
                double h = Math.sin(t * Math.PI) * 0.5;

                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(angle)*r, h, Math.sin(angle)*r);
                RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
                GlStateManager.popMatrix();
            }
        }
        finishRender();
    }

    // 9. Crown: 头顶的皇冠，点上下浮动
    private void renderCrown(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.25f;
        double time = System.currentTimeMillis() / 200.0;
        double radius = 0.5;

        GlStateManager.translate(0, target.height + 0.4, 0);

        for (int i = 0; i < 5; i++) {
            double angle = Math.toRadians(i * 72 - time * 10);
            double yOffset = Math.sin(time + i) * 0.1; // Bobbing

            // Base point
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(angle)*radius, yOffset, Math.sin(angle)*radius);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, color);
            GlStateManager.popMatrix();

            // High point (Spike)
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(angle)*radius, yOffset + 0.3, Math.sin(angle)*radius);
            RenderUtil.drawImage(glowCircle, -size/3f, -size/3f, size/1.5f, size/1.5f, color);
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // 10. Flame: 粒子随机上升模拟火焰
    private void renderFlame(Render3DEvent event) {
        setupRender(event);
        int color = ColorUtil.applyOpacity(((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()), getAlpha()).getRGB();
        float size = 0.3f;
        double time = System.currentTimeMillis();

        for(int i=0; i<15; i++) {
            double speed = (15 - i) * 100;
            double h = ((time + i * 200) % 1500) / 1500.0; // 0 to 1 progress

            if(h > 0.8) continue; // Don't go too high

            double radius = 0.4 * (1.0 - h); // Taper at top
            double angle = (time / 300.0) + (i * 90);

            double px = Math.cos(angle) * radius;
            double py = h * 2.0; // Height scale
            double pz = Math.sin(angle) * radius;

            int fadeColor = ColorUtil.applyOpacity(new Color(color), (float)(1.0 - h)).getRGB();

            GlStateManager.pushMatrix();
            GlStateManager.translate(px, py, pz);
            RenderUtil.drawImage(glowCircle, -size/2f, -size/2f, size, size, fadeColor);
            GlStateManager.popMatrix();
        }
        finishRender();
    }

    // Helper method to reduce duplicate code
    private void setupRender(Render3DEvent event) {
        if (target == null) return;
        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());

        double x = interpolated.xCoord - renderPosX;
        double y = interpolated.yCoord - renderPosY;
        double z = interpolated.zCoord - renderPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);

        RenderUtil.setupOrientationMatrix(x, y, z);
        float[] viewAngles = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-viewAngles[0], 0.0, 1.0, 0.0);
        GL11.glRotated(viewAngles[1], 1.0, 0.0, 0.0);
    }

    private void finishRender() {
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mode.getValue() == 3 && target != null && isEntityVisible(target)) {
            float dst = mc.thePlayer.getDistanceToEntity(target);
            float[] pos = getTargetScreenPosition(target, event);
            if (pos != null) {
                drawTargetESP2D(pos[0], pos[1],
                        (1.0f - MathHelper.clamp_float(Math.abs(dst - 6.0f) / 60.0f, 0.0f, 0.75f)) * 1, 3);
            }
        }
    }

    @EventTarget
    public void onShader2D(Shader2DEvent event) {
        if (event.getShaderType() == Shader2DEvent.ShaderType.GLOW) {
            if (mode.getValue() == 3 && imageMode.getValue() == 0 && target != null && isEntityVisible(target)) {
                float dst = mc.thePlayer.getDistanceToEntity(target);
                float[] pos = getTargetScreenPosition(target, null);
                if (pos != null) {
                    drawTargetESP2D(pos[0], pos[1],
                            (1.0f - MathHelper.clamp_float(Math.abs(dst - 6.0f) / 60.0f, 0.0f, 0.75f)) * 1, 3);
                }
            }
        }
    }

    private void drawTargetESP2D(float x, float y, float scale, int index) {
        long millis = (System.currentTimeMillis() - lastTime) + index * 400L;
        boolean useAnimation = imageMode.getValue() == 5 ? animation.getValue() : true;
        double angle = useAnimation ? MathHelper.clamp_double((Math.sin(millis / 150.0) + 1.0) / 2.0 * 30.0, 0.0, 30.0) : 15.0;
        double scaled = useAnimation ? MathHelper.clamp_double((Math.sin(millis / 500.0) + 1.0) / 2.0, 0.8, 1.0) : 0.9;
        double rotate = useAnimation ? MathHelper.clamp_double((Math.sin(millis / 1000.0) + 1.0) / 2.0 * 360.0, 0.0, 360.0) : 0.0;

        Color baseColor = ((HUD) Myau.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
        float hurtAlpha = getHurtAlpha();

        Color hurtColor = new Color(255, 0, 0, 185);
        Color baseWithAlpha = ColorUtil.applyOpacity(baseColor, 1.0f);
        Color hurtWithAlpha = ColorUtil.applyOpacity(hurtColor, hurtAlpha);

        int r = (int)(baseWithAlpha.getRed() * (1 - hurtAlpha) + hurtWithAlpha.getRed() * hurtAlpha);
        int g = (int)(baseWithAlpha.getGreen() * (1 - hurtAlpha) + hurtWithAlpha.getGreen() * hurtAlpha);
        int b = (int)(baseWithAlpha.getBlue() * (1 - hurtAlpha) + hurtWithAlpha.getBlue() * hurtAlpha);
        int a = (int)(baseWithAlpha.getAlpha() * (1 - hurtAlpha) + hurtWithAlpha.getAlpha() * hurtAlpha);

        int color = new Color(r, g, b, a).getRGB();

        rotate = 45 - (angle - 15.0) + rotate;
        float size = 128.0f * scale * (float) scaled;

        float renderX = x - size / 2.0f;
        float renderY = y - size / 2.0f;
        float x2 = renderX + size;
        float y2 = renderY + size;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate((float) rotate, 0, 0, 1);
        GlStateManager.translate(-x, -y, 0);

        GL11.glDisable(3008);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);

        float alpha = getAlpha();
        GL11.glColor4f(baseColor.getRed() / 255.0f, baseColor.getGreen() / 255.0f, baseColor.getBlue() / 255.0f, alpha);

        ResourceLocation texture = rectangle;
        switch (imageMode.getValue()){
            case 1:
                texture = quadstapple;
                break;
            case 2:
                texture = trianglestapple;
                break;
            case 3:
                texture = trianglestipple;
                break;
            case 4:
                texture = aim;
                break;
            case 5:
                if (customImage != null) {
                    texture = customImage;
                }
                break;
        }

        RenderUtil.drawImage(texture, renderX, renderY, x2, y2, color, color, color, color);

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.resetColor();
        GlStateManager.shadeModel(7424);
        GlStateManager.depthMask(true);
        GL11.glEnable(3008);
        GlStateManager.popMatrix();
    }

    private float[] getTargetScreenPosition(EntityLivingBase entity, Render2DEvent event) {
        if (entity == null) return null;

        EntityRenderer entityRenderer = mc.entityRenderer;
        float partialTicks = event != null ? event.getPartialTicks() : ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;

        double x = MathUtil.interpolate(entity.prevPosX, entity.posX, partialTicks);
        double y = MathUtil.interpolate(entity.prevPosY, entity.posY, partialTicks) + entity.height * 0.4f;
        double z = MathUtil.interpolate(entity.prevPosZ, entity.posZ, partialTicks);

        double width = entity.width / 2.0f;
        double height = entity.height / 4.0f;

        AxisAlignedBB bb = new AxisAlignedBB(
                x - width, y - height, z - width,
                x + width, y + height, z + width
        );

        final double[][] vectors = {
                {bb.minX, bb.minY, bb.minZ},
                {bb.minX, bb.maxY, bb.minZ},
                {bb.minX, bb.maxY, bb.maxZ},
                {bb.minX, bb.minY, bb.maxZ},
                {bb.maxX, bb.minY, bb.minZ},
                {bb.maxX, bb.maxY, bb.minZ},
                {bb.maxX, bb.maxY, bb.maxZ},
                {bb.maxX, bb.minY, bb.maxZ}
        };

        ((IAccessorEntityRenderer) entityRenderer).callSetupCameraTransform(partialTicks, 0);

        float[] position = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -1.0F, -1.0F};
        ScaledResolution sr = new ScaledResolution(mc);
        int scaleFactor = sr.getScaleFactor();

        for (final double[] vec : vectors) {
            float[] projection = GLUtil.project2D(
                    (float) (vec[0] - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX()),
                    (float) (vec[1] - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()),
                    (float) (vec[2] - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()),
                    scaleFactor
            );

            if (projection != null && projection[2] >= 0.0F && projection[2] < 1.0F) {
                position[0] = Math.min(projection[0], position[0]);
                position[1] = Math.min(projection[1], position[1]);
                position[2] = Math.max(projection[0], position[2]);
                position[3] = Math.max(projection[1], position[3]);
            }
        }

        entityRenderer.setupOverlayRendering();

        if (position[0] == Float.MAX_VALUE || position[2] == -1.0F) {
            return null;
        }

        float centerX = (position[0] + position[2]) / 2.0f;
        float centerY = (position[1] + position[3]) / 2.0f;

        return new float[]{centerX, centerY};
    }
}