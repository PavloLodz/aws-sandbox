# Items API – TestNG Test Suite

Standalone TestNG project that tests the **Items REST API** (`/api/items`) with
[RestAssured](https://rest-assured.io/) and [AssertJ](https://assertj.github.io/doc/).

## Project layout

```
items-testng/
├── pom.xml
└── src/test/
    ├── java/pl/ldz/example/
    │   ├── dto/
    │   │   ├── ItemRequest.java      ← mirrors the production request DTO
    │   │   └── ItemResponse.java     ← mirrors the production response DTO
    │   └── tests/
    │       ├── BaseApiTest.java      ← RestAssured config, shared helpers
    │       ├── ItemFactory.java      ← test-data factory
    │       ├── CreateItemTest.java   ← POST /api/items
    │       ├── GetItemTest.java      ← GET /api/items + GET /api/items/{id}
    │       ├── UpdateItemTest.java   ← PUT /api/items/{id}
    │       ├── DeleteItemTest.java   ← DELETE /api/items/{id}
    │       └── CrudLifecycleTest.java ← end-to-end happy path
    └── resources/
        ├── testng.xml               ← suite descriptor
        └── logback-test.xml
```

## Prerequisites

| Tool    | Version |
|---------|---------|
| Java    | 21+     |
| Maven   | 3.9+    |
| Running Items API | any reachable instance |

## Running the tests

### Against localhost (default)

Start the API first (`mvn spring-boot:run -Plocal` or via Docker Compose),
then:

```bash
mvn clean test
```

### Against a custom host

```bash
k8s:
mvn clean test -Dbase.url=http://myapp.local
mvn clean test -Dbase.url=http://myserver:8080
```

### Run a single group

```bash
mvn test -Dgroups=lifecycle
```

## Test groups

| Group       | Description                                    |
|-------------|------------------------------------------------|
| `create`    | POST /api/items – happy path + validation      |
| `read`      | GET /api/items (list + search) + GET by ID     |
| `update`    | PUT /api/items/{id} – happy path + validation  |
| `delete`    | DELETE /api/items/{id}                         |
| `lifecycle` | Full Create → Read → Update → Delete chain     |

## DTOs

The test DTOs in `pl.ldz.example.dto` are intentional copies of the production
records.  They use **Lombok** (`@Data @Builder`) instead of Java records so that
RestAssured's Jackson provider can deserialise them without additional config.

`@JsonIgnoreProperties(ignoreUnknown = true)` on `ItemResponse` makes the suite
forward-compatible: adding fields to the API will not break the tests.

## Reports

Surefire writes TestNG HTML and XML reports to `target/surefire-reports/`.
Open `target/surefire-reports/index.html` in a browser for a full summary.
