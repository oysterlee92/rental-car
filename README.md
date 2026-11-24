# 렌트카 예약 시스템

차량 검색부터 예약과 결제, 취소·환불, 대여·반납까지 단계적으로 확장하는 렌트카 서비스 프로젝트다. 여러 고객이 같은 차량과 기간을 동시에 선택해도 중복 배정되지 않도록 처리한다.

## 구현 기능

| 기능 | 동작 |
| --- | --- |
| 지점 조회 | 차량을 인수·반납할 지점 목록을 조회한다. |
| 차량 검색 | 지점과 대여 기간으로 운영 가능한 차량을 검색하고, 해당 기간에 선점된 차량은 제외한다. |
| 입력 검사 | 지점 ID와 날짜 형식을 확인하고 반납 시각이 인수 시각보다 늦은지 검사한다. 선점 요청의 인수 시각은 현재보다 미래여야 한다. |
| 차량 선점 | 선택한 차량을 5분간 선점하고 예약을 생성한다. |
| 동시 예약 처리 | PostgreSQL 기간 배제 제약으로 같은 차량의 겹치는 선점을 막는다. |
| 요청 재전송 처리 | 같은 요청 키와 조건이면 기존 예약을 반환하고 중복 생성하지 않는다. |
| 요금 산정 | 대여 시간을 24시간 단위로 올림한 일수에 일일 요금을 곱해 예약 생성 시 저장한다. |
| 예약 상세 | 예약 ID로 차량, 대여 기간, 요금, 상태와 선점 만료 시각을 확인한다. |
| 선점 만료 | 스케줄러와 요청 처리 중 만료 여부를 확인해 선점 상태를 해제한다. |
| 동시 요청 검증 | k6로 같은 차량·기간에 요청 100건을 보내 생성 건수와 충돌 건수를 확인한다. |

예약 상태는 선점 중인 `HELD`와 기한이 지난 `EXPIRED`를 사용한다. 결제·환불·대여·반납, Redis 검색 캐시, Kafka 비동기 처리와 메트릭·추적·로그를 연결한 관측 기능은 구현 범위에 포함하지 않는다. Actuator 상태 확인은 제공한다.

## 기술 구성

| 항목 | 사용 기술 |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.2.0 |
| 빌드 | Gradle, Groovy DSL |
| 화면 | Spring MVC, Thymeleaf |
| 데이터 접근 | Spring Data JPA |
| DB 변경 관리 | Flyway |
| 데이터베이스 | PostgreSQL 19.0 |
| 실행 환경 | Docker, Docker Compose |
| 부하 테스트 | k6 |
| 상태 확인 | Spring Boot Actuator |

## 서비스 구성

Gradle 멀티 프로젝트로 공통 빌드 설정을 관리한다. Web과 Reservation은 각각 독립 Spring Boot 앱으로 빌드하고 실행한다.

| 구성 요소 | 역할 | 연결 |
| --- | --- | --- |
| Web | 검색·선점·예약 상세 화면을 제공한다. | 브라우저 요청을 받고 Reservation API를 HTTP로 호출한다. |
| Reservation | 지점·차량 검색, 선점, 요금 산정과 만료를 처리한다. | 자신이 소유한 PostgreSQL DB를 조회하고 변경한다. |
| PostgreSQL | 지점·차량·예약을 저장하고 기간 중복을 제한한다. | Reservation만 접근하며 Web은 직접 조회하지 않는다. |
| k6 | 동시에 들어오는 선점 요청을 재현한다. | 부하 테스트 실행 시 Reservation API를 호출한다. |

Reservation 내부는 `presentation`, `application`, `domain`, `infrastructure`로 구성한다. `RentalPeriod`는 반납이 인수보다 늦어야 한다는 규칙을, `BranchId`는 지점 ID의 양수 규칙을 지킨다. `Reservation` 애그리거트가 배정·요금·선점 상태를 관리한다. 도메인 엔티티에는 JPA 매핑을 함께 사용하고 도메인 저장소 인터페이스와 JPA 구현을 분리한다.

## 날짜와 저장 기준

날짜 필드는 `LocalDateTime`으로 통일하고 입력·저장·응답을 한국 시각 기준으로 처리한다. JVM, PostgreSQL 서버·데이터베이스와 JDBC 연결의 시간대는 `Asia/Seoul`이다. API 날짜 형식은 `2030-01-10T10:00:00`을 사용한다.

DB의 시각 컬럼은 `timestamp without time zone`, 대여 기간은 `tsrange`를 사용한다. 인수 시각을 포함하고 반납 시각을 제외하므로 같은 차량의 `10:00~11:00`과 `11:00~12:00`은 겹치지 않는다. 같은 차량의 `HELD` 예약 기간이 겹치면 DB 배제 제약이 저장을 거부한다.

