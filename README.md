# 렌트카 예약 시스템

지점과 대여 기간으로 차량을 검색하고 선점한 뒤 모의 결제로 예약을 확정하는 MSA 프로젝트다. 차량 기간 중복과 결제 중복 반영을 막고, Kafka로 결제 접수와 실제 처리를 분리한다. 동기 HTTP 모드도 유지해 같은 조건에서 지연을 비교할 수 있다.

## 구현 기능

| 기능 | 동작 |
| --- | --- |
| 지점·차량 검색 | 지점별 운영 가능한 차량 중 요청 기간에 활성 배정이 없는 차량 조회 |
| 차량 선점 | 미래 인수 시각과 대여 기간을 검사하고 기본 5분 선점 생성 |
| 요금 확정 | 24시간 단위로 올림한 일수에 일일 요금을 곱해 예약 생성 시 저장 |
| 동시 예약 | PostgreSQL 기간 배제 제약으로 같은 차량의 활성 배정 중복 방지 |
| 요청 재전송 | 같은 요청 키와 예약 조건이면 기존 예약 반환 |
| 비동기 결제 | 요청 아웃박스 접수 후 202 반환, Kafka 요청·결과 메시지로 처리 |
| 모의 승인 | 기본 200~5,000ms 대기와 10% 거절, 실제 과금 없음 |
| 전송 복구 | 예약·결제 DB의 아웃박스, 임대와 재전송 |
| 소비 재시도 | DB 커밋 후 오프셋 진행, 반복 실패는 DLT 보관 |
| 중복 결과 처리 | 예약 ID별 결제 최종 결과 재사용, 예약 확정 중복 변경 방지 |
| 화면 | 검색·선점·결제 접수·예약 상세와 최종 상태 갱신 |
| 관측 | Collector 수집, CPU·JVM·HTTP·DB 운영 지표와 결제 지연 대시보드, Loki 로그 패널·Tempo 추적 연결 |
| 성능 비교 | k6 동시 선점·동기 결제·비동기 접수와 최종 상태 관측 실험 |

취소·환불, 차량 인수·반납과 Redis 검색 캐시는 구현 범위에 포함하지 않는다.

## 기술 구성

| 항목 | 사용 기술 |
| --- | --- |
| Java / Spring Boot | 25 / 4.2.0 |
| 빌드 | Gradle, Groovy DSL |
| 화면 | Spring MVC, Thymeleaf |
| 데이터 접근 / 스키마 | Spring Data JPA / Flyway |
| DB | PostgreSQL 19.0 |
| 메시징 | Spring Kafka, Apache Kafka 4.1.2 |
| 로컬 실행 | Docker, Docker Compose |
| 메트릭 | Actuator, Micrometer, Prometheus |
| 로그 | SLF4J·Logback JSON 로그, Lombok @Slf4j, Loki |
| 관측 수집 | OpenTelemetry Collector Contrib 0.139.0 |
| 추적 | OpenTelemetry, Tempo |
| 대시보드 | Grafana |
| 부하 테스트 | k6 |

## 서비스 구성

각 서비스는 독립 Spring Boot 앱이고 Gradle 루트에서 공통 빌드를 관리한다. 루트 build.gradle에서 implementation과 annotationProcessor에 같은 Spring Boot BOM을 적용한다. Lombok의 compileOnly와 annotationProcessor 의존성도 루트 subprojects에서 모든 서비스에 공통 적용한다. 서비스별 build.gradle에는 BOM과 Lombok 의존성을 반복해서 적지 않는다.

| 구성 | 책임 | 연결 |
| --- | --- | --- |
| Web | 검색·선점·결제·예약 상세 화면 | Reservation HTTP API |
| Reservation | 차량 배정·요금·예약 상태 | 예약 DB, Kafka 요청 전송·결과 소비 |
| Payment | 모의 승인과 최종 결제 상태 | 결제 DB, Kafka 요청 소비·결과 전송 |
| Kafka | 결제 요청·결과와 실패 메시지 저장 | 컨슈머 그룹별 처리 위치 유지 |
| OpenTelemetry Collector | 로그·메트릭·추적 수집과 전달 | 공유 JSON 파일, Actuator 수집, OTLP HTTP 수신 |
| Prometheus / Grafana | 메트릭 저장과 대시보드 | Collector의 8889 메트릭 주소 |
| Tempo | 추적 저장과 조회 | Collector가 전달한 OTLP HTTP 추적 |
| Loki | 로그 저장과 검색 | Collector → Loki OTLP, Grafana에서 검색 |

예약 DB와 결제 DB는 컨테이너·계정·볼륨을 분리한다. 서비스는 자기 DB만 직접 조회하고 다른 서비스의 DB에 외래 키를 연결하지 않는다. 도메인 모델에는 JPA 매핑을 함께 사용한다.

## 예약·결제 규칙

| 예약 상태 | 차량 배정 |
| --- | --- |
| HELD | 선점 기한 전까지 유지 |
| EXPIRED | 결제 시작 전 선점 기한이 지나 해제 |
| PAYMENT_PENDING | 결과가 확인될 때까지 유지 |
| CONFIRMED | 승인된 예약으로 유지 |
| PAYMENT_FAILED | 거절·승인 기한 초과 결과 반영 후 해제 |

결제 금액과 승인 기한은 Reservation DB의 예약 값을 사용한다. Payment는 같은 예약 ID의 최초 조건과 최종 결과를 유지한다. 승인 결과를 저장할 때 기한이 지났다면 FAILED가 되고, 기한 전에 승인됐지만 결과 메시지만 늦으면 예약을 확정한다. 일반 만료 작업은 HELD만 처리한다.

