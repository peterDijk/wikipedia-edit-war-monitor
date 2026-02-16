# Docker Deployment Guide

## Overview

The Wikipedia Edit War Monitor is fully containerized with Docker Compose, including:
- **Scala Application** - Wikipedia event stream processor
- **Jaeger** - Distributed tracing backend

Both services run in containers with automatic health checks, networking, and tracing integration.

---

## Quick Start

### 1. Start Everything

```bash
./start.sh
```

This will:
1. Build the Scala application Docker image
2. Start Jaeger for tracing
3. Start the Scala application
4. Show logs from both services

**First run** will take several minutes to:
- Download Scala/SBT base images
- Download all dependencies
- Compile the application
- Build the JAR file

**Subsequent runs** are much faster (uses Docker layer caching).

### 2. Access Services

- **Jaeger UI:** http://localhost:16686 (view traces)
- **Application API:** http://localhost:8080 (your app endpoints)
  - http://localhost:8080/hello/world
  - http://localhost:8080/joke

### 3. Stop Everything

```bash
./stop.sh
```

---

## Docker Compose Configuration

### Services

#### wikipedia-monitor (Scala Application)
```yaml
- Build: Multi-stage Docker build
- Port: 8080 (HTTP server)
- Depends on: Jaeger (waits for health check)
- Tracing: Sends traces to Jaeger via OTLP gRPC (port 4317)
```

#### jaeger (Tracing Backend)
```yaml
- Image: cr.jaegertracing.io/jaegertracing/jaeger:2.15.0
- Ports: 16686 (UI), 4317 (OTLP gRPC), 4318 (OTLP HTTP), etc.
- Health check: Ensures Jaeger is ready before starting app
```

### Networking

Both services are on the `tracing` bridge network, allowing:
- App sends traces to `http://jaeger:4317` (internal DNS)
- Services can communicate by container name
- Isolated from other Docker networks

---

## Dockerfile Details

The application uses a **multi-stage build** for optimization:

### Stage 1: Builder
- Base: `sbtscala/scala-sbt:eclipse-temurin-jammy-21.0.5_11_1.10.6_3.3.7`
- Downloads dependencies (cached layer)
- Compiles source code
- Creates fat JAR with `sbt assembly`

### Stage 2: Runtime
- Base: `eclipse-temurin:21-jre-jammy` (smaller, JRE only)
- Copies only the assembled JAR
- Sets OpenTelemetry environment variables
- Exposes port 8080
- Runs the application

**Image sizes:**
- Builder stage: ~2GB (not included in final image)
- Runtime image: ~400MB

---

## Docker Commands

### Basic Operations

```bash
# Start all services (build if needed)
docker-compose up -d --build

# Start without building
docker-compose up -d

# Stop all services
docker-compose down

# View logs (all services)
docker-compose logs -f

# View app logs only
docker-compose logs -f wikipedia-monitor

# View Jaeger logs only
docker-compose logs -f jaeger
```

### Service Management

```bash
# Restart just the app (after code changes)
docker-compose restart wikipedia-monitor

# Rebuild and restart app
docker-compose up -d --build wikipedia-monitor

# Stop app only (keep Jaeger running)
docker-compose stop wikipedia-monitor

# Start app only
docker-compose start wikipedia-monitor
```

### Development Workflow

```bash
# 1. Make code changes
# 2. Rebuild and restart
docker-compose up -d --build wikipedia-monitor

# 3. Watch logs
docker-compose logs -f wikipedia-monitor
```

### Debugging

```bash
# Check service status
docker-compose ps

# Check service health
docker-compose ps wikipedia-monitor

# Exec into app container
docker-compose exec wikipedia-monitor /bin/bash

# View container resource usage
docker stats wikipedia-monitor

# Inspect container
docker inspect wikipedia-monitor
```

### Cleanup

```bash
# Stop and remove containers
docker-compose down

# Remove containers and volumes (deletes trace data)
docker-compose down -v

# Remove containers, volumes, and images
docker-compose down -v --rmi local

# Full cleanup (remove everything)
docker-compose down -v --rmi all
docker system prune -a
```

---

## Environment Variables

The app container has these OpenTelemetry variables set:

```yaml
OTEL_SERVICE_NAME=WikipediaEditWarMonitor
OTEL_TRACES_EXPORTER=otlp
OTEL_EXPORTER_OTLP_ENDPOINT=http://jaeger:4317
OTEL_METRICS_EXPORTER=none
OTEL_LOGS_EXPORTER=none
```

