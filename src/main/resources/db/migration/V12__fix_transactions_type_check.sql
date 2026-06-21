-- transactions.type CHECK constraint ga TAXOMETER_COMMISSION qo'shish
-- Mavjud qiymatlar: TRIP_INCOME, COMMISSION, TOPUP, WITHDRAWAL, DEDUCT, BONUS, PENALTY
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_type_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
    CHECK (type IN (
        'TRIP_INCOME',
        'COMMISSION',
        'TOPUP',
        'WITHDRAWAL',
        'DEDUCT',
        'BONUS',
        'PENALTY',
        'TAXOMETER_COMMISSION'
    ));