비동기 접수는 예약 상태와 요청 아웃박스가 함께 커밋되면 완료된다. Payment의 최종 상태와 결과 아웃박스도 같은 트랜잭션으로 저장한다. Kafka 대기와 모의 sleep 동안 DB 연결·행 잠금을 유지하지 않는다. 전송 성공 뒤 완료 표시 전 종료되면 같은 이벤트가 다시 전송될 수 있어 소비자는 중복을 처리한다.

## 메시지와 실패 처리

| 토픽 | 소비 그룹 | 용도 |
| --- | --- | --- |
| payment.requested.v1 | payment-workers | 결제 요청 |
| payment.completed.v1 | reservation-results | 결제 결과 |
| payment.requested.v1.DLT | 자동 업무 소비 없음 | 요청 처리 실패 보관 |
| payment.completed.v1.DLT | 자동 업무 소비 없음 | 결과 반영 실패 보관 |

토픽은 파티션 3개·복제본 1개이며 보관 기간은 7일이다. 같은 예약 ID를 메시지 키로 사용한다. 단일 브로커 KRaft 실습 구성이며 다중 브로커의 장애 내성을 검증하지 않는다.

메시지 형식·ID 오류는 DLT로 보내고 일반 오류는 1초 간격으로 두 번 더 처리한 뒤 보관한다. DLT 전송이 실패하면 원본 소비를 완료 처리하지 않는다. DLT에 남은 결제는 실패로 단정하지 않고 PAYMENT_PENDING을 유지한다. 원인을 수정한 뒤 해당 메시지를 원본 토픽으로 재전송한다.

아웃박스는 최대 10건을 60초 임대하고 트랜잭션 밖에서 Kafka 승인을 기다린다. 실패한 메시지는 5초 뒤 재시도할 수 있고 배치 종료 후 1초 뒤 다시 조회한다. 완료한 아웃박스 행은 예제에서 삭제하지 않는다. Kafka 보관 기간이 지난 메시지의 자동 복원이나 DLT 자동 업무 재처리는 제공하지 않는다.

## 날짜와 설정

화면·API·엔티티의 날짜는 LocalDateTime이고 한국 시각으로 해석한다. JVM·PostgreSQL 서버·DB·JDBC 연결은 Asia/Seoul을 사용한다. 시각 컬럼은 timestamp without time zone, 기간 비교는 tsrange이며 시작은 포함하고 끝은 제외한다.

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| PAYMENT_MODE | async | Reservation과 Payment에 동일하게 적용, sync는 HTTP 비교 모드 |
| KAFKA_BOOTSTRAP_SERVERS | localhost:9092 | 호스트 앱의 Kafka 초기 접속 주소 |
| reservation.hold-duration | PT5M | 결제 시작 전 선점 유지 시간 |
| PAYMENT_MIN_MILLIS / PAYMENT_MAX_MILLIS | 200 / 5000 | 모의 대기 밀리초, 최대 허용 지연 30000 |
| PAYMENT_FAILURE_RATE | 0.1 | 거절 비율 0~1 |
| outbox.poll-delay | 1000 | 배치 종료 후 대기 밀리초 |
| Payment 읽기 제한 | 8초 | sync 모드의 Reservation → Payment 대기 |
| Reservation 읽기 제한 | 15초 | Web → Reservation 대기 |
| PAYMENT_WAIT_SECONDS | 120 | 비동기 k6의 최종 상태 관측 제한 |
| OTLP_TRACES_ENDPOINT | http://localhost:4318/v1/traces | Collector 추적 수신 주소 |
| APP_LOG_FILE | ./logs/앱이름/application.log | JSON 파일 경로, Compose는 /var/log/rental/앱이름/application.log |

Compose에서는 Kafka를 kafka:9092, 추적 주소를 otel-collector:4318로 덮어쓴다. 추적은 실습용으로 전체 요청을 수집한다. 예약 ID는 로그·추적 속성에만 넣고 메트릭 태그로 사용하지 않는다.

## 준비 사항

Docker Engine 또는 Docker Desktop과 Docker Compose v2를 준비한다. 이미지 내부에서 Gradle로 빌드하므로 Docker 실행에는 별도 JDK·Gradle 설치가 필요하지 않다. 첫 실행에는 이미지와 의존성을 내려받을 네트워크 연결이 필요하다. 호스트에서 앱을 실행하려면 Java 25와 Gradle도 준비한다.

## API

| API | 동작 |
| --- | --- |
| GET /api/branches | 지점 목록 |
| GET /api/vehicles | branchId·pickupAt·returnAt으로 차량 검색 |
| POST /api/reservations | Idempotency-Key 헤더와 JSON으로 선점 |
| GET /api/reservations/{id} | 예약 상세와 현재 상태 |
| POST /api/reservations/{id}/payment | 결제 접수 또는 저장된 최종 상태 반환 |
| Payment의 POST /api/payments | sync 모드에서만 제공하는 내부 HTTP 결제 API |

선점 최초 생성은 201, 같은 키·조건의 재전송은 200이다. 비동기 결제 접수는 202, 이미 완료한 결제 결과는 200이다. 입력 오류는 400, 조회 대상 없음은 404, 배정·요청 조건 충돌은 409다. sync 모드의 결제 통신 결과 미확인은 503이며 결제 거절과 구분한다.

검색 API는 다음처럼 확인한다.

```powershell
Invoke-RestMethod 'http://localhost:8081/api/branches'
Invoke-RestMethod 'http://localhost:8081/api/vehicles?branchId=1&pickupAt=2032-03-01T10:00:00&returnAt=2032-03-02T10:00:00'
```

## 실행과 접속

