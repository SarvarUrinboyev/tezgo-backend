package com.taxi.backend.service;

import com.taxi.backend.model.SupportMessage;
import com.taxi.backend.model.User;
import com.taxi.backend.repository.SupportMessageRepository;
import com.taxi.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Qo'llab-quvvatlash (support) chat — yo'lovchi ↔ operator/admin.
 *
 * Trip chatidan farqi: DOIMIY (Postgres, SupportMessageRepository) saqlanadi —
 * Redis/24h emas. Real-time talab qilinmaydi: ikkala tomon ham REST orqali poll qiladi
 * (passenger ekran ochiqligida ~3s, admin ~10s). Manba — DB.
 *
 * Thread = user_id (yo'lovchi). Operator javob yozsa ham xabar shu user_id ostida,
 * sender_role=OPERATOR, sender_id=operator.id bilan saqlanadi.
 */
@Service
public class SupportChatService {

    private static final Logger log = LoggerFactory.getLogger(SupportChatService.class);
    private static final int MAX_LEN = 1000;
    public static final String ROLE_PASSENGER = "PASSENGER";
    public static final String ROLE_OPERATOR = "OPERATOR";

    private final SupportMessageRepository repo;
    private final UserRepository userRepository;

    public SupportChatService(SupportMessageRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    /** Yo'lovchi support'ga xabar yuboradi. */
    @Transactional
    public Map<String, Object> sendFromPassenger(User passenger, String text) {
        String clean = clean(text);
        SupportMessage m = new SupportMessage();
        m.setUser(passenger);
        m.setSenderRole(ROLE_PASSENGER);
        m.setSenderId(passenger.getId());
        m.setText(clean);
        m.setReadByPassenger(true);   // o'zi yozgan — o'qilgan
        m.setReadByOperator(false);   // operator hali ko'rmagan
        repo.save(m);
        log.info("Support: passenger {} sent a message", passenger.getId());
        return toMap(m, passenger);
    }

    /** Operator/admin yo'lovchining thread'iga javob yozadi. */
    @Transactional
    public Map<String, Object> replyFromStaff(User staff, Long passengerUserId, String text) {
        String clean = clean(text);
        User passenger = userRepository.findById(passengerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Yo'lovchi topilmadi"));
        SupportMessage m = new SupportMessage();
        m.setUser(passenger);
        m.setSenderRole(ROLE_OPERATOR);
        m.setSenderId(staff.getId());
        m.setText(clean);
        m.setReadByOperator(true);    // staff yozdi — operator tomonda o'qilgan
        m.setReadByPassenger(false);  // yo'lovchi hali ko'rmagan
        repo.save(m);
        log.info("Support: staff {} replied to passenger {}", staff.getId(), passengerUserId);
        return toMap(m, passenger);
    }

    /** Yo'lovchi o'z thread'ini ochadi — operator xabarlari o'qilgan deb belgilanadi. */
    @Transactional
    public List<Map<String, Object>> getThreadForPassenger(User passenger) {
        repo.markOperatorMessagesReadByPassenger(passenger.getId());
        return threadMaps(passenger.getId());
    }

    /** Operator/admin yo'lovchi thread'ini ochadi — yo'lovchi xabarlari o'qilgan deb belgilanadi. */
    @Transactional
    public List<Map<String, Object>> getThreadForStaff(Long passengerUserId) {
        repo.markPassengerMessagesReadByOperator(passengerUserId);
        return threadMaps(passengerUserId);
    }

    private List<Map<String, Object>> threadMaps(Long userId) {
        List<SupportMessage> msgs = repo.findByUserIdOrderByCreatedAtAsc(userId);
        List<Map<String, Object>> out = new ArrayList<>(msgs.size());
        for (SupportMessage m : msgs) out.add(toMap(m, m.getUser()));
        return out;
    }

    /** Admin uchun suhbatlar ro'yxati: har thread bo'yicha oxirgi xabar + o'qilmaganlar soni. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listConversations() {
        Map<Long, Long> unread = new HashMap<>();
        for (Object[] row : repo.findUnreadCountsPerThread()) {
            unread.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        List<SupportMessage> latest = repo.findLatestPerThread();
        List<Map<String, Object>> out = new ArrayList<>(latest.size());
        for (SupportMessage m : latest) {
            User u = m.getUser();
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("userId", u.getId());
            c.put("name", u.getName() != null ? u.getName() : u.getPhone());
            c.put("phone", u.getPhone());
            c.put("lastText", m.getText());
            c.put("lastRole", m.getSenderRole());
            c.put("lastAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            c.put("unread", unread.getOrDefault(u.getId(), 0L));
            out.add(c);
        }
        return out;
    }

    /** Yo'lovchi uchun o'qilmagan (operator) xabarlar soni — profil badge. */
    @Transactional(readOnly = true)
    public long passengerUnreadCount(Long userId) {
        return repo.countByUserIdAndReadByPassengerFalseAndSenderRole(userId, ROLE_OPERATOR);
    }

    /** Operator inbox uchun global o'qilmagan (yo'lovchi) xabarlar soni. */
    @Transactional(readOnly = true)
    public long operatorUnreadTotal() {
        return repo.countByReadByOperatorFalseAndSenderRole(ROLE_PASSENGER);
    }

    private Map<String, Object> toMap(SupportMessage m, User threadUser) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", m.getId());
        msg.put("userId", threadUser != null ? threadUser.getId() : null);
        msg.put("senderId", m.getSenderId());
        // Yo'lovchi tomon operator xabarini "Qo'llab-quvvatlash" deb ko'radi
        msg.put("senderName", ROLE_OPERATOR.equals(m.getSenderRole())
                ? "Qo'llab-quvvatlash"
                : (threadUser != null && threadUser.getName() != null ? threadUser.getName()
                   : (threadUser != null ? threadUser.getPhone() : null)));
        msg.put("role", m.getSenderRole());
        msg.put("text", m.getText());
        msg.put("sentAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : LocalDateTime.now().toString());
        msg.put("ts", m.getId() != null ? m.getId() : System.currentTimeMillis());
        return msg;
    }

    private String clean(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Xabar bo'sh");
        }
        String t = text.trim();
        if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN);
        return t;
    }
}
