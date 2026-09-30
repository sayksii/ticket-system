CREATE TABLE IF NOT EXISTS ticket_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    total_stock INT NOT NULL,
    stock INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_ticket_order_event_user
        UNIQUE (event_id, user_id),

    CONSTRAINT fk_ticket_order_event
        FOREIGN KEY (event_id)
        REFERENCES ticket_event(id)
);
