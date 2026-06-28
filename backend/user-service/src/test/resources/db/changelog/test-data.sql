-- liquibase formatted sql

-- changeset bidnow:test-seed-user-profiles
-- comment: Fixed BDD test profiles. @Before resets display_name rather than deleting, so these rows persist across scenarios.
INSERT INTO user_profiles (user_id, display_name, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440001'::uuid, 'Test User Alice', NOW(), NOW()),
       ('550e8400-e29b-41d4-a716-446655440002'::uuid, 'Test User Bob', NOW(), NOW()) ON CONFLICT (user_id) DO NOTHING;
