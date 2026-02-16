# Docker Quick Reference

## 🚀 Start/Stop

```bash
./start.sh    # Start everything (builds app, starts services)
./stop.sh     # Stop everything
```

## 🌐 Access Points

- **Jaeger UI:** http://localhost:16686
- **Application:** http://localhost:8080
  - `/hello/:name` - Hello endpoint
  - `/joke` - Random joke

## 📊 View Logs

```bash
# All services
docker-compose logs -f

# Just the app
docker-compose logs -f wikipedia-monitor

# Just Jaeger
docker-compose logs -f jaeger

# Last 100 lines
docker-compose logs --tail=100 wikipedia-monitor
```

## 🔄 Restart After Code Changes

```bash
# Rebuild and restart the app
docker-compose up -d --build wikipedia-monitor

# Watch logs
docker-compose logs -f wikipedia-monitor
```

## 🔍 Debugging

```bash
# Check status
docker-compose ps

# Get into container
docker-compose exec wikipedia-monitor bash

# Check resources
docker stats wikipedia-monitor

# Check environment
docker-compose exec wikipedia-monitor env | grep OTEL
```

## 🧹 Cleanup

```bash
# Stop containers
docker-compose down

# Stop + remove volumes (deletes traces)
docker-compose down -v

# Stop + remove images
docker-compose down --rmi local
```

## 🛠️ Development Workflow

1. Make code changes
2. `docker-compose up -d --build wikipedia-monitor`
3. `docker-compose logs -f wikipedia-monitor`
4. View traces at http://localhost:16686

## 📦 What's Running

| Service | Container Name | Ports | Purpose |
|---------|---------------|-------|---------|
| wikipedia-monitor | wikipedia-monitor | 8080 | Scala app |
| jaeger | jaeger | 16686, 4317, 4318, 5778, 9411 | Tracing |

## ⚙️ Configuration

OpenTelemetry environment variables (set in docker-compose.yml):
- `OTEL_SERVICE_NAME=WikipediaEditWarMonitor`
- `OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317`
- `OTEL_TRACES_EXPORTER=otlp`

## 🆘 Troubleshooting

**App won't start?**
```bash
docker-compose logs wikipedia-monitor
```

**No traces?**
```bash
# Check app can reach Jaeger
docker-compose exec wikipedia-monitor ping jaeger

# Check Jaeger logs
docker-compose logs jaeger
```

**Port already in use?**
```bash
lsof -i :8080  # or :16686
```

## 📚 More Info

- Full guide: [docs/docker-deployment.md](docker-deployment.md)
- Tracing guide: [docs/running-with-tracing.md](running-with-tracing.md)

