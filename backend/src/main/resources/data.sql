INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Kubernetes DevOps Concert', 20, 20
WHERE NOT EXISTS (
    SELECT 1
    FROM ticket_event
    WHERE name = 'Kubernetes DevOps Concert'
);