요청·응답·폼 DTO는 기능별 `dto` 패키지에 별도 파일로 둔다. Reservation은 `application/dto/vehicle`과 `application/dto/reservation`, Web은 `dto/search`와 `dto/reservation`을 사용한다.

## 준비 사항

Docker Engine 또는 Docker Desktop과 Docker Compose v2가 필요하다. Docker 이미지 빌드에 Gradle과 Java 25가 포함돼 있어 Docker 실행에는 별도 JDK와 Gradle 설치가 필요하지 않다. 첫 실행에는 이미지와 의존성을 내려받을 인터넷 연결이 필요하다.

로컬에서 앱을 직접 실행하려면 Java 25와 Gradle도 준비한다. PostgreSQL 드라이버는 `runtimeOnly`로 사용하고, 예약 저장의 기간 충돌은 JDK의 `SQLException`으로 확인한다.

## 실행

이 README가 있는 `rental-car` 디렉터리에서 실행한다.

```sh
docker compose up --build -d --wait
docker compose ps
```

브라우저에서 `http://localhost:8080`을 연다. 지점과 미래의 대여 기간으로 검색하고 차량 선점 버튼을 누른다. 예약 상세에서 차량과 요금, 선점 만료 시각을 확인한다. 입력과 화면 표시는 한국 현지 시각 기준이다. 정비 중이거나 해당 기간에 선점된 차량은 검색에서 제외된다.

| 대상 | 주소 |
| --- | --- |
| Web | http://localhost:8080 |
| Reservation API | http://localhost:8081 |
| 각 앱의 상태 확인 | `/actuator/health` |

PostgreSQL은 Compose 내부에서 `postgres:5432`, 외부에서 `localhost:5432`로 접근한다. 데이터베이스와 로컬 실습용 계정은 `reservation`, 비밀번호는 `reservation-local`이다. Web은 DB에 직접 접근하지 않는다.

Compose는 `postgres:19.0` 이미지와 `postgres19-data` 볼륨을 사용한다. 볼륨은 `/var/lib/postgresql`에 연결하고 DB 파일은 그 아래 `19/docker`에 저장한다. 이전 버전의 실습 데이터를 사용하는 경우, 새 볼륨에는 초기 데이터가 생성되므로 필요한 데이터는 덤프·복원 등으로 별도 이전한다.

## API 확인

| API | 용도 |
| --- | --- |
| `GET /api/branches` | 지점 목록 |
| `GET /api/vehicles` | 기간별 차량 검색 |
| `POST /api/reservations` | `Idempotency-Key` 헤더와 JSON으로 차량 선점 |
| `GET /api/reservations/{id}` | 예약 상세와 현재 상태 |

PowerShell에서는 다음 명령으로 확인한다.

```powershell
Invoke-RestMethod 'http://localhost:8081/api/branches'
$result = Invoke-RestMethod 'http://localhost:8081/api/vehicles?branchId=1&pickupAt=2030-01-10T10:00:00&returnAt=2030-01-11T10:00:00'
$result | ConvertTo-Json -Depth 5
```

macOS와 Linux에서는 다음 명령을 사용한다.

```sh
curl 'http://localhost:8081/api/branches'
curl 'http://localhost:8081/api/vehicles?branchId=1&pickupAt=2030-01-10T10:00:00&returnAt=2030-01-11T10:00:00'
```

해당 기간에 선점이 없는 초기 데이터라면 `vehicles`에는 ID 1, 2가 포함된다. `pickupAt`과 `returnAt`은 한국 현지 시각의 `LocalDateTime` 형식이다. `2030-01-10T10:00:00`은 한국 시각 오전 10시를 뜻한다.

빈 기간을 선택해 다음 PowerShell 명령으로 선점을 확인한다.

```powershell
$headers = @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
$body = @{
    branchId = 1
    vehicleId = 1
    pickupAt = '2030-01-10T10:00:00'
    returnAt = '2030-01-11T10:00:00'
} | ConvertTo-Json
$first = Invoke-WebRequest -UseBasicParsing -Method Post -Uri 'http://localhost:8081/api/reservations' -Headers $headers -ContentType 'application/json' -Body $body
$second = Invoke-WebRequest -UseBasicParsing -Method Post -Uri 'http://localhost:8081/api/reservations' -Headers $headers -ContentType 'application/json' -Body $body
$first.StatusCode
$second.StatusCode
($first.Content | ConvertFrom-Json).id
($second.Content | ConvertFrom-Json).id
```

