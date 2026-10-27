# Deployment

The API runs on a Forge server as a daemon behind Nginx, at `https://api-where-is-my-car.josrom.io`. GitHub Actions (`.github/workflows/ci-deploy.yml`) tests every push and pull request, and on `main` it builds the jar, uploads it to the server and restarts the daemon. The server never compiles anything.

## One time server setup

1. **Java**: `openjdk-25-jre-headless` (Ubuntu package).
2. **Site**: create `api-where-is-my-car.josrom.io` in Forge as a static site, without repository.
3. **Database**: create the `whereismycar` database and a user in Forge.
4. **Firebase credentials**: upload the service account key as `firebase-credentials.json` to the site directory and run `chmod 600` on it.
5. **Environment**: create `.env` in the site directory from `env.production.example`.
6. **Daemon** (Server, Daemons), user `forge`, directory `/home/forge/api-where-is-my-car.josrom.io`:

   ```bash
   bash -c 'set -a && . ./.env && exec java -Xms64m -Xmx160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=48m -XX:MaxMetaspaceSize=160m -Xss512k -jar app.jar'
   ```

   `set -a` exports the `.env` as real environment variables, which Spring maps to its properties (for example `SPRING_DATASOURCE_URL`). The JVM flags keep the API around 280 MB of memory.

7. **Nginx**: apply `nginx.conf`, then enable Let's Encrypt in Forge.
8. **Daemon restart without password**, for the deploy job (Recipe run as root):

   ```bash
   echo 'forge ALL=(root) NOPASSWD: /usr/bin/supervisorctl restart daemon-*' > /etc/sudoers.d/forge-daemons
   chmod 440 /etc/sudoers.d/forge-daemons
   visudo -c
   ```

## GitHub secrets

In the repository, Settings, Secrets and variables, Actions (environment `production`):

| Secret | Value |
|---|---|
| `DEPLOY_SSH_KEY` | Private key of a key pair used only for deploys. Add its public key in Forge, Server, SSH Keys, for the `forge` user |
| `DEPLOY_HOST` | Server IP |
| `DEPLOY_KNOWN_HOSTS` | Output of `ssh-keyscan -t ed25519 <server IP>` |
| `DEPLOY_PATH` | `/home/forge/api-where-is-my-car.josrom.io` |
| `DAEMON_NAME` | Daemon id shown in Forge, for example `daemon-123456` |

The server IP and host key are secrets on purpose: the repository is public, and with the Cloudflare proxy enabled the origin IP should stay hidden.

## Rollback

The previous jar is kept as `app.jar.previous`. To go back: `mv app.jar.previous app.jar` in the site directory and restart the daemon.

## Database schema

`spring.jpa.hibernate.ddl-auto=update` creates the tables on the first start, but it never changes the type or size of an existing column. Until migrations (Flyway) are added, apply those changes by hand with `ALTER TABLE`.
