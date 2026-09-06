# Deployment & Setup Guide

Portal SSO is engineered with a **Jenkins-style self-hosting philosophy**:
- **Instant 10-Second Setup**: Run standalone or with Docker using a single command. Zero manual database setup required for local evaluation.
- **First-Run Web Setup Wizard**: No digging through hundreds of lines of console logs to hunt for a temporary password. Open your browser and set your root credentials in seconds.
- **1-Click Dynamic Database Migration**: Start on embedded H2 and migrate to production PostgreSQL or MySQL directly within the web UI with zero downtime and automatic hot-restart.

---

## 1. Setup with Docker

### Option A: Single Container (Instant Trial & Evaluation)
Run Portal SSO with its embedded persistent database:

```bash
docker run -d \
  --name portal-sso \
  --restart unless-stopped \
  -p 8080:8080 \
  -v portal-sso-data:/root/.portal-sso \
  tanmaysinghx/portal-sso:latest
```

Open `http://localhost:8080` in your browser. The **First-Run Setup Wizard** will guide you through creating your initial administrator account.

---

### Option B: Docker with External MySQL / MariaDB
Portal SSO **automatically detects** MySQL from your connection string, sets `TINYINT` boolean mappings, and relaxes managed primary-key restrictions without needing manual profile flags:

```bash
docker run -d \
  --name portal-sso \
  --restart unless-stopped \
  -p 8080:8080 \
  -e DB_URL='jdbc:mysql://your-mysql-host:3306/portalsso?ssl-mode=REQUIRED' \
  -e DB_USERNAME='your_username' \
  -e DB_PASSWORD='your_password' \
  -e ISSUER_URL='https://sso.yourdomain.com' \
  -e FORWARD_HEADERS_STRATEGY='FRAMEWORK' \
  -v portal-sso-data:/root/.portal-sso \
  tanmaysinghx/portal-sso:latest
```

---

### Option C: Production Docker Compose (PostgreSQL)
For a complete, self-contained production deployment with PostgreSQL:

```yaml
# compose.yaml
services:
  portal-sso:
    image: tanmaysinghx/portal-sso:latest
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:postgresql://db:5432/portalsso
      DB_USERNAME: portal
      DB_PASSWORD: ${DB_PASSWORD:-change_this_secure_password}
      ISSUER_URL: ${ISSUER_URL:-http://localhost:8080}
      SERVER_SERVLET_SESSION_COOKIE_SECURE: ${COOKIE_SECURE:-true}
      FORWARD_HEADERS_STRATEGY: ${FORWARD_HEADERS_STRATEGY:-FRAMEWORK}
    depends_on:
      db:
        condition: service_healthy
    volumes:
      - portal-sso-data:/root/.portal-sso

  db:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: portalsso
      POSTGRES_USER: portal
      POSTGRES_PASSWORD: ${DB_PASSWORD:-change_this_secure_password}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U portal -d portalsso"]
      interval: 5s
      timeout: 5s
      retries: 5
    volumes:
      - portal-db-data:/var/lib/postgresql/data

volumes:
  portal-sso-data:
  portal-db-data:
```

Launch with:
```bash
docker compose up -d
```

---

## 2. Setup without Docker (Standalone FAT JAR)

Portal SSO is distributed as a self-contained executable fat JAR containing both the Spring Boot authorization engine and the Angular administration console.

### Prerequisites
- **Java 25+** (`openjdk-25-jre` or Temurin JDK 25)

### Download and Run
```bash
# 1. Download the executable JAR
curl -LO https://github.com/tanmaysinghx/portal-sso/releases/latest/download/portal-sso.jar

# 2. Run Portal SSO (defaults to port 8080 and ~/.portal-sso)
java -jar portal-sso.jar
```

### Supported CLI Flags
You can configure runtime options directly via Jenkins-style command-line arguments:

| Flag | Example | Description |
| :--- | :--- | :--- |
| `--httpPort` | `--httpPort=8090` | HTTP listening port (default: `8080`). |
| `--httpListenAddress` | `--httpListenAddress=0.0.0.0` | Bind IP address. |
| `--portalHome` | `--portalHome=/var/lib/portal-sso` | Directory where database, keys, and `portal.properties` reside. |
| `--prefix` | `--prefix=/auth` | Servlet context path prefix when reverse-proxying behind a subpath. |

Example running on port 8090 in custom directory:
```bash
java -jar portal-sso.jar --httpPort=8090 --portalHome=/var/lib/portal-sso
```

---

### Production Systemd Service (Linux)

Create `/etc/systemd/system/portal-sso.service`:

```ini
[Unit]
Description=Portal SSO Identity Provider
After=network.target

[Service]
Type=simple
User=portal
Group=portal
WorkingDirectory=/var/lib/portal-sso
ExecStart=/usr/bin/java -XX:+UseZGC -XX:MaxRAMPercentage=75.0 -jar /opt/portal-sso/portal-sso.jar --httpPort=8080 --portalHome=/var/lib/portal-sso
Restart=always
RestartSec=5
Environment="PORTAL_HOME=/var/lib/portal-sso"

# Security sandboxing
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/portal-sso
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

Enable and start the service:
```bash
sudo useradd -r -s /bin/false -d /var/lib/portal-sso portal
sudo mkdir -p /var/lib/portal-sso /opt/portal-sso
sudo chown -R portal:portal /var/lib/portal-sso /opt/portal-sso
sudo cp portal-sso.jar /opt/portal-sso/
sudo systemctl daemon-reload
sudo systemctl enable --now portal-sso
```

---

## 3. First-Run Web Setup Wizard

When Portal SSO boots for the first time on a new database:
1. Open `http://your-server-ip:8080` in your web browser.
2. The page automatically displays the **First-Run Setup Wizard**.
3. Fill in:
   - **Administrator Email** (e.g. `admin@company.com`)
   - **First & Last Name**
   - **Password** (minimum 12 characters, complying with enterprise password policy)
4. Click **"Complete Setup & Sign In"**.
5. You are immediately authenticated into the Admin Console. The setup endpoint locks down permanently.

---

## 4. Live Database Migration & 1-Click Restart

If you started on the local embedded database and are ready to move to production MySQL or PostgreSQL:

1. Log in to the Admin Console and go to **Settings → Database**.
2. Select your target engine (**PostgreSQL** or **MySQL / MariaDB**).
3. Provide your connection credentials or custom JDBC URL.
4. Click **"Test Connection"** to verify network connectivity.
5. Click **"Migrate to Target Database"**. Portal SSO runs Liquibase migrations on the target and copies all records (users, roles, OAuth clients, keys) with zero data loss.
6. Click **"1-Click Switch & Restart"**. The application writes `portal.properties` and triggers an in-memory hot reload. The UI displays a 6-second reconnect countdown and automatically reloads connected to your new production database!

---

## 5. Reverse Proxy Configuration (Nginx / Cloudflare)

When running behind a reverse proxy or load balancer terminating SSL:

1. Set `FORWARD_HEADERS_STRATEGY=FRAMEWORK` (or `--forward-headers-strategy=FRAMEWORK`).
2. Configure your Nginx virtual host:

```nginx
server {
    listen 443 ssl http2;
    server_name sso.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/sso.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sso.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Port $server_port;
    }
}
```

---

## 6. How to Reset and Start Fresh (Docker)

To wipe only Portal SSO and start completely fresh without affecting other containers (like Jenkins):

```bash
# 1. Stop and remove the container
docker rm -f portal-sso-dev portal-sso 2>/dev/null || true

# 2. Remove stored data volume
docker volume rm portal-sso-dev-data portal-sso-data 2>/dev/null || true

# 3. Pull latest image and run
docker run -d \
  --name portal-sso \
  --restart unless-stopped \
  -p 8080:8080 \
  -v portal-sso-data:/root/.portal-sso \
  tanmaysinghx/portal-sso:latest
```
