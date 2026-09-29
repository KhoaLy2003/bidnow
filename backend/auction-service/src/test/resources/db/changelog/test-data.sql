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

-- changeset bidnow:bdd-bid-auctions
-- comment: Auctions reserved for internal-bid-api.feature (Epic #120 Story 1)
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000005'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Open Auction', 'Open auction for bid BDD',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        500.00, 50.00, 100.00, 500.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000006'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Expired Auction', 'Active auction whose end time has passed',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        200.00, 20.00, 40.00, 200.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 minute', NOW() - INTERVAL '1 minute',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000007'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Same-Price Race Auction', 'Concurrent same-price bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        900.00, 10.00, 100.00, 1000.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-000000000008'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Close Race Auction', 'Closure racing concurrent bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        90.00, 1.00, 10.00, 100.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()) ON CONFLICT (id) DO NOTHING;

-- changeset bidnow:bdd-bid-lock-timeout-auction
-- comment: Auctions for lock-timeout and close-race rounds in internal-bid-api.feature
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-000000000009'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Lock Timeout Auction', 'Row lock held by a background transaction',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        500.00, 50.00, 100.00, 500.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-00000000000a'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Close Race Auction A', 'Closure racing concurrent bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        90.00, 1.00, 10.00, 100.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-00000000000b'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Close Race Auction B', 'Closure racing concurrent bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        90.00, 1.00, 10.00, 100.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-00000000000c'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Bid Close Race Auction C', 'Closure racing concurrent bids',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        90.00, 1.00, 10.00, 100.00, 1,
        '550e8400-e29b-41d4-a716-446655440002'::uuid,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()) ON CONFLICT (id) DO NOTHING;

-- changeset bidnow:bdd-anti-snipe-auctions
-- comment: Auctions for anti-sniping BDD (end times are moved by steps at runtime)
INSERT INTO auction_items (id, seller_id, title, description, category_id,
                           starting_price, bid_increment, deposit_amount,
                           current_price, total_bids, current_winner_id,
                           status, start_time, end_time, original_end_time,
                           created_at, updated_at)
VALUES ('b0000000-0000-0000-0000-00000000000d'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Anti-Snipe Auction', 'Anti-sniping extension',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        100.00, 10.00, 20.00, 100.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()),

       ('b0000000-0000-0000-0000-00000000000e'::uuid,
        '550e8400-e29b-41d4-a716-446655440001'::uuid,
        'BDD Deferred Closure Auction', 'Closure after extension',
        'a0000000-0000-0000-0000-000000000001'::uuid,
        100.00, 10.00, 20.00, 100.00, 0, NULL,
        'ACTIVE',
        NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', NOW() + INTERVAL '7 days',
        NOW(), NOW()) ON CONFLICT (id) DO NOTHING;