프로젝트 루트에서 실행한다. Flyway는 예약 DB에 V1~V5, 결제 DB에 V1~V2를 적용한다. 이미 적용된 파일은 다시 실행하지 않고 기존 데이터 볼륨을 유지한다.

loki-init은 loki-data에 완료 표시가 없을 때만 root로 소유권을 10001:10001로 맞추고 권한을 설정한다. 성공하면 완료 표시를 남긴 뒤 종료한다. 이후 Loki는 10001:10001 사용자로 실행된다.

otel-collector-init은 application-logs와 otel-collector-data의 완료 표시를 각각 확인한다. 표시가 없는 볼륨만 root로 디렉터리와 10001:10001 소유권·접근 권한을 준비하고, 성공한 볼륨에 완료 표시를 남긴 뒤 종료한다. Web·Reservation·Payment와 Collector는 초기화 성공 후 10001:10001 사용자로 실행한다. 앱은 로그 볼륨에 파일을 쓰고 Collector는 읽기 전용으로 연결한다. Collector의 상태 볼륨만 쓰기 가능하게 연결한다.

완료 표시는 각 볼륨 루트의 .permissions-10001-10001-v1 파일이다. 초기화 컨테이너는 docker compose up에서 다시 실행될 수 있지만, 표시가 있으면 디렉터리 생성과 재귀 소유권 변경·권한 설정을 건너뛴다. 기존 볼륨에 표시가 없으면 처음 한 번 초기화한다. 컨테이너를 다시 만들거나 docker compose down 후 up을 해도 볼륨이 유지되면 표시가 남는다. 볼륨을 새로 만들면 표시도 없어지므로 해당 볼륨만 다시 초기화한다. 초기화에 실패한 볼륨에는 표시를 남기지 않아 다음 실행에서 재시도한다.

```sh
docker compose up --build -d --wait
docker compose ps
```

| 대상 | 접속 정보 |
| --- | --- |
| 검색·예약 화면 | http://localhost:8080 |
| Reservation API | http://localhost:8081 |
| Payment 상태 확인 | http://localhost:8082/actuator/health |
| Grafana | http://localhost:3000, admin / rental-local |
| Prometheus | http://localhost:9090 |
| Tempo | http://localhost:3200 |
| Loki 준비 상태 | http://localhost:3100/ready |
| Collector 상태 | http://localhost:13133 |
| Collector 추적 수신 | http://localhost:4318/v1/traces |
| Kafka, 로컬 앱 | localhost:9092 |
| Kafka, 컨테이너 사이 | kafka:9092 |
| 예약 DB | localhost:5432 / reservation / reservation / reservation-local |
| 결제 DB | localhost:5433 / payment / payment / payment-local |

DB 접속 정보는 주소 / DB / 계정 / 비밀번호 순서다. 비동기 모드에서는 Payment의 HTTP 결제 API를 열지 않으며 Payment 상태 확인과 관측 주소는 유지한다.

Kafka는 처음 접속한 주소 이후에 브로커가 알려 주는 advertised listener 주소로 통신한다. 컨테이너가 localhost로 되돌아가거나 로컬 앱이 kafka라는 컨테이너 이름을 받지 않도록 내부·외부 listener를 나눴다. 호스트의 9092는 컨테이너의 외부 listener인 29092에 연결된다.

## 접수와 최종 상태를 나눠 확인하기

화면에서 미래의 빈 기간으로 차량을 선점하고 모의 결제를 누른다. 결제 접수 안내 후 상세 화면이 2초마다 상태를 조회한다. 최종 상태는 CONFIRMED 또는 PAYMENT_FAILED다. 기본 10% 거절이 있으므로 접수 성공과 승인 성공을 같은 의미로 보지 않는다.

API는 PowerShell에서 다음처럼 확인한다.

```powershell
$headers = @{ 'Idempotency-Key' = [guid]::NewGuid().ToString() }
$body = @{
    branchId = 1
    vehicleId = 1
    pickupAt = '2032-03-01T10:00:00'
    returnAt = '2032-03-02T10:00:00'
} | ConvertTo-Json
$held = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/reservations' -Headers $headers -ContentType 'application/json' -Body $body
$id = $held.id
$queued = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8081/api/reservations/$id/payment"
$queued.StatusCode
$queued.Headers.Location
$queued.Content
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    $current = Invoke-RestMethod "http://localhost:8081/api/reservations/$id"
    $current.status
    if ($current.status -in @('CONFIRMED', 'PAYMENT_FAILED')) { break }
    Start-Sleep -Milliseconds 500
}
```

새 결제 요청의 응답은 202이고 Location은 예약 상세 API 주소다. 반복 조회가 끝나도 PAYMENT_PENDING이라면 최종 결과를 아직 확인하지 못한 것이며 승인 성공으로 집계하지 않는다. 이미 완료된 예약에 같은 결제 POST를 보내면 200과 기존 최종 상태를 반환한다.

같은 예약 ID의 결제 요청을 여러 번 보내도 예약·결제 DB와 결과는 같아야 한다. 실제로 재실행할 때는 이미 확정된 기간을 피하도록 새 날짜를 사용한다.

## Kafka의 처리 위치 확인

토픽과 컨슈머 그룹의 처리 위치는 다음 명령으로 확인한다.

```sh
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group payment-workers --describe
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:9092 --group reservation-results --describe
```

LAG는 각 파티션의 아직 처리하지 못한 건수다. 결제를 모두 처리하고 나면 두 그룹의 lag가 0으로 내려가는지 확인한다. DLT에 보관하고 원본 소비 위치를 진행한 경우에도 원본 lag는 0이 될 수 있으므로 DLT 건수와 예약의 최종 상태도 같이 확인한다.

