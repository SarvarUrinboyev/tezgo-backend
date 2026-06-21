-- V28: broadcast_targets â€” BroadcastMessage.targetDriverIds uchun @ElementCollection jadvali.
-- Entity (BroadcastMessage.java):
--   @CollectionTable(name = "broadcast_targets", joinColumns = @JoinColumn(name = "message_id"))
--   @Column(name = "driver_id")  List<Long> targetDriverIds
-- Bu jadval V1..V27 migratsiyalarida YARATILMAGAN edi. Toza (bo'sh) bazada Hibernate
-- ddl-auto=validate "Schema-validation: missing table [broadcast_targets]" bilan ishga
-- tushmas edi. Mavjud bazalarda IF NOT EXISTS tufayli xavfsiz (qayta yaratmaydi).
CREATE TABLE IF NOT EXISTS broadcast_targets (
    message_id BIGINT NOT NULL REFERENCES broadcast_messages(id) ON DELETE CASCADE,
    driver_id  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_broadcast_targets_message ON broadcast_targets(message_id);
