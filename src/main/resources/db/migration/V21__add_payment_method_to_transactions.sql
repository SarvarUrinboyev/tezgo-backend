-- Task 3A: To'lov usuli ustuni — faqat admin qo'l to'ldirish uchun
ALTER TABLE transactions
    ADD COLUMN payment_method VARCHAR(10) NULL
        CONSTRAINT chk_payment_method CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'CARD'));
