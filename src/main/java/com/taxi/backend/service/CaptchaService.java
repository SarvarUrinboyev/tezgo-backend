package com.taxi.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O'zimizning captcha generatori.
 * 6 ta belgi: 4 ta raqam + 2 ta harf, ustidan chiziq.
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    // sessionId → {code, expiry}
    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    // Chalkashmaydigan harflar (I, O, Q, U ga o'xshashlar chiqarildi)
    private static final char[] LETTERS = "ABCDEFGHJKLMNPRSTUVWXYZ".toCharArray();
    // Chalkashmaydigan raqamlar (0 va 1 chiqarildi)
    private static final char[] DIGITS  = "23456789".toCharArray();

    private record CaptchaEntry(String code, long expiresAt) {}

    // ── Yangi captcha yaratish ─────────────────────────────────────────────
    public Map<String, Object> generate(String sessionId) {
        try {
            // Eski sessiyalarni tozalash
            if (store.size() > 300) {
                long now = System.currentTimeMillis();
                store.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            }

            String code = buildCode();
            store.put(sessionId, new CaptchaEntry(code, System.currentTimeMillis() + 5 * 60_000L));

            byte[] img   = render(code);
            String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(img);
            return Map.of("captchaBase64", base64, "sessionId", sessionId);
        } catch (Exception e) {
            // AWT ishlamasa (headless server) — testMode
            log.error("Captcha render xatolik: {}", e.getMessage());
            String code = buildCode();
            store.put(sessionId, new CaptchaEntry(code, System.currentTimeMillis() + 5 * 60_000L));
            return Map.of("captchaBase64", "", "sessionId", sessionId, "testMode", true);
        }
    }

    // ── Captcha tekshirish ─────────────────────────────────────────────────
    public boolean verify(String sessionId, String userInput) {
        if (sessionId == null || userInput == null) return false;
        CaptchaEntry entry = store.remove(sessionId);
        if (entry == null || System.currentTimeMillis() > entry.expiresAt()) return false;
        return entry.code().equalsIgnoreCase(userInput.trim());
    }

    // ── 6 ta belgi: 4 raqam + 2 harf, aralashtirilgan ────────────────────
    private String buildCode() {
        Random rnd = new Random();
        char[] result = new char[6];

        // 2 ta harf uchun tasodifiy pozitsiya tanlash
        List<Integer> positions = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        Collections.shuffle(positions, rnd);
        Set<Integer> letterPos = new HashSet<>(positions.subList(0, 2));

        for (int i = 0; i < 6; i++) {
            result[i] = letterPos.contains(i)
                    ? LETTERS[rnd.nextInt(LETTERS.length)]
                    : DIGITS[rnd.nextInt(DIGITS.length)];
        }
        return new String(result);
    }

    // ── Rasm yaratish ──────────────────────────────────────────────────────
    private byte[] render(String code) throws Exception {
        final int W = 210, H = 72;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        Random rnd = new Random();

        // ── 1. Fon ──────────────────────────────────────────────────────
        g.setColor(new Color(13, 17, 30));
        g.fillRoundRect(0, 0, W, H, 14, 14);

        // Gradient effekt uchun yorqin to'rtburchak
        g.setColor(new Color(30, 45, 80, 60));
        g.fillRoundRect(0, 0, W, H / 2, 14, 14);

        // ── 2. Shovqin nuqtalar ─────────────────────────────────────────
        for (int i = 0; i < 90; i++) {
            int alpha = 30 + rnd.nextInt(70);
            g.setColor(new Color(80 + rnd.nextInt(100), 100 + rnd.nextInt(120), 200, alpha));
            int nx = rnd.nextInt(W), ny = rnd.nextInt(H);
            int ns = rnd.nextInt(3) + 1;
            g.fillOval(nx, ny, ns, ns);
        }

        // ── 3. Orqa shovqin chiziqlar ────────────────────────────────────
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i < 4; i++) {
            g.setColor(new Color(60, 80, 140, 40 + rnd.nextInt(30)));
            g.drawLine(rnd.nextInt(W / 2), rnd.nextInt(H),
                       W / 2 + rnd.nextInt(W / 2), rnd.nextInt(H));
        }

        // ── 4. Har bir belgi ─────────────────────────────────────────────
        Color[] palette = {
            new Color(250, 204,  21),  // sariq
            new Color( 96, 165, 250),  // ko'k
            new Color( 74, 222, 128),  // yashil
            new Color(251, 146,  60),  // to'q sariq
            new Color(167, 139, 250),  // binafsha
            new Color(251, 113, 133),  // pushti
        };
        // Ranglarni aralashtiramiz
        List<Color> colorList = new ArrayList<>(Arrays.asList(palette));
        Collections.shuffle(colorList, rnd);

        Font charFont = new Font(Font.MONOSPACED, Font.BOLD, 36);
        g.setFont(charFont);

        FontMetrics fm = g.getFontMetrics();
        int charW    = fm.charWidth('8');
        int totalW   = charW * 6 + 5 * 6; // 6ta belgi + 5ta bo'sh
        int startX   = (W - totalW) / 2;

        for (int i = 0; i < code.length(); i++) {
            String ch = String.valueOf(code.charAt(i));
            int x = startX + i * (charW + 6);
            int jY = rnd.nextInt(10) - 5;         // vertikal titroq
            int jX = rnd.nextInt(4)  - 2;         // gorizontal titroq

            // Qorong'i soya
            g.setColor(new Color(0, 0, 0, 100));
            g.drawString(ch, x + jX + 2, 48 + jY + 2);

            // Asosiy belgi
            g.setColor(colorList.get(i));
            g.drawString(ch, x + jX, 48 + jY);
        }

        // ── 5. Ustidan o'tuvchi asosiy chiziq ───────────────────────────
        // O'qishga to'sqinlik qilmaydi, lekin OCR ni chalkashtiради
        int lineY = H / 2 + 2 + rnd.nextInt(8) - 4;
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(148, 163, 200, 150));
        // To'g'ri chiziq emas — biroz to'lqinli ko'rinish uchun qismlarga bo'lamiz
        int segments = 6;
        int segW = W / segments;
        int prevX = 4, prevY = lineY;
        for (int i = 1; i <= segments; i++) {
            int nx = i * segW - 4;
            int ny = lineY + rnd.nextInt(8) - 4;
            g.drawLine(prevX, prevY, nx, ny);
            prevX = nx; prevY = ny;
        }

        // ── 6. Chegara (border) ──────────────────────────────────────────
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(50, 70, 120, 180));
        g.drawRoundRect(1, 1, W - 2, H - 2, 12, 12);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