각 DB의 아웃박스는 별도로 조회한다. published_at이 있는 메시지는 Kafka 전송 승인을 받은 것이며 소비자가 업무 처리를 완료했다는 뜻은 아니다.

```sql
-- 예약 DB와 결제 DB에서 각각 실행한다.
SELECT event_id, topic, message_key, created_at, available_at, published_at, lease_token
FROM outbox_message
ORDER BY created_at DESC;

SELECT COUNT(*) AS unpublished
FROM outbox_message WHERE published_at IS NULL;
```

최종 데이터 정합성도 확인한다.

```sql
-- 결제 DB: 예약 ID별 결제가 한 행인지와 최종 상태를 확인한다.
SELECT reservation_id, amount, expires_at, completed_at, status FROM payment;

-- 예약 DB: 활성 배정끼리 같은 차량과 기간이 겹치지 않아야 한다. 기대 결과는 0행이다.
SELECT a.id, b.id
FROM reservation a
JOIN reservation b ON a.vehicle_id = b.vehicle_id
    AND a.id < b.id AND a.during && b.during
WHERE a.status IN ('HELD', 'PAYMENT_PENDING', 'CONFIRMED')
  AND b.status IN ('HELD', 'PAYMENT_PENDING', 'CONFIRMED');
```

위 두 SELECT는 서로 다른 DB에서 실행한다. 서비스 코드가 다른 서비스의 DB를 직접 조회하게 만들지는 않는다.

## 중단과 재전달 검증

다음은 기능의 실패 경로를 확인하는 실험이다. 각 실험에는 새로운 예약과 빈 대여 기간을 사용한다.

| 실험 | 관찰할 결과 |
| --- | --- |
| Payment 중단 후 접수 | 202는 반환되며 예약은 결제 중 유지, 요청 토픽 lag 증가 |
| Kafka 중단 후 접수 | 요청 아웃박스에 미전송 행이 남고 예약은 결제 중 유지 |
| 다시 시작 | 아웃박스 전송·소비가 재개되고 최종 상태 반영 |
| 같은 요청 이벤트 재전달 | 결제 한 행과 동일 결과 유지, 확정 상태 중복 변경 없음 |
| 형식이 잘못된 이벤트 | DLT에 보관되고 정상 메시지 처리는 계속 진행 |
| 승인 기한을 넘긴 대기 | Payment가 FAILED를 저장한 뒤 예약 배정 해제 |

앱들이 정상 시작한 뒤 Payment만 중지해 본다.

```sh
docker compose stop payment-service
```

새 예약의 결제 API를 호출하고 202와 PAYMENT_PENDING, payment-workers 그룹의 lag를 확인한다. 아웃박스 전송이 끝났다면 예약 DB 미전송 건수는 0이어도 소비 lag는 남는다. 다음 명령으로 다시 시작한다.

```sh
docker compose start payment-service
```

Kafka 중단 실험도 정상 시작 이후 진행한다. 현재 Compose의 앱 기동 순서는 Kafka의 초기 상태 확인을 기다리므로 브로커를 끈 채 앱을 처음 시작하는 실험과는 구분한다.

```sh
docker compose stop kafka
```

새 결제를 접수하고 예약 DB에서 미전송 아웃박스와 전송 실패 메트릭을 확인한다. 이후 다시 시작해 메시지가 전송되고 결과가 반영되는지 확인한다. 임대 중 프로세스까지 종료했다면 최대 60초 임대 만료도 기다릴 수 있다.

```sh
docker compose start kafka
```

같은 요청 이벤트를 재전달하려면 처리한 메시지의 키와 본문을 읽어 같은 토픽에 보낸다. PowerShell 예시는 가장 먼저 보관된 한 건을 사용하므로 대상 예약 ID를 확인한 뒤 실행한다.

```powershell
$record = docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic payment.requested.v1 --from-beginning --max-messages 1 --timeout-ms 10000 --property print.key=true --property 'key.separator=|'
$record
$record | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic payment.requested.v1 --property parse.key=true --property 'key.separator=|'
```

결제 DB의 해당 예약 ID 행과 최종 상태가 바뀌지 않아야 한다. 이미 전송 완료한 같은 결과 아웃박스를 무조건 다시 발행하지는 않는다. 결과 반영 실패가 DLT에 남은 경우에는 그 결과 메시지를 재전달해야 한다.

형식 오류 실험은 예약에 연결되지 않는 잘못된 JSON 한 건을 요청 토픽에 보낸다.

```powershell
'demo|{not-json}' | docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic payment.requested.v1 --property parse.key=true --property 'key.separator=|'
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic payment.requested.v1.DLT --from-beginning --max-messages 1 --timeout-ms 10000 --property print.key=true --property 'key.separator=|'
```

DLT는 자동 승인·거절 큐가 아니다. 실제 업무 메시지가 DLT에 남았다면 먼저 예외와 원래 예약·결제 상태를 확인하고 처리 코드나 조건을 수정한다. 그 뒤 읽은 메시지의 키·본문을 해당 원본 토픽으로 재전송한다. 위의 의도적으로 잘못 만든 JSON을 그대로 재전송하면 다시 DLT로 이동한다. DLT를 읽었다는 이유로 원본 예약의 상태를 직접 덮어쓰지 않는다.

## 동기·비동기 지연 비교

모드를 바꾸기 전에는 테스트 예약의 최종 상태, 두 DB의 미전송 0건, 두 그룹의 lag 0과 DLT 미처리 여부를 확인한다. 처리 중에 모드를 바꾸면 한쪽은 HTTP를 기대하고 다른 쪽은 Kafka만 소비하는 상태가 될 수 있다. 두 서비스는 항상 같은 PAYMENT_MODE로 재생성한다.

