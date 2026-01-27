package myau.mixin;

import myau.module.modules.Chat;
import myau.util.AnimationUtil;
import myau.util.RenderUtil;
import myau.util.shader.ShadowShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.List;

@Mixin(GuiNewChat.class)
public abstract class MixinGuiNewChat {
    private final AnimationUtil backgroundAnim = new AnimationUtil(AnimationUtil.Easing.EASE_OUT_SINE, 300);
    @Shadow
    @Final
    private Minecraft mc;
    @Shadow
    private int scrollPos;
    @Shadow
    private List<ChatLine> drawnChatLines;
    private float currentOpacity = 0.0f;

    @Shadow
    public abstract int getChatWidth();

    @Shadow
    public abstract int getLineCount();

    @Shadow
    public abstract float getChatScale();

    @Shadow
    public abstract boolean getChatOpen();

    @Shadow
    private boolean isScrolled;

    @Inject(method = "drawChat", at = @At("HEAD"))
    public void onDrawChatHead(int updateCounter, CallbackInfo ci) {
        backgroundAnim.run(1.0f);
        currentOpacity = (float) backgroundAnim.getValue();
    }

    /**
     * @author Myau
     * @reason 优化聊天栏渲染，添加圆角背景、阴影及平滑动画
     */
    @Overwrite
    public void drawChat(int updateCounter) {
        if (this.mc.gameSettings.chatVisibility == EntityPlayer.EnumChatVisibility.HIDDEN) return;

        int maxLineCount = this.getLineCount();
        int drawnSize = this.drawnChatLines.size();
        if (drawnSize == 0) return;

        int validLines = 0;
        boolean chatOpen = this.getChatOpen();

        for (int i = 0; i + scrollPos < drawnSize && i < maxLineCount; ++i) {
            ChatLine chatline = drawnChatLines.get(i + scrollPos);
            if (chatline != null) {
                int age = updateCounter - chatline.getUpdatedCounter();
                if (chatOpen || age < 200) {
                    validLines++;
                }
            }
        }

        if (validLines == 0 && currentOpacity < 0.01f) return;

        float opacitySettings = this.mc.gameSettings.chatOpacity * 0.9F + 0.1F;
        float scale = this.getChatScale();
        int chatWidth = MathHelper.ceiling_float_int(getChatWidth() / scale);
        int chatHeight = validLines * 9 + 4;

        if (validLines == 0) chatHeight = 0;

        GlStateManager.pushMatrix();

        // 保持与原版一致的渲染位置（只改变缩放）
        GlStateManager.translate(2.0F, 20.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);

        // 获取ChatRender模块实例
        Chat chat = Chat.INSTANCE;

        // 计算背景和文本的最终透明度
        float finalOpacity = currentOpacity * opacitySettings;

        // 绘制背景（仅在可见时）
        if (finalOpacity > 0.01f && chatHeight > 0) {
            float bgAlpha = finalOpacity;
            if (chat != null && chat.isEnabled()) {
                bgAlpha *= (float) chat.backgroundOpacity.getValue();
            } else {
                bgAlpha *= 0.8f; // 默认值
            }

            int alphaInt = (int) (MathHelper.clamp_float(bgAlpha, 0, 1) * 255);
            int backgroundColor = new Color(15, 15, 20, alphaInt).getRGB();
            int borderColor = new Color(45, 45, 55, (int) (MathHelper.clamp_float(bgAlpha, 0, 1) * 200)).getRGB();

            float shadowOpacity = 0.6f; // 默认阴影不透明度
            if (chat != null && chat.isEnabled()) {
                shadowOpacity = (float) chat.shadowOpacity.getValue();
            }
            int shadowAlpha = (int) (MathHelper.clamp_float(currentOpacity * shadowOpacity, 0, 1) * 100);
            int shadowColor = new Color(0, 0, 0, shadowAlpha).getRGB();

            float bgWidth = chatWidth + 4.0f;
            float bgX = 0;
            float bgY = -chatHeight; // 背景从当前原点向上延伸

            // 根据模块设置决定是否绘制阴影
            boolean drawShadow = true;
            if (chat != null && chat.isEnabled()) {
                drawShadow = chat.enableShadow.getValue();
            }

            if (drawShadow) {
                float shadowRadius = 4.0f; // 默认阴影半径
                if (chat != null && chat.isEnabled()) {
                    shadowRadius = (float) chat.shadowRadius.getValue();
                }
                // 调整阴影位置以匹配背景
                ShadowShader.drawShadow(bgX, bgY, bgWidth, chatHeight, shadowRadius, 6.0f, shadowColor);
            }

            // 根据模块设置决定是否绘制圆角
            boolean roundCorners = true;
            if (chat != null && chat.isEnabled()) {
                roundCorners = chat.enableRoundCorners.getValue();
            }

            if (roundCorners) {
                float cornerRadius = 4.0f; // 默认圆角半径
                if (chat != null && chat.isEnabled()) {
                    cornerRadius = (float) chat.cornerRadius.getValue();
                }
                RenderUtil.drawRoundedRect(bgX, bgY, bgWidth, chatHeight, cornerRadius, backgroundColor, true, true, true, true);
            } else {
                // 如果不使用圆角，则绘制普通矩形
                RenderUtil.drawRect(bgX, bgY, bgWidth, chatHeight, backgroundColor);
            }
        }

        // 绘制聊天文本
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();

        FontRenderer fontRenderer = mc.fontRendererObj;
        int lineHeight = 9;
        int lineIndex = 0;

        for (int i = 0; i + scrollPos < drawnSize && i < maxLineCount; ++i) {
            ChatLine chatline = drawnChatLines.get(i + scrollPos);

            if (chatline != null) {
                int age = updateCounter - chatline.getUpdatedCounter();

                if (chatOpen || age < 200) {
                    double opacity = 1.0;

                    if (!chatOpen && age > 180) {
                        opacity = 1.0 - (double) (age - 180) / 20.0;
                    }

                    opacity *= finalOpacity;

                    int alpha = (int) (255.0 * opacity);
                    if (alpha > 4) {
                        int color = (alpha << 24) | 0xFFFFFF;
                        int lineY = -(lineIndex + 1) * lineHeight - 2; // 从当前原点向上绘制

                        fontRenderer.drawStringWithShadow(
                                chatline.getChatComponent().getFormattedText(),
                                0.0f, // X坐标保持为0，因为已经平移了2像素
                                lineY,
                                color
                        );

                        lineIndex++;
                    }
                }
            }
        }

        // 如果需要，可以在这里绘制滚动条（原版逻辑）
        if (chatOpen) {
            int fontHeight = this.mc.fontRendererObj.FONT_HEIGHT;
            int totalHeight = drawnSize * fontHeight + drawnSize;
            int visibleHeight = validLines * fontHeight + validLines;

            if (totalHeight > visibleHeight) {
                int scrollBarHeight = visibleHeight * visibleHeight / totalHeight;
                int scrollBarY = this.scrollPos * visibleHeight / drawnSize;

                int scrollBarColor1 = this.isScrolled ? 13382451 : 3355562;
                int scrollBarColor2 = 13421772;
                int scrollBarAlpha = scrollBarY > 0 ? 170 : 96;

                // 绘制滚动条背景
                RenderUtil.drawRect(-3, -scrollBarY - scrollBarHeight, -1, -scrollBarY,
                        scrollBarColor1 + (scrollBarAlpha << 24));
                RenderUtil.drawRect(-1, -scrollBarY - scrollBarHeight, 0, -scrollBarY,
                        scrollBarColor2 + (scrollBarAlpha << 24));
            }
        }

        GlStateManager.popMatrix();
    }
}