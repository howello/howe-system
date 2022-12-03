package com.howe.common.utils.captcha;

import cn.hutool.core.util.RandomUtil;
import com.howe.common.config.HoweConfig;
import com.howe.common.enums.exception.CommonExceptionEnum;
import com.howe.common.enums.redis.RedisKeyPrefixEnum;
import com.howe.common.exception.child.CommonException;
import com.howe.common.utils.redis.RedisUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>@Author lu
 * <p>@Date 2022/11/28 16:30 星期一
 * <p>@Version 1.0
 * <p>@Description 验证码工具类
 */
@Component
public class CaptchaUtils {
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private HoweConfig howeConfig;

    /**
     * 常用颜色
     */
    public static final int[][] COLOR = {{0, 135, 255}, {51, 153, 51}, {255, 102, 102}, {255, 153, 0},
            {153, 102, 0}, {153, 102, 153}, {51, 153, 153}, {102, 102, 255}, {0, 102, 204}, {204, 51, 51},
            {0, 153, 204}, {0, 51, 102}};

    /**
     * 生成验证码
     *
     * @return
     */
    public String generateCode(String uuid, int height, int width) {
        String type = "data:image/gif;base64,";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        String code = out(outputStream, height, width);
        Integer expireTime = howeConfig.getCaptcha().getExpireTime();
        redisUtils.set(RedisKeyPrefixEnum.VERIFICATION_CODE, uuid, code, expireTime, TimeUnit.SECONDS);
        return type + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * 校验验证码
     * 失败抛异常
     */
    @SneakyThrows
    public void validateCaptcha(String uuid, String code) {
        String captcha = redisUtils.get(RedisKeyPrefixEnum.VERIFICATION_CODE, uuid);
        if (StringUtils.isBlank(captcha)) {
            throw new CommonException(CommonExceptionEnum.VERIFICATION_CODE_VERIFICATION_FAILED);
        }
        if (!captcha.equalsIgnoreCase(code)) {
            throw new CommonException(CommonExceptionEnum.VERIFICATION_CODE_VERIFICATION_FAILED);
        }
    }

    private String out(OutputStream os, int height, int width) {
        Integer len = howeConfig.getCaptcha().getLength();
        char[] strs = new char[len];
        try {
            char[] allTexts = howeConfig.getCaptcha().getBaseStr().toCharArray();
            // 随机生成每个文字的颜色
            Color[] fontColor = new Color[len];
            for (int i = 0; i < len; i++) {
                fontColor[i] = color();
                int index = RandomUtil.randomInt(allTexts.length);
                strs[i] = allTexts[index];
            }
            // 随机生成贝塞尔曲线参数h
            int x1 = 5, y1 = num(5, height / 2);
            int x2 = width - 5, y2 = num(height / 2, height - 5);
            int ctrlx = num(width / 4, width / 4 * 3), ctrly = num(5, height - 5);
            if (num(2) == 0) {
                int ty = y1;
                y1 = y2;
                y2 = ty;
            }
            int ctrlx1 = num(width / 4, width / 4 * 3), ctrly1 = num(5, height - 5);
            int[][] besselXY = new int[][]{{x1, y1}, {ctrlx, ctrly}, {ctrlx1, ctrly1}, {x2, y2}};
            // 开始画gif每一帧
            GifEncoder gifEncoder = new GifEncoder();
            gifEncoder.setQuality(180);
            gifEncoder.setDelay(100);
            gifEncoder.setRepeat(0);
            gifEncoder.start(os);
            for (int i = 0; i < len; i++) {
                BufferedImage frame = graphicsImage(fontColor, strs, i, besselXY, height, width);
                gifEncoder.addFrame(frame);
                frame.flush();
            }
            gifEncoder.finish();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return new String(strs);
    }

    /**
     * 画随机码图
     *
     * @param fontColor 随机字体颜色
     * @param strs      字符数组
     * @param flag      透明度
     * @param besselXY  干扰线参数
     * @return BufferedImage
     */
    @SneakyThrows
    private BufferedImage graphicsImage(Color[] fontColor, char[] strs, int flag, int[][] besselXY, int height, int width) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D) image.getGraphics();
        // 填充背景颜色
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);
        // 抗锯齿
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 画干扰圆圈
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f * num(10)));
        // 设置透明度
        drawOval(2, g2d, height, width);
        // 画干扰线
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        // 设置透明度
        g2d.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        g2d.setColor(fontColor[0]);
        CubicCurve2D shape = new CubicCurve2D.Double(besselXY[0][0], besselXY[0][1], besselXY[1][0], besselXY[1][1],
                besselXY[2][0], besselXY[2][1], besselXY[3][0], besselXY[3][1]);
        g2d.draw(shape);
        // 画验证码
        g2d.setFont(setFont());
        FontMetrics fontMetrics = g2d.getFontMetrics();
        // 每一个字符所占的宽度
        int fW = width / strs.length;
        // 字符的左右边距
        int fSp = (fW - (int) fontMetrics.getStringBounds("W", g2d).getWidth()) / 2;
        for (int i = 0; i < fontColor.length; i++) {
            // 设置透明度
            AlphaComposite ac3 = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, getAlpha(flag, i, fontColor.length));
            g2d.setComposite(ac3);
            g2d.setColor(fontColor[i]);
            int fY = height - ((height - (int) fontMetrics.getStringBounds(String.valueOf(strs[i]), g2d).getHeight()) >> 1);
            g2d.drawString(String.valueOf(strs[i]), i * fW + fSp + 3, fY - 3);
        }
        g2d.dispose();
        return image;
    }

    /**
     * 获取随机常用颜色
     *
     * @return 随机颜色
     */
    private Color color() {
        int[] color = COLOR[num(COLOR.length)];
        return new Color(color[0], color[1], color[2]);
    }

    private int num(Integer limit) {
        return RandomUtil.randomInt(limit);
    }


    private int num(Integer start, Integer end) {
        return RandomUtil.randomInt(start, end);
    }

    /**
     * 获取透明度,从0到1,自动计算步长
     *
     * @param i
     * @param j
     * @return 透明度
     */
    private float getAlpha(int i, int j, int len) {
        int num = i + j;
        float r = (float) 1 / (len - 1);
        float s = len * r;
        return num >= len ? (num * r - s) : num * r;
    }

    /**
     * 随机画干扰圆
     *
     * @param num 数量
     * @param g   Graphics2D
     */
    private void drawOval(int num, Graphics2D g, int height, int width) {
        for (int i = 0; i < num; i++) {
            g.setColor(color());
            int w = 5 + num(10);
            g.drawOval(num(width - 25), num(height - 15), w, w);
        }
    }

    private Font setFont() throws IOException, FontFormatException {
        return Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(getClass().getResourceAsStream("/fonts/actionj.ttf")))
                .deriveFont(Font.BOLD, 32f);
    }
}