먼저 승인만 일어나는 동기 기준 실험을 실행한다. PowerShell에서 다음처럼 설정한다.

```powershell
$env:PAYMENT_MODE = 'sync'
$env:PAYMENT_FAILURE_RATE = '0'
$env:PAYMENT_MIN_MILLIS = '200'
$env:PAYMENT_MAX_MILLIS = '5000'
$env:VUS = '5'
$env:ITERATIONS = '20'
$env:RUN_ID = '10'
docker compose up -d --wait payment-service reservation-service web-service
docker compose --profile load run --rm k6 run /scripts/payment-latency.js
```

비동기 모드로 재생성하고 같은 동시 사용자 수·요청 수·승인 지연으로 측정한다.

```powershell
$env:PAYMENT_MODE = 'async'
$env:RUN_ID = '11'
docker compose up -d --wait payment-service reservation-service web-service
docker compose --profile load run --rm k6 run /scripts/async-payment-latency.js
```

기존 payment-latency.js는 200 최종 결과를 기대하는 동기 전용 스크립트다. async-payment-latency.js는 202 접수 지연과 예약 상세에서 최종 상태를 관측할 때까지의 시간을 분리한다. 기본 0.5초 간격으로 조회하므로 관측 시간에는 최대 한 조회 간격 수준의 추가 지연이 생긴다.

두 스크립트의 RUN_ID는 이미 확정한 기간 재사용을 피하는 값이다. 재실행마다 바꾸고 ITERATIONS는 1~1,000으로 사용한다. 동기 스크립트는 2030년 7월, 비동기 스크립트는 2031년 1월을 기준으로 서로 다른 슬롯을 만든다. 실제 실행 시 생성된 인수일이 미래인지 확인한다.

| 방식 | 결제 API p95 / p99 | 최종 상태 관측 p95 / p99 | DB 확정 지연 | 미완료 수 | 미전송·lag·DLT |
| --- | --- | --- | --- | --- | --- |
| sync | 직접 측정 | 응답이 최종 결과 | 직접 측정 | 직접 측정 | 해당 모드의 상태 확인 |
| async | 202 접수 지연 측정 | 조회로 최종 상태를 관측한 시간 | 직접 측정 | 직접 측정 | 직접 측정 |

이 표는 측정 전 양식이다. 비동기 접수 지연이 줄어도 결제 소비가 밀리면 최종 확정 시간은 늘어날 수 있다. 가상 사용자마다 최종 상태를 기다리는 폐쇄형 부하이므로 동시에 미완료 상태로 남을 수 있는 요청도 VUS의 영향을 받는다. 이 실험만으로 지속 유입 한계 처리량을 단정하지 않는다.

부하를 높일 때도 202 건수뿐 아니라 unfinished_payments, 컨슈머 lag, 오래된 미전송 시간과 예약의 PAYMENT_PENDING 체류를 함께 본다. 원래 같은 차량·기간의 동시 선점 검증은 기존 명령을 사용한다.

```sh
docker compose --profile load run --rm k6
```

## 대시보드·추적·로그 읽기

로그·메트릭·추적은 OpenTelemetry Collector를 거쳐 저장소로 전달한다.

| 데이터 | 수집·저장 경로 |
| --- | --- |
| 로그 | 앱 JSON 파일 → Collector filelog → Loki |
| 메트릭 | Actuator → Collector prometheus 수신기 → Collector 8889 → Prometheus |
| 추적 | 앱 OpenTelemetry → Collector 4318 → Tempo |

Prometheus는 Collector의 주소만 수집한다. 앱의 OTLP 메트릭 전송은 끄며 application 태그와 기존 히스토그램 이름을 유지한다. Collector 상태는 http://localhost:13133 에서 확인하고, 전달 결과는 실제 로그·메트릭·추적 조회로 확인한다. 이 상태 주소만으로 저장소 전송 성공을 판단하지 않는다.

Grafana의 렌트카 동기·비동기 결제 관측 대시보드에서 동기 요청 p95·p99, 비동기 접수 p95, 예약 생성부터 확정까지의 p95를 비교한다. 최근 5분 이동 구간을 사용하므로 모드 전환 직후에는 이전 실험의 데이터가 섞일 수 있다. 실험 구간과 집계 범위를 맞춰 해석한다.

대시보드 위쪽에는 기본 운영 지표를 두고, 그 아래에 기존 결제 지연 패널을 유지한다. 아래쪽의 앱 로그·ERROR 로그 패널은 Loki를 조회한다.

| 패널 | 확인할 내용 |
| --- | --- |
| 메트릭 수집 상태 | 앱의 메트릭 주소와 Collector 수집 성공은 1, 실패는 0이다. 업무 기능의 정상 여부와는 별개다. |
| 프로세스 CPU 사용률 | JVM이 보고하는 앱 프로세스의 CPU 사용률이다. 호스트나 Docker 전체 사용률은 아니다. |
| JVM 힙 메모리 | 사용 용량과 JVM이 현재 확보한 용량을 비교한다. 확보 용량은 최대 허용 용량이나 프로세스 전체 메모리가 아니다. |
| GC 중단 시간 비율 | 사용하지 않는 객체를 정리하는 GC 때문에 멈춘 누적 시간을 최근 5분의 비율로 본다. 개별 GC의 p95는 아니다. |
| HTTP 처리량·5xx 비율 | Actuator 요청을 제외한 초당 요청 수와 서버 오류 비율이다. 예약 충돌 409를 5xx 오류로 세지 않는다. |
| JVM 스레드 | 현재 스레드와 모니터 잠금을 기다리는 BLOCKED 스레드를 비교한다. HTTP 요청 수나 가상 스레드 전체 수가 아니다. |
| DB 사용·최대 연결·대기 | 사용 연결이 최대치에 가까워질 때 연결을 빌리려는 대기가 늘어나는지 확인한다. DB가 없는 Web에는 값이 없다. |
| 앱 로그·ERROR 로그 | 대시보드 조회 시간 범위에서 세 앱의 최신 로그를 각각 최대 200줄 조회한다. ERROR가 없으면 해당 패널이 비어 있을 수 있다. |

