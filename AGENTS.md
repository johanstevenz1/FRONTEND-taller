# AGENTS.md — Sistema de monitoreo de microservicios con Proxy de auditoría y logging

Este archivo le explica al agente de IA **qué construir, cómo construirlo y cómo verificar que todo funciona**. Léelo completo antes de escribir una sola línea de código.

---

## 0. Reglas innegociables

1. **Todo el código va en inglés**: nombres de clases, métodos, variables, endpoints, campos JSON, comentarios, mensajes de log, textos de la UI y mensajes de error. Solo este `AGENTS.md` y el chat con el usuario están en español.
2. **Todo debe compilar y ejecutarse sin errores.** No entregues nada que no hayas compilado tú mismo (ver sección 8, "Definición de terminado").
3. **No dejes TODOs, stubs ni métodos vacíos.** Cada operación de cada servicio debe estar realmente implementada.
4. **No uses Lombok** (evita fallos de compilación por annotation processing). Usa `record`s de Java y constructores explícitos.
5. **No inventes dependencias.** Usa solo las listadas aquí. Si necesitas otra, justifícala en el README.
6. **No uses base de datos externa.** Los logs y datos de los servicios viven en memoria (estructuras thread-safe).
7. **Al final, entrega los archivos al usuario** (sección 9). El usuario hará el despliegue manualmente en Railway y Vercel.
8. Trabajo **individual**, sin servicios extra, sin autenticación.

---

## 1. Contexto del proyecto

**Nombre:** Microservices Monitoring System with Audit & Logging Proxy.

Un sistema con **3 microservicios** (Inventory, Orders, Payments). Un **Proxy de Logging** se sitúa frente a cada microservicio e intercepta todas las llamadas para registrar métricas de uso, tiempos de respuesta y errores. El **frontend** muestra un dashboard de observabilidad en tiempo real.

Se construyen dos aplicaciones:

| Parte    | Tecnología                                                    | Despliegue        |
|----------|---------------------------------------------------------------|-------------------|
| Backend  | **Java 17 + Spring Boot 3.3.x + Maven** (Spring Web, Validation) | Railway (Docker)  |
| Frontend | **React 18 + Vite + TypeScript + Recharts**                   | Vercel            |

---

## 2. Estructura del repositorio

```
/
├── AGENTS.md
├── README.md                  # how to run locally + deploy steps (in English)
├── .gitignore
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── .dockerignore
│   └── src/
│       ├── main/java/com/monitoring/
│       │   ├── MonitoringApplication.java
│       │   ├── config/        (CorsConfig)
│       │   ├── proxy/         (MicroserviceProxy, LoggingProxy)
│       │   ├── service/       (InventoryService, OrderService, PaymentService)
│       │   ├── model/         (LogEntry, CallStatus, ServiceSummary, PageResponse, ...)
│       │   ├── store/         (LogStore)
│       │   ├── controller/    (ServiceController, MetricsController)
│       │   └── exception/     (ServiceExecutionException, PaymentFailedException, GlobalExceptionHandler)
│       ├── main/resources/application.properties
│       └── test/java/com/monitoring/   (unit tests)
└── frontend/
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts
    ├── index.html
    ├── vercel.json
    ├── .env.example
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── api/client.ts
        ├── types.ts
        ├── hooks/usePolling.ts
        ├── components/  (ServiceCard, ResponseTimeChart, LogsTable, LogFilters, LogDetails, SimulateLoadButton)
        └── styles.css
```

---

## 3. Backend

### 3.1 Configuración

