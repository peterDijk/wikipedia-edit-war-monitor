#!/usr/bin/env bash

# Start Wikipedia Edit War Monitor with Tracing
# This script builds and starts both Jaeger and the Scala app in Docker

set -e

echo "🚀 Starting Wikipedia Edit War Monitor with Tracing"
echo ""

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
  echo "❌ Error: Docker is not running. Please start Docker Desktop."
  exit 1
fi

echo "🔨 Building and starting services..."
echo "   This may take a few minutes on first run (downloading dependencies)"
echo ""

# Build and start all services
docker-compose up -d --build

echo ""
echo "⏳ Waiting for services to be ready..."

# Wait for Jaeger to be healthy
timeout=60
counter=0
until docker-compose exec -T jaeger wget --spider -q http://localhost:16686 2>/dev/null; do
  sleep 2
  counter=$((counter + 2))
  if [ $counter -ge $timeout ]; then
    echo "⚠️  Warning: Jaeger health check timed out"
    break
  fi
  echo -n "."
done

echo ""
echo ""
echo "✅ Services are running!"
echo ""
echo "📊 Jaeger UI:        http://localhost:16686"
echo "🌐 Application API:  http://localhost:8080"
echo ""
echo "📝 Useful commands:"
echo "   View logs:           docker-compose logs -f"
echo "   View app logs:       docker-compose logs -f wikipedia-monitor"
echo "   View Jaeger logs:    docker-compose logs -f jaeger"
echo "   Stop services:       ./stop.sh"
echo "   Restart app:         docker-compose restart wikipedia-monitor"
echo ""
echo "Press Ctrl+C to view logs (services will keep running)"
echo ""

# Follow logs (can be stopped with Ctrl+C)
docker-compose logs -f

