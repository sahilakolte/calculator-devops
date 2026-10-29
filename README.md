# Calculator DevOps

A Calculator REST API built with **Spring Boot**. A push to GitHub triggers a Jenkins pipeline that builds, tests, containerizes, pushes to Docker Hub and deploys with Docker Compose. No step is manual.

```
GitHub ──(webhook / Poll SCM)──► Jenkins
                                   │ Checkout → Build → Test → Docker Build → Push → Deploy
                                   ▼                                   │          │
                              JUnit reports                       Docker Hub   docker compose up -d
                              Email notification                               │
                                                                               ▼
                                              client ──► Nginx :8090 ──► calculator ×2 (:8080)
```

## Tech stack

| Concern            | Tool                                      |
|--------------------|-------------------------------------------|
| Application        | Java 17, Spring Boot 3.5 (Web, Actuator)  |
| Build / tests      | Maven, JUnit 5, MockMvc                   |
| Version control    | Git, GitHub                               |
| CI/CD              | Jenkins declarative pipeline (`Jenkinsfile`) |
| Containerization   | Docker (multi-stage `Dockerfile`)         |
| Deployment         | Docker Compose (Nginx + 2 API replicas)   |

## API

Base path: `/api/v1`. All operations accept the query parameters `a` and `b` (numbers).

| Operation | Endpoint                         | Example response |
|-----------|----------------------------------|------------------|
| Add       | `GET /api/v1/add?a=2&b=3`        | `{"operation":"add","a":2.0,"b":3.0,"result":5.0}` |
| Subtract  | `GET /api/v1/subtract?a=5&b=3`   | `{"operation":"subtract","a":5.0,"b":3.0,"result":2.0}` |
| Multiply  | `GET /api/v1/multiply?a=6&b=7`   | `{"operation":"multiply","a":6.0,"b":7.0,"result":42.0}` |
| Divide    | `GET /api/v1/divide?a=7&b=2`     | `{"operation":"divide","a":7.0,"b":2.0,"result":3.5}` |
| Health    | `GET /actuator/health`           | `{"status":"UP",...}` |

### Errors

Invalid input returns **HTTP 400** with a structured body:

```json
{
  "timestamp": "2026-10-29T18:25:10.853Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Division by zero is not allowed",
  "path": "/api/v1/divide"
}
```

These inputs return 400: division by zero, a missing parameter (`?a=1`), a non-numeric value (`?a=abc`), `NaN` or `Infinity`, and a result that overflows.

## Build, test and run locally

Requirements: JDK 17, Maven 3.9 and Docker with the Compose plugin.

```bash
# Build the JAR and run all tests (reports in target/surefire-reports)
mvn clean package

# Run tests only
mvn test

# Run the app directly
java -jar target/calculator.jar
curl "http://localhost:8080/api/v1/add?a=2&b=3"
```

### Docker

```bash
docker build -t calculator-api .
docker run -d -p 8081:8080 calculator-api
curl "http://localhost:8081/api/v1/divide?a=10&b=4"
```

### Docker Compose (full stack)

```bash
docker compose up -d          # builds the image if needed, starts Nginx + 2 replicas
docker compose ps             # all containers should be "healthy"
curl "http://localhost:8090/api/v1/multiply?a=6&b=7"
docker compose down
```

Nginx listens on host port **8090** (Jenkins already uses 8080). It round-robins requests across both replicas. The `X-Upstream` response header shows which replica served each request.

Environment variables used by `docker-compose.yml`:

| Variable     | Default          | Purpose                          |
|--------------|------------------|----------------------------------|
| `IMAGE_NAME` | `calculator-api` | Image repository                 |
| `IMAGE_TAG`  | `latest`         | Image tag (Jenkins: build number)|
| `HOST_PORT`  | `8090`           | Host port for the Nginx proxy    |

## CI/CD pipeline (Jenkins)

`Jenkinsfile` defines a declarative pipeline:

| Stage        | What it does |
|--------------|--------------|
| Checkout     | Pulls the latest commit from GitHub |
| Build        | `mvn clean package -DskipTests` |
| Test         | `mvn test`; publishes JUnit XML. A failing test fails the build |
| Docker Build | Builds `<dockerhub-user>/calculator-api:<BUILD_NUMBER>` and `:latest` |
| Push         | Logs in to Docker Hub with Jenkins credentials and pushes both tags |
| Deploy       | `docker compose -p calculator up -d --wait`, then checks `/actuator/health` through Nginx |

After every run, Jenkins emails the build status (success or failure).

Re-running the pipeline is idempotent. Every build uses the same Compose project name (`calculator`), so a re-run replaces the running containers in place and does not create a second stack.

### Jenkins setup

1. **Plugins:** Pipeline, Git, GitHub, JUnit, Credentials Binding, Email Extension, Timestamper.
2. **Docker access:** the `jenkins` user must be in the `docker` group (`sudo usermod -aG docker jenkins`, then restart Jenkins).
3. **Credentials:** *Manage Jenkins → Credentials → Global → Add*:
   - Kind: *Username with password*
   - Username: your Docker Hub username
   - Password: a Docker Hub **access token**
   - ID: `dockerhub-creds`
4. **Email:** *Manage Jenkins → System → Extended E-mail Notification*. For Gmail, use SMTP server `smtp.gmail.com`, port `465`, SSL, and a Gmail **App Password** as a credential.
5. **Job:** *New Item → Pipeline*. Choose *Pipeline script from SCM → Git* and enter this repository's URL. Set the branch to `*/main` and the script path to `Jenkinsfile`. Tick *GitHub hook trigger for GITScm polling*.
6. **Webhook (optional):** GitHub repo *Settings → Webhooks* → `https://<public-jenkins-url>/github-webhook/`, content type `application/json`. A local Jenkins needs a tunnel such as `ngrok http 8080`. If there is no webhook, the pipeline falls back to Poll SCM every ~2 minutes.

## Repository layout

```
calculator-devops/
├── src/main/java/com/spe/calculator/   # Spring Boot application
├── src/test/java/com/spe/calculator/   # JUnit 5 unit + MockMvc integration tests
├── nginx/nginx.conf                    # reverse proxy / load balancer config
├── pom.xml
├── Dockerfile                          # multi-stage: Maven build → JRE runtime
├── .dockerignore
├── docker-compose.yml                  # Nginx + 2 calculator replicas
├── Jenkinsfile                         # declarative CI/CD pipeline
├── .gitignore
└── README.md
```
