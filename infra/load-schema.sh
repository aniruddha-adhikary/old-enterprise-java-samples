#!/bin/bash
# Load Oracle schema and sample data into the bigcorp-oracle container.
# Run after start-infra.sh and after Oracle is ready.
#
# Usage: ./infra/load-schema.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCHEMA_FILE="$SCRIPT_DIR/../sql/schema-oracle.sql"

echo "Loading Oracle schema from $SCHEMA_FILE..."

docker cp "$SCHEMA_FILE" bigcorp-oracle:/tmp/schema-oracle.sql

docker exec bigcorp-oracle bash -c '
cat /tmp/schema-oracle.sql | sqlplus -s bigcorp_app/bigcorp_2002@//localhost:1521/XEPDB1
'

echo ""
echo "Verifying data..."
docker exec bigcorp-oracle bash -c '
sqlplus -s bigcorp_app/bigcorp_2002@//localhost:1521/XEPDB1 <<EOSQL
SELECT COUNT(*) AS CLIENT_COUNT FROM CLIENTS;
SELECT COUNT(*) AS PRICING_COUNT FROM PRICING_CACHE;
EOSQL
'

echo "Schema loaded successfully."
