-- liquibase formatted sql

-- changeset bidnow:bdd-test-category
-- comment: Fixed-UUID category for BDD test data references
INSERT INTO auction_categories (id, name, slug, description, display_order, is_active)
VALUES ('a0000000-0000-0000-0000-000000000001'::uuid,
        'BDD Test Category', 'bdd-test-category', 'Category for BDD tests', 99, TRUE) ON CONFLICT (id) DO NOTHING;

-- changeset bidnow:bdd-test-auctions
-- comment: Seed four auctions in different statuses for BDD scenario coverage
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000001'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Draft Auction', 'A draft auction for BDD tests',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        100.00, 10.00, 20.00, 100.00, 0, NULL,
        'DRAFT',
        NOW() + INTERVAL '1 day', NOW() + INTERVAL '8 days', NOW() + INTERVAL '8 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000002'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Scheduled Auction', 'A scheduled auction for BDD tests',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        200.00, 20.00, 40.00, 200.00, 0, NULL,
        'SCHEDULED',
        NOW() + INTERVAL '1 day', NOW() + INTERVAL '8 days', NOW() + INTERVAL '8 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000003'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Active Auction', 'An active auction for BDD tests',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        300.00, 30.00, 60.00, 330.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000004'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Cancelled Auction', 'A cancelled auction for BDD tests',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        150.00, 15.00, 30.00, 150.00, 0, NULL,
        'CANCELLED',
        NOW() - INTERVAL '2 days', NOW() + INTERVAL '5 days', NOW() + INTERVAL '5 days',
        NOW(), NOW()) ON CONFLICT (id) DO NOTHING;
