-- liquibase formatted sql

-- changeset hiep:002-seed-auction-categories
-- comment: Seed 10 top-level auction categories
INSERT INTO auction_categories (id, name, slug, description, display_order, is_active)
VALUES (gen_random_uuid(), 'Electronics', 'electronics', 'Smartphones, laptops, cameras, and other electronic devices',
        1, TRUE),
       (gen_random_uuid(), 'Collectibles & Art', 'collectibles-art',
        'Fine art, coins, stamps, memorabilia, and rare collectibles', 2, TRUE),
       (gen_random_uuid(), 'Fashion & Accessories', 'fashion-accessories',
        'Clothing, shoes, handbags, and fashion accessories', 3, TRUE),
       (gen_random_uuid(), 'Jewelry & Watches', 'jewelry-watches', 'Fine jewelry, luxury watches, and gemstones', 4,
        TRUE),
       (gen_random_uuid(), 'Home & Garden', 'home-garden', 'Furniture, decor, appliances, and garden equipment', 5,
        TRUE),
       (gen_random_uuid(), 'Sports & Outdoors', 'sports-outdoors',
        'Sports equipment, outdoor gear, and fitness accessories', 6, TRUE),
       (gen_random_uuid(), 'Vehicles & Motors', 'vehicles-motors', 'Cars, motorcycles, boats, and vehicle parts', 7,
        TRUE),
       (gen_random_uuid(), 'Books, Music & Media', 'books-music-media',
        'Books, vinyl records, CDs, DVDs, and digital media', 8, TRUE),
       (gen_random_uuid(), 'Antiques & Vintage', 'antiques-vintage',
        'Pre-owned antiques, vintage items, and historical artifacts', 9, TRUE),
       (gen_random_uuid(), 'Real Estate', 'real-estate', 'Residential, commercial, and land property auctions', 10,
        TRUE);
-- rollback DELETE FROM auction_categories WHERE slug IN ('electronics','collectibles-art','fashion-accessories','jewelry-watches','home-garden','sports-outdoors','vehicles-motors','books-music-media','antiques-vintage','real-estate');