첫 생성은 201, 같은 키·조건의 재전송은 200이며 예약 ID와 선점 만료 시각이 같다. 같은 키로 다른 조건을 보내거나 다른 키로 겹치는 기간을 선점하면 409를 반환한다. 만료된 키를 다시 보내면 기존 예약의 `EXPIRED` 상태를 반환한다. 새 선점에는 새 키를 사용한다.

예약 요금은 24시간 단위로 올림한 일수에 일일 요금을 곱해 생성 시 저장한다. 인수 시각은 현재보다 미래여야 한다. 선점은 기본 5분간 유지되며 `reservation.hold-duration: PT5M`으로 설정한다. 만료 스케줄러는 10초마다 최대 100건을 처리하고, 새 선점과 상세 조회에서도 만료 상태를 반영한다.

반납이 인수보다 빠르거나 같은 경우, 잘못된 ID와 날짜 형식에는 HTTP 400을 반환한다. 존재하지 않는 지점·차량·예약은 해당 조회 API에서 HTTP 404를 반환한다. Web은 잘못된 입력을 화면에 안내하고 통신 결과가 불확실하면 같은 요청 키로 다시 확인하는 폼을 보여 준다.

## 동시 예약 확인

```sh
docker compose --profile load run --rm k6
```

k6는 Compose 내부에서 1번 차량에 서로 다른 키로 100건을 요청한다. 기본 기간은 한국 시각 2030년 6월 1일 오전 10시부터 다음 날 오전 10시까지다. 해당 기간이 비어 있어야 하며 시작 전에 검색으로 확인한다. 기대 결과는 `created_reservations` 1건, `conflicting_reservations` 99건이다. 409는 예상된 충돌로 처리하고 500이나 타임아웃은 실패로 집계한다.

기간을 바꿀 때는 PowerShell에서 환경 변수를 지정한다.

```powershell
$env:PICKUP_AT = '2030-06-03T10:00:00'
$env:RETURN_AT = '2030-06-04T10:00:00'
docker compose --profile load run --rm k6
```

활성 배정끼리 겹치는지 DB에서도 확인한다. 다음 쿼리의 기대 결과는 0행이다.

```sql
SELECT a.id AS first_id, b.id AS second_id
FROM reservation a
JOIN reservation b
  ON a.vehicle_id = b.vehicle_id
 AND a.id < b.id
 AND a.during && b.during
WHERE a.status = 'HELD'
  AND b.status = 'HELD';
```

## 로그와 종료

```sh
docker compose logs --tail=100 reservation-service web-service
docker compose down
```

`down` 후에도 PostgreSQL 볼륨은 유지된다. 전체 실습 데이터를 지우고 처음부터 다시 시작하려면 다음 명령을 실행한다. 이 명령은 이 Compose 프로젝트의 DB 볼륨을 삭제한다.

```sh
docker compose down -v
docker compose up --build -d --wait
```

Flyway는 Reservation 시작 시 `V1__create_catalog.sql`, `V2__seed_catalog.sql`, `V3__create_reservation.sql`을 순서대로 적용한다. V3는 예약의 시각 컬럼을 한국 현지 시각의 `timestamp without time zone`으로 생성하고 기간 비교에 `tsrange`를 사용한다. 재시작하면 이미 적용한 파일을 다시 실행하지 않는다. JPA의 `ddl-auto=validate`는 매핑과 스키마를 확인한다.

## 로컬 JDK로 실행

Java 25와 Gradle을 설치한 경우 다음처럼 실행할 수도 있다. 터미널마다 한 앱씩 실행한다. IntelliJ에서 직접 실행할 때는 두 앱의 VM options에 -Duser.timezone=Asia/Seoul을 설정한다.

```sh
docker compose up -d --wait postgres
gradle clean bootJar
```