- Java 17, Spring Boot 3.3.x, Maven. Group `com.monitoring`, artifact `monitoring-backend`.
- Dependencias: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-test` (scope test). Nada más.
- `application.properties`:
  ```properties
  server.port=${PORT:8080}
  app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:5173}
  ```
- Genera el JAR con nombre fijo: `<finalName>app</finalName>` en `pom.xml` (para el Dockerfile).
- CORS: permitir los orígenes de `app.cors.allowed-origins` (lista separada por comas), métodos `GET, POST, OPTIONS`, todos los headers. Debe aceptar también `*` si es lo que se configura.

### 3.2 Interfaz genérica del proxy

```java
public interface MicroserviceProxy<T> {
    T execute(String operation, Object... params);
    String getServiceId();
}
```

- Los tres servicios reales implementan `MicroserviceProxy<Object>` (el resultado es un `Map`/`record`/`List`, serializable a JSON).
- `getServiceId()` devuelve exactamente: `inventory`, `orders`, `payments`.

### 3.3 Servicios reales (con operaciones implementadas)

Todos usan estructuras en memoria `ConcurrentHashMap` y son thread-safe. Datos iniciales (seed) al arrancar. Los `params` llegan desde JSON, así que los números pueden ser `Integer`, `Long` o `Double`: crea un helper para convertir con seguridad (`((Number) p).intValue()`, `String.valueOf(...)`, etc.) y lanza `IllegalArgumentException` con mensaje claro si falta un parámetro o el tipo es inválido.

**InventoryService** (`serviceId = "inventory"`) — seed: 5 productos (ej. `P-100` Laptop, `P-200` Mouse, `P-300` Keyboard, `P-400` Monitor, `P-500` Headset) con stock inicial.

| operation       | params                          | result |
|-----------------|---------------------------------|--------|
| `listProducts`  | —                               | list of products (`id`, `name`, `stock`, `price`) |
| `checkStock`    | `productId: String`             | `{productId, name, stock}` |
| `addStock`      | `productId: String, quantity: int` | updated product |
| `reserveStock`  | `productId: String, quantity: int` | updated product; throws `IllegalStateException("Insufficient stock")` if not enough |

**OrderService** (`serviceId = "orders"`)

| operation      | params                                               | result |
|----------------|------------------------------------------------------|--------|
| `createOrder`  | `customerId: String, productId: String, quantity: int` | new order `{orderId (UUID), customerId, productId, quantity, status: "CREATED", createdAt}` |
| `getOrder`     | `orderId: String`                                    | order, or `NoSuchElementException("Order not found")` |
| `cancelOrder`  | `orderId: String`                                    | order with `status: "CANCELLED"` |
| `listOrders`   | —                                                    | list of orders |

**PaymentService** (`serviceId = "payments"`)

| operation        | params                                          | result |
|------------------|-------------------------------------------------|--------|
| `processPayment` | `orderId: String, amount: number, method: String` | `{paymentId (UUID), orderId, amount, method, status: "APPROVED", processedAt}` |
| `getPayment`     | `paymentId: String`                             | payment, or `NoSuchElementException("Payment not found")` |
| `refundPayment`  | `paymentId: String`                             | payment with `status: "REFUNDED"` |

- **Fallas aleatorias:** en `PaymentService`, **cada llamada a cualquier operación** tiene un **10 %** de probabilidad de lanzar `PaymentFailedException("Simulated payment gateway failure")`. Usa `ThreadLocalRandom`. Haz la probabilidad configurable por una constante o propiedad (`app.payments.failure-rate=0.10`) pero el valor por defecto debe ser `0.10`.
- Una operación desconocida lanza `IllegalArgumentException("Unknown operation: <name>")`.
- Todos los servicios simulan latencia realista con `Thread.sleep` aleatorio de 20–300 ms (maneja `InterruptedException` restaurando el flag de interrupción) para que el dashboard muestre tiempos variados.

### 3.4 LoggingProxy

`LoggingProxy<T> implements MicroserviceProxy<T>` envuelve **cualquier** `MicroserviceProxy<T>` (composición, no herencia). Comportamiento de `execute(operation, params)`:

1. Genera `requestId = UUID.randomUUID().toString()` y toma `startNanos = System.nanoTime()`.
2. **Antes de ejecutar:** registra el inicio con SLF4J (`INFO`): `[requestId] START serviceId.operation`.
3. Ejecuta `delegate.execute(operation, params)`.
4. **Si OK:** calcula `durationMs`, crea un `LogEntry` con `status = SUCCESS`, guarda `params` y `response`, lo añade al `LogStore` y registra `INFO` con duración y resultado.
5. **Si falla (catch `Exception`):** calcula `durationMs`, crea `LogEntry` con `status = ERROR`, `errorMessage` y `stackTraceSummary` (**resumido**: nombre de la excepción + mensaje + máximo las 5 primeras líneas del stack trace, unidas por `\n`), lo guarda, registra `ERROR`, y **relanza** envuelto en `ServiceExecutionException` (RuntimeException) que conserva la causa y el `requestId`.
6. Nunca debe tragarse una excepción ni dejar de registrar el log (usa `try/catch` y asegura que el log se guarda en ambos caminos).

Cableado: en una clase `@Configuration` crea tres beans `MicroserviceProxy<Object>` (uno por servicio) envueltos con `LoggingProxy`, y un `Map<String, MicroserviceProxy<Object>>` indexado por `serviceId`. **Los controladores solo hablan con los proxies, nunca con los servicios directamente.**

### 3.5 Modelo de log

`LogEntry` (record) — se serializa tal cual a JSON:

```json
{
  "requestId": "3f1c2a7e-....",
  "serviceId": "payments",
  "operation": "processPayment",
  "durationMs": 142,
  "status": "SUCCESS",
  "timestamp": "2025-01-15T10:22:31.456Z",
  "params": ["ORD-1", 99.9, "CARD"],
  "response": { "paymentId": "...", "status": "APPROVED" },
  "errorMessage": null,
  "stackTraceSummary": null
}
```

- `status` es el enum `CallStatus { SUCCESS, ERROR }`.
- `timestamp` es `java.time.Instant`, serializado como string ISO-8601 (no como número).
- `params` es `List<Object>`; `response` es `Object`.

`LogStore`: componente en memoria thread-safe (por ejemplo `ConcurrentLinkedDeque` o lista sincronizada) con **tope de 5000 entradas** (descarta las más antiguas). Métodos: `add`, `findAll(service, status, from, to, page, size)` (ordenado por `timestamp` **descendente**), `all()`.

### 3.6 Endpoints

Prefijo común `/api`. Todos devuelven JSON.

**Ejecución de servicios (pasan por el logging proxy):**

```
POST /api/services/inventory/{operation}
POST /api/services/orders/{operation}
POST /api/services/payments/{operation}
```

- Body: `{ "params": [ ... ] }` (el campo `params` es opcional; si falta o es null, se usa lista vacía). Ejemplos:
  - `POST /api/services/inventory/checkStock` → `{"params": ["P-100"]}`
  - `POST /api/services/payments/processPayment` → `{"params": ["ORD-1", 99.9, "CARD"]}`
- Respuesta OK (200): `{ "status": "SUCCESS", "result": <any> }`
- Respuesta de error: `{ "status": "ERROR", "error": "<message>", "requestId": "<uuid>" }` con HTTP **400** si la causa es `IllegalArgumentException`, **404** si es `NoSuchElementException`, **409** si es `IllegalStateException`, **502** si es `PaymentFailedException`, **500** para el resto. Implementar con un `@RestControllerAdvice` (`GlobalExceptionHandler`).
- Idealmente un solo `ServiceController` con rutas `/{operation}` por servicio, o un método por servicio; evita duplicar lógica.

**Métricas:**

```
GET /api/metrics/summary
```
Devuelve un arreglo con **siempre 3 elementos** (aunque no haya llamadas aún; en ese caso ceros):
```json
[
  { "serviceId": "inventory", "totalCalls": 20, "successCount": 20, "errorCount": 0,
    "errorRate": 0.0, "successRate": 100.0, "avgResponseTimeMs": 112.5 },
  ...
]
```
`errorRate` y `successRate` son **porcentajes 0–100** con 2 decimales de redondeo. `avgResponseTimeMs` también con 2 decimales. Evita división por cero.

```
GET /api/metrics/logs?service=&status=&from=&to=&page=0&size=20
```
- Todos los parámetros opcionales. `service` ∈ `inventory|orders|payments`; `status` ∈ `SUCCESS|ERROR`; `from`/`to` son instantes ISO-8601 (`2025-01-15T10:00:00Z`) → usa `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)` con `Instant`. Cadenas vacías (`service=`) se tratan como "sin filtro".
- Paginación base 0; `size` por defecto 20, máximo 100.
- Orden: más recientes primero.
- Respuesta:
```json
{ "content": [ LogEntry, ... ], "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
```
- Un valor inválido de `status` debe devolver 400, no 500.

```
POST /api/metrics/simulate-load
```
- Genera **exactamente 50 llamadas aleatorias** pasando por los proxies (mezcla de servicios y operaciones válidas con parámetros válidos, incluyendo algunas que fallen de forma natural, p. ej. stock insuficiente). Captura todas las excepciones para que las 50 se completen.
- Respuesta: `{ "executed": 50, "success": <n>, "errors": <n> }`.
- Puede ejecutarse secuencialmente (≈ 5–10 s en el peor caso por la latencia simulada). Para que no tarde demasiado, usa latencia reducida o un `ExecutorService` con un pool pequeño y espera a que terminen; **la respuesta solo se devuelve cuando las 50 terminaron**.

**Opcional pero recomendado:** `GET /api/health` → `{ "status": "UP" }` (útil para Railway).

### 3.7 Tests backend (mínimo)

Con JUnit 5:
- `LoggingProxyTest`: éxito registra `SUCCESS` con `durationMs >= 0`, `requestId` UUID válido y `timestamp`; fallo registra `ERROR` con `errorMessage` y `stackTraceSummary`, y relanza la excepción.
- `PaymentServiceTest`: con tasa de fallo 1.0 siempre falla; con 0.0 nunca falla.
- `MetricsControllerTest` (`@SpringBootTest` + `MockMvc`): `summary` devuelve 3 elementos; `logs` respeta paginación y filtros; `simulate-load` devuelve `executed = 50`.

---

## 4. Frontend

### 4.1 Configuración

- Vite + React 18 + TypeScript (`strict: true`), `recharts` para la gráfica. Sin librerías de UI pesadas: CSS propio simple y limpio (diseño responsive, tema claro).
- `VITE_API_URL` define la URL base del backend (por defecto `http://localhost:8080`). Un único cliente en `src/api/client.ts` usa `import.meta.env.VITE_API_URL` (quita la `/` final si existe). Incluye `.env.example` con `VITE_API_URL=http://localhost:8080`.
- Scripts `package.json`: `dev`, `build` (`tsc -b && vite build` o `tsc && vite build`), `preview`.
- `vercel.json` simple (framework Vite, `outputDirectory: dist`).
- Fija versiones concretas y compatibles en `package.json` (no uses `latest`).

### 4.2 Funcionalidades (todas obligatorias)

1. **Dashboard principal con 3 tarjetas de servicio** (`inventory`, `orders`, `payments`), cada una mostrando: **total de llamadas**, **tasa de éxito (%)** y **tiempo promedio (ms)**. Datos de `GET /api/metrics/summary`.
2. **Indicador visual de servicio con problemas:** si `errorRate > 15`, la tarjeta se resalta en **rojo** (borde/fondo rojo + etiqueta "Unhealthy"/"High error rate"). Si no, estilo normal/verde.
3. **Gráfica de líneas** (Recharts `LineChart`) con las **últimas 20 llamadas por servicio** y su **tiempo de respuesta**. Una línea por servicio (3 líneas, colores distintos, leyenda y tooltip). Eje X = número de llamada (1..20, de la más antigua a la más reciente), eje Y = `durationMs`. Obtén los datos con `GET /api/metrics/logs?service=<id>&size=20` (una petición por servicio) e invierte el orden (vienen del más reciente al más antiguo). Si un servicio tiene menos de 20 llamadas, la línea simplemente es más corta.
4. **Tabla de logs en tiempo real con polling cada 3 segundos** (`setInterval` de 3000 ms con limpieza en el `useEffect`, sin fugas ni solapamiento de peticiones). Columnas: `requestId`, servicio, operación, duración (ms), estado (badge verde SUCCESS / rojo ERROR), y también timestamp. Incluye paginación (anterior / siguiente, indicador "Page X of Y"). El mismo polling refresca el summary y la gráfica.
5. **Filtros:** por servicio (All / inventory / orders / payments), por estado (All / SUCCESS / ERROR) y por **rango de fechas** (`from` y `to` con `<input type="datetime-local">`, convertidos a ISO con `new Date(value).toISOString()`). Botón "Clear filters". Al cambiar un filtro se vuelve a la página 0 y se consulta de inmediato (no esperar al siguiente tick).
6. **Botón "Simular carga"** (texto visible: `Simulate load`): llama a `POST /api/metrics/simulate-load`, muestra estado de carga (botón deshabilitado + spinner/texto "Running…") y al terminar **actualiza el dashboard inmediatamente** (summary, gráfica y tabla) y muestra un mensaje con el resultado (`50 calls executed: X success, Y errors`).
7. **Detalle expandible al hacer clic en un log:** la fila se expande y muestra los **parámetros de entrada** y la **respuesta completa** (JSON con `JSON.stringify(value, null, 2)` en un `<pre>`), además de `errorMessage` y `stackTraceSummary` si el log es ERROR. Un segundo clic la contrae.

### 4.3 Calidad del frontend

- Maneja estados de **loading**, **error de red** (banner "Cannot reach the backend at <url>") y **lista vacía** ("No logs yet. Click 'Simulate load'.").
- No uses `any`; define los tipos en `src/types.ts` (`LogEntry`, `ServiceSummary`, `PageResponse<T>`, `CallStatus`).
- Usa `key` estables (`requestId`) en las listas.
- Si una petición en vuelo se queda obsoleta por un cambio de filtros, ignora su resultado (usa `AbortController` o un contador de petición).
- El título de la página y el encabezado: `Microservices Monitoring Dashboard`.

---

## 5. Despliegue (referencia; el usuario lo ejecuta)

Prepara todo para que el usuario solo tenga que subir los archivos.

**Backend → Railway** (Docker):

`backend/Dockerfile` multi-stage:
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```
Variables de entorno en Railway: `CORS_ALLOWED_ORIGINS=https://<frontend>.vercel.app` (`PORT` lo inyecta Railway; el backend ya lo lee).

**Frontend → Vercel:** Root Directory `frontend`, Framework Preset `Vite`, variable `VITE_API_URL=https://<backend>.up.railway.app`.

El `README.md` debe explicar estos pasos en inglés, más cómo correr todo en local.

---

## 6. Orden de trabajo recomendado

1. Crear la estructura y el `pom.xml`; verificar que `mvn -q clean compile` pasa con una app vacía.
2. Modelos, `LogStore`, interfaz `MicroserviceProxy`, `LoggingProxy`.
3. Los 3 servicios + excepciones.
4. Configuración (beans, CORS), controladores y `GlobalExceptionHandler`.
5. Tests backend; ejecutar `mvn -q clean verify`.
6. Levantar el backend y probar con `curl` (sección 7).
7. Crear el frontend; `npm install` y `npm run build`.
8. Probar frontend contra backend en local.
9. README, `.gitignore`, Dockerfile, `vercel.json`.
10. Verificación final y empaquetado (sección 9).

---

## 7. Pruebas manuales obligatorias (smoke test)

Con el backend corriendo en local, ejecuta y comprueba las respuestas:

```bash
curl -s -X POST localhost:8080/api/services/inventory/checkStock -H 'Content-Type: application/json' -d '{"params":["P-100"]}'
curl -s -X POST localhost:8080/api/services/orders/createOrder -H 'Content-Type: application/json' -d '{"params":["C-1","P-100",2]}'
curl -s -X POST localhost:8080/api/services/payments/processPayment -H 'Content-Type: application/json' -d '{"params":["ORD-1",99.9,"CARD"]}'
curl -s -X POST localhost:8080/api/services/inventory/unknownOp -H 'Content-Type: application/json' -d '{}'   # must return 400 and be logged as ERROR
curl -s -X POST localhost:8080/api/metrics/simulate-load                                                     # executed = 50
curl -s localhost:8080/api/metrics/summary
curl -s "localhost:8080/api/metrics/logs?service=payments&status=ERROR&page=0&size=5"
curl -s "localhost:8080/api/metrics/logs?service=&status=&from=2020-01-01T00:00:00Z&to=2100-01-01T00:00:00Z"
```

Después de varias ejecuciones de `simulate-load`, verifica que `payments` muestra una tasa de error cercana al 10 % (más los errores naturales de otras operaciones) y que los logs contienen `requestId` único, `durationMs`, `status` y `timestamp`.

---

## 8. Definición de terminado (checklist)

Marca cada punto **solo después de comprobarlo ejecutando el comando**:

- [ ] `cd backend && mvn -q clean verify` termina con **BUILD SUCCESS** (compilación + tests).
- [ ] `docker build` del backend no es obligatorio si no hay Docker, pero el `Dockerfile` debe ser coherente con el `pom.xml` (`target/app.jar`).
- [ ] El backend arranca con `java -jar target/app.jar` sin excepciones y responde a la smoke test de la sección 7.
- [ ] `cd frontend && npm install && npm run build` termina **sin errores de TypeScript ni de Vite**.
- [ ] El frontend en modo `dev` contra el backend local muestra: 3 tarjetas, gráfica, tabla que se refresca cada 3 s, filtros funcionales, botón `Simulate load` y detalle expandible.
- [ ] Una tarjeta se pone roja cuando su `errorRate > 15` (verifícalo forzando fallos, p. ej. varias simulaciones o bajando temporalmente el umbral en una prueba, y vuelve a dejarlo en 15).
- [ ] No quedan `TODO`, `FIXME`, código muerto, `console.log` de depuración ni texto en español dentro del código.
- [ ] No hay secretos ni URLs de producción hardcodeadas.
- [ ] `README.md`, `.gitignore`, `Dockerfile`, `vercel.json` y `.env.example` existen.

Si algo falla, **corrige y vuelve a ejecutar** hasta que todo pase. No entregues con errores pendientes ni "para que el usuario lo arregle".

---

## 9. Entrega final

Cuando todo lo anterior esté verificado:

1. Limpia artefactos: elimina `backend/target`, `frontend/node_modules`, `frontend/dist` y cualquier archivo temporal o de logs.
2. Empaqueta el proyecto en **un único ZIP** `monitoring-system.zip` que contenga `backend/`, `frontend/`, `README.md`, `.gitignore` y `AGENTS.md`.
3. Entrega al usuario el ZIP **y** (si el entorno lo permite) las carpetas `backend/` y `frontend/` por separado para que pueda subirlas directamente a Railway y Vercel.
4. Termina con un mensaje corto en español que incluya:
   - Qué se construyó y cómo correrlo en local (3–4 comandos).
   - Las variables de entorno que debe configurar en Railway y Vercel.
   - Cualquier decisión o suposición que hayas tomado.
   - Confirmación explícita de que ejecutaste `mvn clean verify` y `npm run build` con éxito.
