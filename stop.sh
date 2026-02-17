#!/usr/bin/env bash

# Stop Wikipedia Edit War Monitor services

set -e

echo "🛑 Stopping Wikipedia Edit War Monitor services..."
echo ""

# Stop all services
echo "📊 Stopping all services..."
docker-compose down

echo ""
echo "✅ All services stopped"
echo ""
echo "Additional commands:"
echo "  Remove all data (including traces):  docker-compose down -v"
echo "  View stopped containers:             docker-compose ps -a"
echo "  Remove built images:                 docker-compose down --rmi local"