PowerShell에서는 다음처럼 실행한다.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/reservation'
java -Duser.timezone=Asia/Seoul -jar reservation-service/build/libs/reservation-service.jar
```

macOS와 Linux에서는 다음처럼 실행한다.

```sh
DB_URL=jdbc:postgresql://localhost:5432/reservation java -Duser.timezone=Asia/Seoul -jar reservation-service/build/libs/reservation-service.jar
```

다른 터미널에서 Web을 실행한다.

```sh
java -Duser.timezone=Asia/Seoul -jar web-service/build/libs/web-service.jar
```

두 앱을 이미 Compose로 실행했다면 먼저 `docker compose stop web-service reservation-service`로 중지해 8080, 8081 포트를 비운다.

## 패키지 배치

DDD의 네 계층 아래에서도 파일을 역할별 패키지로 나눈다. 서비스와 예외, 엔티티와 값 객체, 저장소 인터페이스와 JPA 구현을 각각 구분한다.

| Reservation 패키지 | 역할 | 주요 파일 |
| --- | --- | --- |
| `application/service` | 검색·선점·만료 처리 순서 | `VehicleSearchService`, `ReservationService`, `ReservationExpiryService` |
| `application/transaction` | 예약 생성·재조회 트랜잭션 | `ReservationTransactions` |
| `application/dto/vehicle, application/dto/reservation` | 요청·응답과 처리 결과를 전달하는 별도 DTO 파일 | `SearchResponse`, `HoldRequest`, `HoldResult`, `ReservationResponse` |
| `application/exception` | 조회 대상이 없을 때 발생하는 오류 | `BranchNotFoundException`, `VehicleNotFoundException`, `ReservationNotFoundException` |
| `domain/entity` | ID를 가진 엔티티와 예약 애그리거트 | `Branch`, `Vehicle`, `Reservation` |
| `domain/vo` | 값의 유효성과 대여 기간 규칙 | `BranchId`, `RentalPeriod` |
| `domain/enums` | 차량·예약 상태 상수 | `VehicleStatus`, `ReservationStatus` |
| `domain/exception` | 예약 규칙 위반 오류 | `ReservationConflictException` |
| `domain/repository` | 애플리케이션이 호출하는 도메인 저장소 인터페이스 | `BranchRepository`, `VehicleRepository`, `ReservationRepository` |
| `infrastructure/repository/jpa` | Spring Data JPA 저장소 인터페이스 | `JpaBranchRepository`, `JpaVehicleRepository`, `JpaReservationRepository` |
| `infrastructure/repository/adapter` | 도메인 저장소 호출을 JPA 호출로 연결 | `BranchRepositoryAdapter`, `VehicleRepositoryAdapter`, `ReservationRepositoryAdapter` |
| `infrastructure/config` | Clock과 스케줄 실행 설정 | `ReservationConfiguration` |
| `infrastructure/scheduler` | 주기적인 선점 만료 작업 호출 | `ReservationExpiryJob` |
| `presentation/controller` | HTTP 요청 입력과 응답 | `VehicleSearchController`, `ReservationController` |
| `presentation/exception` | 업무 오류를 HTTP 오류 응답으로 변환 | `ApiExceptionHandler` |

Web은 `controller`에 화면 컨트롤러, `client`에 `ReservationClient`, `dto/search`와 `dto/reservation`에 기능별 DTO를 둔다. 각 앱의 시작 클래스만 서비스 루트 패키지에 둔다. 상태 enum의 패키지 이름은 `enums`다. `enum`은 Java 예약어이므로 패키지 이름으로 사용할 수 없다.

`domain/repository`에는 순수 Java 인터페이스를 두고, `infrastructure/repository/jpa`에는 Spring Data JPA 인터페이스를 둔다. `infrastructure/repository/adapter`의 어댑터가 두 저장소를 연결한다. 예외는 발생 원인에 따라 `application/exception`과 `domain/exception`에 두고, HTTP 상태를 정하는 처리는 `presentation/exception`에 둔다.

## 프로젝트 구조

```text
rental-car/
|-- .dockerignore
|-- .gitignore
|-- Dockerfile
|-- README.md
|-- build.gradle
|-- compose.yaml
|-- reservation-service
|   |-- build.gradle
|   |-- src
|   |   |-- main
|   |   |   |-- java
|   |   |   |   |-- com
|   |   |   |   |   |-- example
|   |   |   |   |   |   |-- rental
|   |   |   |   |   |   |   |-- reservation
|   |   |   |   |   |   |   |   |-- ReservationApplication.java
|   |   |   |   |   |   |   |   |-- application
|   |   |   |   |   |   |   |   |   |-- dto
|   |   |   |   |   |   |   |   |   |   |-- reservation
|   |   |   |   |   |   |   |   |   |   |   |-- HoldRequest.java
|   |   |   |   |   |   |   |   |   |   |   |-- HoldResult.java
|   |   |   |   |   |   |   |   |   |   |   |-- ReservationResponse.java
|   |   |   |   |   |   |   |   |   |   |-- vehicle
|   |   |   |   |   |   |   |   |   |   |   |-- BranchResponse.java
|   |   |   |   |   |   |   |   |   |   |   |-- SearchResponse.java
|   |   |   |   |   |   |   |   |   |   |   |-- VehicleResponse.java
|   |   |   |   |   |   |   |   |   |-- exception
|   |   |   |   |   |   |   |   |   |   |-- BranchNotFoundException.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationNotFoundException.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleNotFoundException.java
|   |   |   |   |   |   |   |   |   |-- service
|   |   |   |   |   |   |   |   |   |   |-- ReservationExpiryService.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationService.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleSearchService.java
|   |   |   |   |   |   |   |   |   |-- transaction
|   |   |   |   |   |   |   |   |   |   |-- ReservationTransactions.java
|   |   |   |   |   |   |   |   |-- domain
|   |   |   |   |   |   |   |   |   |-- entity
|   |   |   |   |   |   |   |   |   |   |-- Branch.java
|   |   |   |   |   |   |   |   |   |   |-- Reservation.java
|   |   |   |   |   |   |   |   |   |   |-- Vehicle.java
|   |   |   |   |   |   |   |   |   |-- enums
|   |   |   |   |   |   |   |   |   |   |-- ReservationStatus.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleStatus.java
|   |   |   |   |   |   |   |   |   |-- exception
|   |   |   |   |   |   |   |   |   |   |-- ReservationConflictException.java
|   |   |   |   |   |   |   |   |   |-- repository
|   |   |   |   |   |   |   |   |   |   |-- BranchRepository.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationRepository.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleRepository.java
|   |   |   |   |   |   |   |   |   |-- vo
|   |   |   |   |   |   |   |   |   |   |-- BranchId.java
|   |   |   |   |   |   |   |   |   |   |-- RentalPeriod.java
|   |   |   |   |   |   |   |   |-- infrastructure
|   |   |   |   |   |   |   |   |   |-- config
|   |   |   |   |   |   |   |   |   |   |-- ReservationConfiguration.java
|   |   |   |   |   |   |   |   |   |-- repository
|   |   |   |   |   |   |   |   |   |   |-- adapter
|   |   |   |   |   |   |   |   |   |   |   |-- BranchRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |   |-- ReservationRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |   |-- VehicleRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |-- jpa
|   |   |   |   |   |   |   |   |   |   |   |-- JpaBranchRepository.java
|   |   |   |   |   |   |   |   |   |   |   |-- JpaReservationRepository.java
|   |   |   |   |   |   |   |   |   |   |   |-- JpaVehicleRepository.java
|   |   |   |   |   |   |   |   |   |-- scheduler
|   |   |   |   |   |   |   |   |   |   |-- ReservationExpiryJob.java
|   |   |   |   |   |   |   |   |-- presentation
|   |   |   |   |   |   |   |   |   |-- controller
|   |   |   |   |   |   |   |   |   |   |-- ReservationController.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleSearchController.java
|   |   |   |   |   |   |   |   |   |-- exception
|   |   |   |   |   |   |   |   |   |   |-- ApiExceptionHandler.java
|   |   |   |-- resources
|   |   |   |   |-- application.yaml
|   |   |   |   |-- db
|   |   |   |   |   |-- migration
|   |   |   |   |   |   |-- V1__create_catalog.sql
|   |   |   |   |   |   |-- V2__seed_catalog.sql
|   |   |   |   |   |   |-- V3__create_reservation.sql
|-- scripts
|   |-- reservation-race.js
|-- settings.gradle
|-- web-service
|   |-- build.gradle
|   |-- src
|   |   |-- main
|   |   |   |-- java
|   |   |   |   |-- com
|   |   |   |   |   |-- example
|   |   |   |   |   |   |-- rental
|   |   |   |   |   |   |   |-- web
|   |   |   |   |   |   |   |   |-- WebApplication.java
|   |   |   |   |   |   |   |   |-- client
|   |   |   |   |   |   |   |   |   |-- ReservationClient.java
|   |   |   |   |   |   |   |   |-- controller
|   |   |   |   |   |   |   |   |   |-- ReservationPageController.java
|   |   |   |   |   |   |   |   |   |-- SearchController.java
|   |   |   |   |   |   |   |   |-- dto
|   |   |   |   |   |   |   |   |   |-- reservation
|   |   |   |   |   |   |   |   |   |   |-- HoldRequest.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationView.java
|   |   |   |   |   |   |   |   |   |-- search
|   |   |   |   |   |   |   |   |   |   |-- BranchView.java
|   |   |   |   |   |   |   |   |   |   |-- SearchForm.java
|   |   |   |   |   |   |   |   |   |   |-- SearchResult.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleView.java
|   |   |   |-- resources
|   |   |   |   |-- application.yaml
|   |   |   |   |-- static
|   |   |   |   |   |-- search.css
|   |   |   |   |-- templates
|   |   |   |   |   |-- reservation-error.html
|   |   |   |   |   |-- reservation.html
|   |   |   |   |   |-- search.html
```