처리량이 늘면서 CPU·힙·GC·DB 대기 중 무엇이 함께 늘어나는지 보고 결제 지연과 연결해 해석한다. 요청이 없거나 GC가 아직 발생하지 않은 구간에는 일부 지표가 표시되지 않을 수 있다. 수집이 끊긴 상태를 정상적인 0으로 해석하지 않도록 수집 상태도 함께 본다.

로그 패널의 한 줄을 펼치면 JSON 필드와 traceId의 Tempo 링크를 확인할 수 있다. 조회 범위가 넓거나 예약 ID로 더 좁혀야 하면 Explore에서 검색한다. 패널은 Loki의 조회 결과를 표시하므로 수집되지 않은 로그를 새로 만들어 주지는 않는다.

아웃박스 미전송과 오래된 미전송 시간은 DB → Kafka 구간을 보여 준다. Kafka 그룹 lag는 Kafka → 소비자의 구간을 보여 준다. Kafka 리스너 p95에는 Payment의 모의 승인 대기도 포함된다. DB 연결 사용량과 비교해 sleep 중 연결을 잡지 않는지도 확인한다.

Tempo에서는 원래 결제 접수 traceId로 추적을 찾는다. 요청이 반환된 뒤 실행된 outbox.publish와 Kafka 전송·소비, payment.mock, 결과 아웃박스 전송, reservation.payment.result가 이어지는지 확인한다. 수동 콘솔 재전송은 원래 추적 헤더를 복사하지 않으므로 별도 trace가 생길 수 있으며 reservationId로 같은 예약을 연결한다.

```sh
docker compose logs --tail=200 reservation-service payment-service
```

PowerShell에서는 예약 ID로 로그를 좁힌다.

```powershell
docker compose logs --no-log-prefix reservation-service payment-service | Select-String $id
```

결제·아웃박스 처리 로그는 JSON의 traceId와 reservationId로 찾는다. 시작 로그나 메시지를 해석하기 전에 발생한 JSON 형식 오류처럼 예약에 연결되지 않는 로그에는 reservationId가 없을 수 있다.

Grafana Explore에서 Loki를 선택해 예약 ID로 Reservation·Payment의 로그를 함께 검색한다. Collector의 filelog 수신기가 공유 application-logs 볼륨에서 앱 3개의 JSON 파일을 읽어 Loki에 OTLP로 전송한다. 아웃박스와 소비 재시도로 traceId가 여러 개라면 reservationId로 먼저 묶고 각 로그의 Tempo 링크를 연다.

```logql
{compose_project="rental-car", service_name=~"reservation-service|payment-service"}
  | json
  | __error__=""
  | reservationId="실제-예약-UUID"
```

원본 JSON의 reservationId는 검색할 때 읽는 필드이며 Loki 수집 라벨에 넣지 않는다. JSON으로 해석하지 못한 줄은 __error__ 조건으로 제외한다. 특정 예약을 해석하지 못한 메시지 형식 오류는 service_name 라벨과 JSON의 level 필드로도 찾아야 한다. 다음 검색은 예약 ID 유무와 관계없이 앱의 ERROR 로그를 보여 준다.

```logql
{compose_project="rental-car"}
  | json
  | __error__=""
  | level="ERROR"
```

Loki는 로그를 loki-data에 저장하고 7일이 지난 로그를 주기적인 삭제 대상으로 삼는다. Collector는 읽은 위치와 Loki 전송 대기 로그를 otel-collector-data에 보관한다. 앱은 로그를 application-logs에 쓰고 10MB·날짜 기준으로 순환하며 최대 7일·200MB의 순환 파일 보관 한도를 사용한다. 이 파일 수집 구성은 Docker 실행 기준이며 재전송 과정에서 로그가 중복될 수 있다.

## 로컬 JDK 실행과 종료

Java 25와 Gradle을 준비하고 앱을 호스트에서 실행할 수도 있다. 기존 Compose 앱을 중지한 뒤 DB·Kafka·관측 도구를 시작한다.

```sh
docker compose stop web-service reservation-service payment-service
docker compose up -d --wait postgres payment-postgres kafka otel-collector prometheus tempo loki grafana
gradle clean bootJar
```

각 앱의 VM 옵션은 -Duser.timezone=Asia/Seoul로 맞춘다. 앱 기본값은 비동기 모드, Kafka localhost:9092, 예약 DB localhost:5432, 결제 DB localhost:5433, Collector localhost:4318을 사용한다. 각 명령을 다른 터미널에서 실행한다.

```sh
java -Duser.timezone=Asia/Seoul -jar payment-service/build/libs/payment-service.jar
```

```sh
java -Duser.timezone=Asia/Seoul -jar reservation-service/build/libs/reservation-service.jar
```

```sh
java -Duser.timezone=Asia/Seoul -jar web-service/build/libs/web-service.jar
```

IntelliJ도 동일한 VM 옵션·환경 변수를 적용한다. 호스트에서 sync 비교 모드를 실행하려면 Payment와 Reservation 두 실행 설정 모두에 PAYMENT_MODE=sync를 지정한다.

