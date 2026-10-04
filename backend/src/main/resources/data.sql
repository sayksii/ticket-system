INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Kubernetes DevOps Concert', 20, 20
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_event WHERE name = 'Kubernetes DevOps Concert'
);

INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Cloud Native Summit', 120, 120
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_event WHERE name = 'Cloud Native Summit'
);

INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Spring Boot Workshop', 80, 80
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_event WHERE name = 'Spring Boot Workshop'
);

INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Indie Music Night', 60, 60
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_event WHERE name = 'Indie Music Night'
);

INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Future Design Expo', 100, 100
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_event WHERE name = 'Future Design Expo'
);

INSERT INTO ticket_event(name, total_stock, stock)
SELECT 'Weekend Jazz Market', 40, 40
WHERE NOT EXISTS (
    SELECT 1 FROM ticket_event WHERE name = 'Weekend Jazz Market'
);
