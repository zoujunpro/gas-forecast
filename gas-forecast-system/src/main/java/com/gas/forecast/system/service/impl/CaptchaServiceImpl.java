package com.gas.forecast.system.service.impl;

import com.gas.forecast.common.core.BusinessException;
import com.gas.forecast.system.dto.resp.AuthCaptchaResponse;
import com.gas.forecast.system.service.CaptchaService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

@Service
public class CaptchaServiceImpl implements CaptchaService {
    private static final String CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final long EXPIRE_SECONDS = 300;
    private static final int WIDTH = 128;
    private static final int HEIGHT = 44;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, CaptchaItem> captchas = new ConcurrentHashMap<>();

    @Override
    public AuthCaptchaResponse createCaptcha() {
        cleanupExpired();
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        captchas.put(captchaId, new CaptchaItem(code, Instant.now().getEpochSecond() + EXPIRE_SECONDS));
        return new AuthCaptchaResponse(captchaId, renderImage(code));
    }

    @Override
    public void validate(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            throw new BusinessException("400", "验证码错误");
        }
        CaptchaItem item = captchas.remove(captchaId);
        if (item == null || item.expiresAt() < Instant.now().getEpochSecond()) {
            throw new BusinessException("400", "验证码已过期，请刷新后重试");
        }
        if (!item.code().equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException("400", "验证码错误");
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(4);
        for (int index = 0; index < 4; index++) {
            code.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return code.toString();
    }

    private String renderImage(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(246, 248, 251));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        drawNoise(graphics);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        for (int index = 0; index < code.length(); index++) {
            graphics.setColor(new Color(24 + random.nextInt(70), 80 + random.nextInt(80), 130 + random.nextInt(90)));
            graphics.rotate(Math.toRadians(random.nextInt(25) - 12), 24 + index * 24, 28);
            graphics.drawString(String.valueOf(code.charAt(index)), 16 + index * 25, 31);
            graphics.rotate(Math.toRadians(12 - random.nextInt(25)), 24 + index * 24, 28);
        }
        graphics.dispose();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", outputStream);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create captcha image", exception);
        }
    }

    private void drawNoise(Graphics2D graphics) {
        for (int index = 0; index < 8; index++) {
            graphics.setColor(new Color(180 + random.nextInt(50), 190 + random.nextInt(45), 205 + random.nextInt(40)));
            int y = random.nextInt(HEIGHT);
            graphics.drawLine(
                    random.nextInt(WIDTH / 2), y, WIDTH / 2 + random.nextInt(WIDTH / 2), random.nextInt(HEIGHT));
        }
        for (int index = 0; index < 32; index++) {
            graphics.setColor(new Color(180 + random.nextInt(60), 190 + random.nextInt(55), 205 + random.nextInt(45)));
            graphics.fillOval(random.nextInt(WIDTH), random.nextInt(HEIGHT), 2, 2);
        }
    }

    private void cleanupExpired() {
        long now = Instant.now().getEpochSecond();
        captchas.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CaptchaItem {
        private String code;

        private long expiresAt;

        public String code() {
            return code;
        }

        public long expiresAt() {
            return expiresAt;
        }
    }
}
