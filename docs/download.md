# Downloading and Installing Portal SSO (Jenkins LTS Style)

Portal SSO can be installed in multiple ways:
1. **Generic Executable JAR (Recommended, Zero-Setup)**
2. **One-Line Installer Script (`curl | bash`)**
3. **Linux Systemd Service**
4. **Docker Container**

---

## 1. Generic Executable Package (`portal-sso.jar`)

Similar to Jenkins LTS (`jenkins.war`), Portal SSO distributes a single, self-contained executable JAR that packages both the OAuth2/OIDC Auth Server and the Angular SPA Admin Console with an embedded persistent database engine.

### Prerequisites
- **Java 25+** (Eclipse Temurin, OpenJDK, or Oracle JDK)
  ```bash
  java -version
  ```

### Direct Download
Download the latest LTS standalone JAR from GitHub Releases:
```bash
curl -LO https://github.com/tanmaysinghx/portal-sso/releases/latest/download/portal-sso.jar
```

### Running Standalone
Open a terminal in your download directory and execute:
```bash
java -jar portal-sso.jar
```

To run on a specific HTTP port (Jenkins CLI syntax supported):
```bash
java -jar portal-sso.jar --httpPort=8080
```

Additional CLI options:
| Option | Environment Variable | Default | Description |
|---|---|---|---|
| `--httpPort=PORT` | `SERVER_PORT` | `8080` | Web server listening port |
| `--httpListenAddress=ADDR` | `SERVER_ADDRESS` | `0.0.0.0` | Bind IP address |
| `--portalHome=DIR` | `PORTAL_HOME` | `~/.portal-sso` | Data and secrets directory |
| `--prefix=PATH` | `SERVER_SERVLET_CONTEXT_PATH` | `/` | Context path prefix |

### First-Run Unlock (initialAdminPassword)
When launching for the first time without pre-configured credentials, Portal SSO creates **no account**. It writes a one-time setup key to disk and prints a prominent banner:

```
*************************************************************
*************************************************************
Portal SSO initial setup is required.

Open http://localhost:8080/sign-in and enter this setup key to create
the first administrator:

  <43-character random key>

The key has also been written to:
  ~/.portal-sso/secrets/initialAdminPassword

It works once. If the server restarts before setup is
finished, a new key replaces it.
*************************************************************
*************************************************************
```

1. Open `http://localhost:8080` in your browser. The setup wizard appears in place of the sign-in form.
2. Paste the setup key, then enter your email, name and password (at least 12 characters; the form shows the rules as you type).
3. Select **Complete setup**. You are signed straight in to the dashboard, and the key file is deleted.

The wizard is closed for good once an administrator exists. To skip it entirely (for scripted or
clustered deployments), set `APP_BOOTSTRAP_ADMIN_EMAIL` and `APP_BOOTSTRAP_ADMIN_PASSWORD`; no key
is issued then. With `APP_BOOTSTRAP_AUTO_GENERATE=false` and neither set, the sign-in page explains
that setup is locked.

---

## 2. Automated One-Line Installer (`install.sh`)

You can download and install Portal SSO using the automated script:

```bash
curl -fsSL https://raw.githubusercontent.com/tanmaysinghx/portal-sso/main/install.sh | bash
```

The script:
- Verifies your Java 25 environment.
- Sets up `~/.portal-sso` with appropriate permissions (`0700` for secrets).
- Downloads or links the executable JAR.
- Prepares you to launch with a single command.

---

## 3. Production Linux Systemd Service

To run Portal SSO as a background daemon on Linux servers:

1. Create a dedicated system user:
   ```bash
   sudo useradd --system --user-group --shell /bin/false portal
   sudo mkdir -p /opt/portal-sso /var/lib/portal-sso
   sudo chown -R portal:portal /opt/portal-sso /var/lib/portal-sso
   ```

2. Place `portal-sso.jar` in `/opt/portal-sso/`:
   ```bash
   sudo cp portal-sso.jar /opt/portal-sso/
   sudo chown portal:portal /opt/portal-sso/portal-sso.jar
   ```

3. Install the systemd unit file:
   ```bash
   sudo cp distribution/portal-sso.service /etc/systemd/system/portal-sso.service
   sudo systemctl daemon-reload
   sudo systemctl enable --now portal-sso
   ```

4. Check the service status and view the setup key in `initialAdminPassword`:
   ```bash
   sudo systemctl status portal-sso
   sudo journalctl -u portal-sso -n 50
   sudo cat /var/lib/portal-sso/secrets/initialAdminPassword
   ```

---

## 4. Connecting to External Databases (PostgreSQL / MySQL)

By default, standalone Portal SSO uses an embedded persistent database in `${PORTAL_HOME}/data/portalsso`. 

For large-scale or clustered production deployments, supply standard JDBC connection settings:

```bash
java -jar portal-sso.jar \
  --spring.datasource.url=jdbc:postgresql://postgres.internal:5432/portalsso \
  --spring.datasource.username=portal \
  --spring.datasource.password=SecurePassword123
```
Or via environment variables:
```bash
export DB_URL=jdbc:postgresql://postgres.internal:5432/portalsso
export DB_USERNAME=portal
export DB_PASSWORD=SecurePassword123
java -jar portal-sso.jar
```
