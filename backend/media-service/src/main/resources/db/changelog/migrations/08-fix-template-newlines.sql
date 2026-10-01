-- liquibase formatted sql

-- changeset hiep.nguyen:fix-seeded-template-newlines
-- comment: 02/03 stored '\n' as a literal backslash-n (standard_conforming_strings); convert to real newlines
UPDATE media_notification_templates
SET body_text = REPLACE(body_text, E'\\n', E'\n')
WHERE body_text LIKE E'%\\\\n%';