Collector의 prometheus 수신기는 기본적으로 Compose 서비스 이름을 조회한다. 호스트 앱의 메트릭을 수집하려면 infra/otel-collector/collector.yaml의 세 대상을 host.docker.internal:8080, host.docker.internal:8081, host.docker.internal:8082로 바꾸고 otel-collector를 재시작한다. Docker Desktop 기준이며 Linux Docker Engine에서는 otel-collector의 extra_hosts에 host.docker.internal:host-gateway 매핑도 추가한다. Prometheus는 otel-collector:8889 수집을 유지한다. Docker 실행으로 돌아갈 때는 Collector의 세 대상을 원래대로 되돌린다. 호스트 추적도 localhost:4318의 Collector를 사용한다. 호스트 로그는 기본 ./logs에 남고 Docker 공유 볼륨을 읽는 Collector가 자동 수집하지 않는다.

종료하면 DB·Kafka·관측 데이터 볼륨은 유지한다. 로컬 JDK 앱은 각 터미널에서 Ctrl+C로 종료한다.

```sh
docker compose down
```


## 패키지 배치

| 위치 | 역할 |
| --- | --- |
| application/service | 선점·결제 처리 순서, Payment의 모의 승인 흐름 |
| application/transaction | 짧은 업무 DB 트랜잭션 |
| application/dto | 기능별 요청·응답·이벤트와 아웃박스 스냅샷, 클래스마다 별도 파일 |
| application/port | 외부 결제 호출과 메시지 보관 계약 |
| domain/entity, domain/vo, domain/enums | 애그리거트·엔티티, 값 객체, 상태 |
| domain/repository, domain/exception | 업무 저장소 계약과 업무 규칙 예외 |
| infrastructure/repository/jpa, adapter | JPA 저장소와 도메인 저장소 연결 |
| infrastructure/messaging/producer, consumer, support | 이벤트 보관 구현, Kafka 소비, 안정적인 이벤트 ID 생성 |
| infrastructure/outbox/entity, transaction | 기술적인 메시지 보관 엔티티와 임대·완료 DB 처리 |
| infrastructure/scheduler | 아웃박스 전송, 선점 만료, sync 모드 HTTP 재확인 |
| infrastructure/tracing, config | 추적 문맥 저장과 앱 설정 |
| infrastructure/client, simulator | sync HTTP 호출과 모의 승인 지연 |
| presentation/controller, exception | API 요청 처리와 HTTP 오류 변환 |
| Web의 controller, client, dto | 화면·API 호출·기능별 DTO |

요청·응답 DTO를 서비스나 컨트롤러의 내부 클래스로 선언하지 않는다. 기술적인 아웃박스 엔티티를 업무 애그리거트와 섞지 않는다.

## 프로젝트 구조