To override, edit `docker-compose.yml` or create a `.env` file:

```bash
# .env file
OTEL_SERVICE_NAME=MyCustomName
```

---

## Development vs Production

### Development (Current Setup)

✅ Automatic restart on failure
✅ Hot reload with rebuild (`docker-compose up -d --build`)
✅ Full logging enabled
✅ No resource limits

### Production Recommendations

Add to `docker-compose.yml`:

```yaml
wikipedia-monitor:
  # ... existing config ...
  deploy:
    resources:
      limits:
        cpus: '2.0'
        memory: 2G
      reservations:
        cpus: '1.0'
        memory: 1G
  logging:
    driver: "json-file"
    options:
      max-size: "10m"
      max-file: "3"
  environment:
    # Add production configs
    - OTEL_TRACES_SAMPLER=traceidratio
    - OTEL_TRACES_SAMPLER_ARG=0.1  # Sample 10%
```

---

## Troubleshooting

### Build fails

**Issue:** `sbt assembly` fails during Docker build

**Solutions:**
1. Check sbt-assembly plugin in `project/plugins.sbt`
2. Ensure `build.sbt` is valid
3. Try building locally first: `sbt assembly`
4. Check Docker has enough memory (4GB+ recommended)

```bash
# View build logs
docker-compose build --no-cache wikipedia-monitor
```

### App won't start

**Issue:** Container exits immediately

**Solutions:**
1. Check logs: `docker-compose logs wikipedia-monitor`
2. Check if port 8080 is already in use
3. Verify JAR was created: `docker-compose run wikipedia-monitor ls -la`

### No traces in Jaeger

**Issue:** Jaeger UI shows no traces

**Solutions:**
1. Verify app started: `docker-compose ps`
2. Check app logs for OpenTelemetry errors
3. Verify Jaeger endpoint: `docker-compose exec wikipedia-monitor env | grep OTEL`
4. Test connectivity: `docker-compose exec wikipedia-monitor ping jaeger`

### Slow build times

**Issue:** Docker build takes too long

**Solutions:**
1. Use `.dockerignore` to exclude unnecessary files
2. Leverage Docker layer caching (don't change `build.sbt` frequently)
3. Build locally and copy JAR (faster for development)

**Alternative Dockerfile for local builds:**
```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/scala-3.3.7/*-assembly*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Then build locally and use simple Dockerfile:
```bash
sbt assembly
docker-compose up -d --build
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build and Push

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker image
        run: docker-compose build wikipedia-monitor
      
      - name: Start services
        run: docker-compose up -d
      
      - name: Wait for health check
        run: sleep 30
      
      - name: Test endpoint
        run: curl -f http://localhost:8080/hello/world
      
      - name: View traces
        run: curl http://localhost:16686/api/traces?service=WikipediaEditWarMonitor
```

---

## Performance Tips

### Build Optimization

1. **Cache dependencies:** Don't modify `build.sbt` unnecessarily
2. **Use `.dockerignore`:** Exclude `target/`, `.git/`, etc.
3. **Multi-stage build:** Already implemented (keeps runtime image small)

### Runtime Optimization

1. **JVM heap size:**
   ```yaml
   environment:
     - JAVA_OPTS=-Xmx1g -Xms512m
   entrypoint: ["java", "-Xmx1g", "-Xms512m", "-jar", "app.jar"]
   ```

2. **Parallel GC:**
   ```yaml
   entrypoint: ["java", "-XX:+UseParallelGC", "-jar", "app.jar"]
   ```

3. **Resource limits:** Set CPU/memory limits (see Production section)

---

## Useful Commands Reference

```bash
# Development
./start.sh                                    # Start everything
./stop.sh                                     # Stop everything
docker-compose logs -f wikipedia-monitor      # Watch app logs
docker-compose restart wikipedia-monitor      # Restart app

# Debugging
docker-compose ps                             # Service status
docker-compose exec wikipedia-monitor bash    # Shell into container
docker-compose logs --tail=100 wikipedia-monitor  # Last 100 lines

# Cleanup
docker-compose down                           # Stop containers
docker-compose down -v                        # Stop + remove volumes
docker system prune                           # Clean up Docker
```

---

## Next Steps

1. ✅ Services are containerized
2. ✅ Tracing is integrated
3. 🔄 Add health check endpoint to app
4. 🔄 Add monitoring (Prometheus/Grafana)
5. 🔄 Deploy to Kubernetes
6. 🔄 Set up CI/CD pipeline

The application is now fully containerized and ready for deployment! 🚀

