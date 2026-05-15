#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE identity_db;
    CREATE DATABASE user_db;
    CREATE DATABASE auction_db;
    CREATE DATABASE bidding_db;
    CREATE DATABASE wallet_db;
    CREATE DATABASE notification_db;
EOSQL
