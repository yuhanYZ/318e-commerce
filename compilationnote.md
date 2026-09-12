# Compilation & Run Notes

How to build and run `318e-commerce` locally.

> **Branch status.** This branch (`feature/inventory-product`) currently holds the
> iteration 2 specs plus Maven scaffolding only. Every service module is a stub
> `pom.xml` with no Java sources yet. `mvn test-compile` succeeds because there is
> nothing to compile. Sections marked *(once implemented)* apply as services land.

---

## 1. Prerequisites

| Tool | Required version | Why |
|---|---|---|
| JDK | **21** (LTS) | Root `pom.xml` sets `maven.compiler.source/target` to 21 |
| Maven | 3.9.x | No Maven wrapper in this repo, so a local install is required |
| Docker Desktop | any current | Only needed for Kafka *(once implemented)* |

**Do not build with JDK 25 or 26.** This project targets Spring Boot 3.3.5, which
was released well before those JDKs. Its bytecode tooling (ByteBuddy / CGLIB) will
fail at test time with `Unsupported class file major version`.

### Verify your toolchain

```bash
mvn -v
```

The `Java version:` line must read **21.x**. If it reads anything else, fix it
before going further.

---

## 2. Selecting JDK 21

Maven picks its JDK from the `JAVA_HOME` environment variable. It ignores whatever
`java` resolves to on your `PATH`, so `java -version` disagreeing with `mvn -v` is
normal and not a problem.

### Windows (PowerShell)

If your machine default is a different JDK, set 21 for the current shell only:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
```

Adjust the path to your own JDK 21. To make this one word, add a helper to your
PowerShell profile (`$PROFILE`):

```powershell
function jdk21 {
    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    java -version
}
```

Then type `jdk21` in any new terminal before building.

### macOS / Linux

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk      # Linux
```

### IntelliJ IDEA

IntelliJ does **not** read `JAVA_HOME`. Set it separately:

- **File -> Project Structure -> Project -> SDK** -> a JDK 21
- **Settings -> Build Tools -> Maven -> Importing -> JDK for importer** -> the same JDK 21

---

## 3. Module layout

Multi-module Maven build. The root `pom.xml` is a `pom`-packaging aggregator that
owns all shared dependency versions; each service inherits from it.

```
318e-commerce/
├── pom.xml                  aggregator: Java 21, Spring Boot 3.3.5,
│                            Spring Cloud 2023.0.4, JJWT 0.12.6
├── user-service/            port 8081
├── product-service/         port 8082
├── order-service/           port 8083
├── inventory-service/       port 8084
├── payment-service/         port 8085
├── ai-customer-service/     port 8086
└── specs/                   READ-ONLY - the contract every service builds against
```

Two rules that save pain later:

- **Folder names are all lowercase**, matching
  `specs/iter2/1-technical-architecture-v2.md`. A `<module>` entry must match its
  folder byte for byte. Windows and macOS ignore case, so a mismatch builds fine
  locally and then fails on Linux or CI with `Child module ... does not exist`.
  Yuhan's `testingByYuhan` branch uses `inventory-Service` with a capital `S`;
  that is the older spelling and will need reconciling at final merge.
- **Never edit another service's folder.** Each module has one owner. Keeping
  changes inside your own module is what stops merge conflicts.

Inside a module, the package layout follows the four-layer architecture from
`specs/iter2/1-technical-architecture-v2.md`:

```
src/main/java/com/ecommerce/<service>/
├── controller/     presentation - HTTP in/out, no business logic
├── service/        orchestration - calls domain, persists via repository
├── domain/         pure business rules and invariants, no infrastructure
├── repository/     persistence
├── dto/            request/response payloads
├── config/         Spring configuration, Kafka bindings
├── security/       JWT filter and validation
└── exception/      API exceptions and the global handler
src/main/resources/application.yml
src/test/java/com/ecommerce/<service>/
```

---

## 4. Building

All commands run from the repository root.

```bash
mvn clean install          # build every module
mvn test                   # run all tests
mvn test-compile           # compile only, fastest sanity check
```

Build or test a single module with `-pl`. Add `-am` to also build what it depends on:

```bash
mvn -pl product-service test
mvn -pl inventory-service test
mvn -pl product-service,inventory-service test
```

Useful flags:

| Flag | Effect |
|---|---|
| `-o` | Offline, uses only your local `~/.m2` cache |
| `-DskipTests` | Compile and package without running tests |
| `-q` | Quiet, prints only warnings and errors |
| `-Dtest=ProductServiceContractTests` | Run one test class |

---

## 5. Running a service *(once implemented)*

```bash
mvn -pl product-service spring-boot:run
```

Or run the `*Application` class directly from IntelliJ.

Each service uses its own in-memory H2 database, so **no database install is
needed**. Data is wiped on restart. The H2 console is at
`http://localhost:<port>/h2-console` with JDBC URL `jdbc:h2:mem:<service>db`,
username `sa`, and an empty password.

### Shared JWT secret

Authentication is HS256 JWT. Every service validates tokens locally using the
**same** `app.jwt.secret`. If two services disagree on this value, every
cross-service call fails with 401. The default in `application.yml` is a dev-only
placeholder; override it consistently for anything beyond local dev:

```bash
export JWT_SECRET="...at least 32 bytes, identical across all six services..."
```

### Kafka

Kafka is **off by default**, which is deliberate. `mvn test` performs zero Kafka
activity and needs no broker.

To turn on the event-driven flows, start a broker and run services with the `kafka`
Spring profile:

```bash
docker compose up -d                                    # Kafka on localhost:9092
mvn -pl inventory-service spring-boot:run -Dspring-boot.run.profiles=kafka
```

In IntelliJ, set **Active profiles: `kafka`** in the run configuration.

> The `docker-compose.yml` for this lives on the `testingByYuhan` branch and has not
> been brought onto this branch yet.

---

## 6. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Child module ... does not exist` | A folder listed in the root `<modules>` has no `pom.xml`. Every module needs at least a stub. |
| `Unsupported class file major version` | Building on JDK 25/26. Point `JAVA_HOME` at JDK 21. |
| `mvn` not recognised | Maven is not on your `PATH`, or you opened the terminal before installing it. Open a new one. |
| `mvn -v` shows the wrong Java | `JAVA_HOME` is unset or wrong for that shell. Ignore `java -version`, it is unrelated. |
| 401 on a cross-service call | `app.jwt.secret` differs between the two services. |
| Port 808x already in use | A previous run is still alive. Find the PID with `netstat -ano` filtered on the port, then kill it. |
| Kafka connection refused | Broker not running, or you forgot the `kafka` profile. |
| Tests pass alone but fail together | H2 is shared per service, not per test. Tests must not depend on each other's data. |
| Builds on Windows, fails on Linux/CI | A folder rename that only changed case. Git sets `core.ignorecase=true` on Windows and may not record it. Rename with `git mv -f old new` and check `git ls-files` shows the new spelling. |

---

## 7. Contributing

Branch from `iter2`, which is the integration branch. `main` is reserved for the
final merge and has an unrelated history, so a pull request from your branch into
`main` cannot merge.

```bash
git fetch origin
git switch -c feature/<your-scope> origin/iter2
git pull --rebase origin iter2       # before each session
```

Commit with the service as a scope, for example `product: add search endpoint`.
Open pull requests against `iter2`.

`target/` and `.idea/` are gitignored. Never commit build output.
