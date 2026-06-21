package com.taxi.backend.pricing;

/**
 * Surge omil — har bir omilning nomi, qiymati va sababi.
 * Debug va UI uchun foydali — foydalanuvchiga nima uchun narx oshganini ko'rsatish mumkin.
 */
public record SurgeFactor(String name, double value, String reason) {

    /** Omil surge ga ta'sir qilmaydimi? */
    public boolean isNeutral() {
        return value == 0.0;
    }
}
