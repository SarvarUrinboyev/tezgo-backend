package com.taxi.backend.enums;

import java.util.List;

/**
 * Haydovchi "Xabarlar" ekranidagi 6 kanal.
 *
 * TEXNIK_YORDAM — 2 tomonlama support (support_messages, V34/V38), haydovchi javob yoza oladi.
 * Qolgan 5 tasi — ADMIN→DRIVER, FAQAT O'QISH (channel_messages, V39): haydovchi javob yoza OLMAYDI.
 */
public enum MessageChannel {
    TEXNIK_YORDAM,
    PRO_YANGILIKLARI,
    BONUSLAR,
    QOLLAB_QUVVATLASH,
    OGOHLANTIRISHLAR,
    HISOB_BALANS;

    /** Admin broadcast qila oladigan (faqat o'qish) kanallar — TEXNIK_YORDAM dan tashqari hammasi. */
    public static final List<MessageChannel> BROADCAST_CHANNELS = List.of(
            PRO_YANGILIKLARI, BONUSLAR, QOLLAB_QUVVATLASH, OGOHLANTIRISHLAR, HISOB_BALANS);

    /** name broadcast (faqat o'qish) kanalmi? TEXNIK_YORDAM va noma'lum qiymatlar uchun false. */
    public static boolean isBroadcastChannel(String name) {
        if (name == null) return false;
        try { return BROADCAST_CHANNELS.contains(MessageChannel.valueOf(name)); }
        catch (IllegalArgumentException e) { return false; }
    }
}
