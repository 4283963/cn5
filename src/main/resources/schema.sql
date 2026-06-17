CREATE TABLE IF NOT EXISTS reconciliation_task (
    id              BIGSERIAL PRIMARY KEY,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count     INTEGER DEFAULT 0,
    match_count     INTEGER DEFAULT 0,
    difference_count INTEGER DEFAULT 0,
    total_order_amount  DECIMAL(18, 6) DEFAULT 0.000000,
    total_payment_amount DECIMAL(18, 6) DEFAULT 0.000000,
    total_difference DECIMAL(18, 6) DEFAULT 0.000000,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0
);

COMMENT ON TABLE reconciliation_task IS '对账任务表';
COMMENT ON COLUMN reconciliation_task.start_date IS '对账开始日期';
COMMENT ON COLUMN reconciliation_task.end_date IS '对账结束日期';
COMMENT ON COLUMN reconciliation_task.status IS '任务状态: PENDING/RUNNING/COMPLETED/FAILED';
COMMENT ON COLUMN reconciliation_task.total_count IS '总对账笔数';
COMMENT ON COLUMN reconciliation_task.match_count IS '匹配成功笔数';
COMMENT ON COLUMN reconciliation_task.difference_count IS '差异笔数';
COMMENT ON COLUMN reconciliation_task.total_order_amount IS '订单总金额(基币)';
COMMENT ON COLUMN reconciliation_task.total_payment_amount IS '支付总金额(基币)';
COMMENT ON COLUMN reconciliation_task.total_difference IS '总差异金额(基币)';

CREATE TABLE IF NOT EXISTS reconciliation_difference (
    id                      BIGSERIAL PRIMARY KEY,
    task_id                 BIGINT NOT NULL,
    order_no                VARCHAR(64),
    payment_transaction_id  VARCHAR(64),
    difference_type         VARCHAR(30) NOT NULL,
    order_amount            DECIMAL(18, 2),
    order_currency          VARCHAR(10),
    payment_amount          DECIMAL(18, 2),
    payment_currency        VARCHAR(10),
    order_amount_in_base    DECIMAL(18, 6),
    payment_amount_in_base  DECIMAL(18, 6),
    amount_difference       DECIMAL(18, 6),
    exchange_rate_used      DECIMAL(18, 6),
    order_quantity          INTEGER,
    payment_quantity        INTEGER,
    quantity_difference     INTEGER,
    remark                  TEXT,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted                 INTEGER DEFAULT 0
);

COMMENT ON TABLE reconciliation_difference IS '对账差异明细表';
COMMENT ON COLUMN reconciliation_difference.difference_type IS '差异类型: AMOUNT_MISMATCH/QUANTITY_MISMATCH/ORDER_ONLY/PAYMENT_ONLY/EXCHANGE_RATE_MISMATCH';
COMMENT ON COLUMN reconciliation_difference.order_amount_in_base IS '订单金额折基币';
COMMENT ON COLUMN reconciliation_difference.payment_amount_in_base IS '支付金额折基币';
COMMENT ON COLUMN reconciliation_difference.amount_difference IS '金额差异';

CREATE TABLE IF NOT EXISTS exchange_rate (
    id              BIGSERIAL PRIMARY KEY,
    source_currency VARCHAR(10) NOT NULL,
    target_currency VARCHAR(10) NOT NULL,
    rate            DECIMAL(18, 6) NOT NULL,
    rate_date       DATE NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         INTEGER DEFAULT 0
);

COMMENT ON TABLE exchange_rate IS '汇率表';
COMMENT ON COLUMN exchange_rate.source_currency IS '源货币';
COMMENT ON COLUMN exchange_rate.target_currency IS '目标货币';
COMMENT ON COLUMN exchange_rate.rate IS '汇率';
COMMENT ON COLUMN exchange_rate.rate_date IS '汇率日期';

CREATE INDEX idx_reconciliation_task_dates ON reconciliation_task (start_date, end_date);
CREATE INDEX idx_reconciliation_task_status ON reconciliation_task (status);
CREATE INDEX idx_reconciliation_difference_task ON reconciliation_difference (task_id);
CREATE INDEX idx_reconciliation_difference_type ON reconciliation_difference (difference_type);
CREATE INDEX idx_exchange_rate_lookup ON exchange_rate (source_currency, target_currency, rate_date);