```text
rental-car/
|-- .dockerignore
|-- .gitignore
|-- Dockerfile
|-- README.md
|-- build.gradle
|-- compose.yaml
|-- infra
|   |-- grafana
|   |   |-- dashboards
|   |   |   |-- rental-payment.json
|   |   |-- provisioning
|   |   |   |-- dashboards
|   |   |   |   |-- dashboards.yaml
|   |   |   |-- datasources
|   |   |   |   |-- datasources.yaml
|   |-- loki
|   |   |-- loki.yaml
|   |-- otel-collector
|   |   |-- collector.yaml
|   |-- prometheus
|   |   |-- prometheus.yaml
|   |-- tempo
|   |   |-- tempo.yaml
|-- payment-service
|   |-- build.gradle
|   |-- src
|   |   |-- main
|   |   |   |-- java
|   |   |   |   |-- com
|   |   |   |   |   |-- example
|   |   |   |   |   |   |-- rental
|   |   |   |   |   |   |   |-- payment
|   |   |   |   |   |   |   |   |-- PaymentApplication.java
|   |   |   |   |   |   |   |   |-- application
|   |   |   |   |   |   |   |   |   |-- dto
|   |   |   |   |   |   |   |   |   |   |-- PaymentRequest.java
|   |   |   |   |   |   |   |   |   |   |-- PaymentResponse.java
|   |   |   |   |   |   |   |   |   |   |-- messaging
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxBatch.java
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxDelivery.java
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentCompleted.java
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentRequested.java
|   |   |   |   |   |   |   |   |   |-- port
|   |   |   |   |   |   |   |   |   |   |-- ApprovalGateway.java
|   |   |   |   |   |   |   |   |   |   |-- PaymentResultPublisher.java
|   |   |   |   |   |   |   |   |   |-- service
|   |   |   |   |   |   |   |   |   |   |-- PaymentService.java
|   |   |   |   |   |   |   |   |   |-- transaction
|   |   |   |   |   |   |   |   |   |   |-- PaymentTransactions.java
|   |   |   |   |   |   |   |   |-- domain
|   |   |   |   |   |   |   |   |   |-- entity
|   |   |   |   |   |   |   |   |   |   |-- Payment.java
|   |   |   |   |   |   |   |   |   |-- enums
|   |   |   |   |   |   |   |   |   |   |-- PaymentStatus.java
|   |   |   |   |   |   |   |   |   |-- exception
|   |   |   |   |   |   |   |   |   |   |-- PaymentConflictException.java
|   |   |   |   |   |   |   |   |   |-- repository
|   |   |   |   |   |   |   |   |   |   |-- PaymentRepository.java
|   |   |   |   |   |   |   |   |-- infrastructure
|   |   |   |   |   |   |   |   |   |-- config
|   |   |   |   |   |   |   |   |   |   |-- KafkaConfiguration.java
|   |   |   |   |   |   |   |   |   |   |-- PaymentConfiguration.java
|   |   |   |   |   |   |   |   |   |-- messaging
|   |   |   |   |   |   |   |   |   |   |-- consumer
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentRequestListener.java
|   |   |   |   |   |   |   |   |   |   |-- producer
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxPaymentResultPublisher.java
|   |   |   |   |   |   |   |   |   |   |-- support
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentEventIds.java
|   |   |   |   |   |   |   |   |   |-- outbox
|   |   |   |   |   |   |   |   |   |   |-- entity
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxMessage.java
|   |   |   |   |   |   |   |   |   |   |-- transaction
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxTransactions.java
|   |   |   |   |   |   |   |   |   |-- repository
|   |   |   |   |   |   |   |   |   |   |-- adapter
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |-- jpa
|   |   |   |   |   |   |   |   |   |   |   |-- JpaOutboxRepository.java
|   |   |   |   |   |   |   |   |   |   |   |-- JpaPaymentRepository.java
|   |   |   |   |   |   |   |   |   |-- scheduler
|   |   |   |   |   |   |   |   |   |   |-- OutboxRelay.java
|   |   |   |   |   |   |   |   |   |-- simulator
|   |   |   |   |   |   |   |   |   |   |-- MockApprovalGateway.java
|   |   |   |   |   |   |   |   |   |-- tracing
|   |   |   |   |   |   |   |   |   |   |-- TraceCarrier.java
|   |   |   |   |   |   |   |   |-- presentation
|   |   |   |   |   |   |   |   |   |-- controller
|   |   |   |   |   |   |   |   |   |   |-- PaymentController.java
|   |   |   |   |   |   |   |   |   |-- exception
|   |   |   |   |   |   |   |   |   |   |-- ApiExceptionHandler.java
|   |   |   |-- resources
|   |   |   |   |-- application.yaml
|   |   |   |   |-- db
|   |   |   |   |   |-- migration
|   |   |   |   |   |   |-- V1__create_payment.sql
|   |   |   |   |   |   |-- V2__create_outbox.sql
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
|   |   |   |   |   |   |   |   |   |   |-- messaging
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxBatch.java
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxDelivery.java
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentCompleted.java
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentRequested.java
|   |   |   |   |   |   |   |   |   |   |-- payment
|   |   |   |   |   |   |   |   |   |   |   |-- CheckoutCompletion.java
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentCommand.java
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentResult.java
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
|   |   |   |   |   |   |   |   |   |   |-- PaymentUnavailableException.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationNotFoundException.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleNotFoundException.java
|   |   |   |   |   |   |   |   |   |-- port
|   |   |   |   |   |   |   |   |   |   |-- PaymentGateway.java
|   |   |   |   |   |   |   |   |   |   |-- PaymentRequestPublisher.java
|   |   |   |   |   |   |   |   |   |-- service
|   |   |   |   |   |   |   |   |   |   |-- CheckoutService.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationExpiryService.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationService.java
|   |   |   |   |   |   |   |   |   |   |-- VehicleSearchService.java
|   |   |   |   |   |   |   |   |   |-- transaction
|   |   |   |   |   |   |   |   |   |   |-- CheckoutTransactions.java
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
|   |   |   |   |   |   |   |   |   |-- client
|   |   |   |   |   |   |   |   |   |   |-- PaymentClient.java
|   |   |   |   |   |   |   |   |   |-- config
|   |   |   |   |   |   |   |   |   |   |-- KafkaConfiguration.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationConfiguration.java
|   |   |   |   |   |   |   |   |   |-- messaging
|   |   |   |   |   |   |   |   |   |   |-- consumer
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentResultListener.java
|   |   |   |   |   |   |   |   |   |   |-- producer
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxPaymentRequestPublisher.java
|   |   |   |   |   |   |   |   |   |   |-- support
|   |   |   |   |   |   |   |   |   |   |   |-- PaymentEventIds.java
|   |   |   |   |   |   |   |   |   |-- outbox
|   |   |   |   |   |   |   |   |   |   |-- entity
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxMessage.java
|   |   |   |   |   |   |   |   |   |   |-- transaction
|   |   |   |   |   |   |   |   |   |   |   |-- OutboxTransactions.java
|   |   |   |   |   |   |   |   |   |-- repository
|   |   |   |   |   |   |   |   |   |   |-- adapter
|   |   |   |   |   |   |   |   |   |   |   |-- BranchRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |   |-- ReservationRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |   |-- VehicleRepositoryAdapter.java
|   |   |   |   |   |   |   |   |   |   |-- jpa
|   |   |   |   |   |   |   |   |   |   |   |-- JpaBranchRepository.java
|   |   |   |   |   |   |   |   |   |   |   |-- JpaOutboxRepository.java
|   |   |   |   |   |   |   |   |   |   |   |-- JpaReservationRepository.java
|   |   |   |   |   |   |   |   |   |   |   |-- JpaVehicleRepository.java
|   |   |   |   |   |   |   |   |   |-- scheduler
|   |   |   |   |   |   |   |   |   |   |-- OutboxRelay.java
|   |   |   |   |   |   |   |   |   |   |-- PaymentRecoveryJob.java
|   |   |   |   |   |   |   |   |   |   |-- ReservationExpiryJob.java
|   |   |   |   |   |   |   |   |   |-- tracing
|   |   |   |   |   |   |   |   |   |   |-- TraceCarrier.java
|   |   |   |   |   |   |   |   |-- presentation
|   |   |   |   |   |   |   |   |   |-- controller
|   |   |   |   |   |   |   |   |   |   |-- CheckoutController.java
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
|   |   |   |   |   |   |-- V4__add_payment_state.sql
|   |   |   |   |   |   |-- V5__create_outbox.sql
|-- scripts
|   |-- async-payment-latency.js
|   |-- payment-latency.js
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
|   |   |   |   |   |   |   |   |   |-- PaymentPageController.java
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
