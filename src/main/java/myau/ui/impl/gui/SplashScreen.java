package myau.ui.impl.gui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无边框启动画面
 *
 * <ul>
 *   <li>全屏展示 /assets/minecraft/blitzbounce/splash.png</li>
 *   <li>右下角显示 "SushiYuan Shield 加载中..." 文字</li>
 *   <li>右下角进度条随 10 秒倒计时填充</li>
 *   <li>倒计时结束后窗口关闭，调用线程恢复执行</li>
 * </ul>
 */
public class SplashScreen {

    private static final int   DURATION_MS  = 10_000;   // 总时长 10 秒
    private static final int   TICK_MS      = 50;        // 刷新间隔（ms），约 20 fps
    private static final int   BAR_W        = 280;       // 进度条宽度
    private static final int   BAR_H        = 6;         // 进度条高度
    private static final int   MARGIN       = 18;        // 距窗口边缘距离
    private static final int   ARC          = 4;         // 圆角半径
    private static final Color COLOR_BG     = new Color(0, 0, 0, 120);   // 文字区半透明背景
    private static final Color COLOR_TRACK  = new Color(255, 255, 255, 60);
    private static final Color COLOR_FILL   = new Color(255, 255, 255, 220);
    private static final Color COLOR_TEXT   = new Color(255, 255, 255, 210);
    private static final String LABEL_TEXT  = "SuhiYuan Shield 保护中...";

    /** 显示 splash 窗口并阻塞当前线程 50 秒。 */
    public static void show() {
        CountDownLatch latch = new CountDownLatch(1);

        SwingUtilities.invokeLater(() -> buildAndRun(latch));

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------

    private static void buildAndRun(CountDownLatch latch) {
        // ---- 加载图片 ----
        BufferedImage image = loadImage();
        int imgW = (image != null) ? image.getWidth()  : 854;
        int imgH = (image != null) ? image.getHeight() : 480;

        // ---- 进度计数器（0 ~ DURATION_MS） ----
        AtomicInteger elapsed = new AtomicInteger(0);

        // ---- 主绘制面板 ----
        JPanel panel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // 背景图（或纯黑）
                if (image != null) {
                    g.drawImage(image, 0, 0, w, h, null);
                } else {
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, w, h);
                }

                // ---- 右下角 HUD 区域 ----
                Font font = new Font("SansSerif", Font.PLAIN, 13);
                g.setFont(font);
                FontMetrics fm = g.getFontMetrics(font);
                int textW  = fm.stringWidth(LABEL_TEXT);
                int textH  = fm.getAscent();

                // HUD 总宽取进度条宽和文字宽中的较大值
                int hudW   = Math.max(BAR_W, textW) + MARGIN;
                int hudH   = textH + 6 + BAR_H + MARGIN;   // 文字 + 间距 + 进度条 + 下边距
                int hudX   = w - hudW - MARGIN;
                int hudY   = h - hudH - MARGIN;

                // 半透明底板
                g.setColor(COLOR_BG);
                g.fill(new RoundRectangle2D.Float(hudX - 8, hudY - 6,
                        hudW + 16, hudH + 10, 10, 10));

                // 文字
                g.setColor(COLOR_TEXT);
                g.drawString(LABEL_TEXT,
                        w - MARGIN - Math.max(BAR_W, textW),
                        hudY + textH);

                // 进度条轨道
                int barX = w - MARGIN - BAR_W;
                int barY = hudY + textH + 6;
                g.setColor(COLOR_TRACK);
                g.fill(new RoundRectangle2D.Float(barX, barY, BAR_W, BAR_H, ARC, ARC));

                // 进度条填充
                float progress = Math.min(1f, (float) elapsed.get() / DURATION_MS);
                int fillW = (int) (BAR_W * progress);
                if (fillW > 0) {
                    g.setColor(COLOR_FILL);
                    g.fill(new RoundRectangle2D.Float(barX, barY, fillW, BAR_H, ARC, ARC));
                }
            }
        };
        panel.setBackground(Color.BLACK);

        // ---- 无边框窗口 ----
        JWindow window = new JWindow();
        window.setContentPane(panel);
        window.setSize(imgW, imgH);
        window.setLocationRelativeTo(null);
        window.setAlwaysOnTop(true);
        window.setVisible(true);

        // ---- 定时刷新进度 ----
        Timer ticker = new Timer(TICK_MS, null);
        ticker.addActionListener(e -> {
            int now = elapsed.addAndGet(TICK_MS);
            panel.repaint();
            if (now >= DURATION_MS) {
                ticker.stop();
                window.setVisible(false);
                window.dispose();
                latch.countDown();
            }
        });
        ticker.start();
    }

    private static BufferedImage loadImage() {
        try (InputStream is = SplashScreen.class
                .getResourceAsStream("/assets/minecraft/myau/image/splash.png")) {
            if (is != null) return ImageIO.read(is);
        } catch (Exception ignored) {
        }
        return null;
    }
}
