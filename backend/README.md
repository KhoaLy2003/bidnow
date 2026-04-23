# BidNow - Backend Microservices

This directory contains the backend source code for the BidNow auction system, built using a microservices architecture with Spring Boot 3 and Spring Cloud.

## System Architecture

The BidNow backend is designed as a distributed system to ensure scalability, fault tolerance, and high performance (especially for the bidding engine).

### Core Components
1.  **Discovery Service (Eureka Server)**: Acts as a service registry where all microservices register themselves, allowing for dynamic service-to-service communication.
2.  **API Gateway**: The single entry point for all client requests. It handles routing to appropriate microservices using Spring Cloud Gateway and provides initial security/rate-limiting pathing.
3.  **Identity Service**: Manages user authentication, registration, and JWT issuance.
4.  **User Service**: Manages user profiles, account metadata, and account status.
5.  **Auction Service**: Handles the lifecycle of auction listings (creation, updates, and closure).
6.  **Bidding Service**: High-performance engine for bid placement and auto-bidding logic.
7.  **Wallet & Payment Service**: Manages user funds, escrow for bids, and transaction history.
8.  **Notification Service**: Handles system alerts and real-time push notifications.
9.  **Common Library**: A shared module containing standardized DTOs, exception handlers, and base JPA entities used across all services.

### Port Mapping
| Service | Port | Description |
| :--- | :--- | :--- |
| `discovery-service` | 8761 | Eureka Dashboard |
| `api-gateway` | 8080 | Entry point for APIs |
| `identity-service` | 8081 | Auth & Identity |
| `user-service` | 8082 | Profile management |
| `auction-service` | 8083 | Auction listings |
| `bidding-service` | 8084 | Bidding engine |
| `wallet-service` | 8085 | Financial ledger |
| `notification-service` | 8086 | User notifications |

---

## Getting Started

### Prerequisites
- **Java 17** or higher
- **Maven 3.8+**
- **PostgreSQL 15+** (Running locally or via Docker)

### Build Instructions
1.  Navigate to the `backend/` directory:
    ```bash
    cd backend
    ```
2.  Build all modules and install the common library:
    ```bash
    mvn clean install
    ```

### Running the Services
Services must be started in a specific order for proper registration:

1.  **Start Discovery Service**:
    ```bash
    mvn spring-boot:run -pl discovery-service
    ```
2.  **Start API Gateway**:
    ```bash
    mvn spring-boot:run -pl api-gateway
    ```
3.  **Start Other Services** (e.g., Auction Service):
    ```bash
    mvn spring-boot:run -pl auction-service
    ```

### Health Check
- Eureka Dashboard: [http://localhost:8761](http://localhost:8761)
- Individual Service Health: `http://localhost:<port>/actuator/health`

## Health Monitoring

All microservices are equipped with Spring Boot Actuator for health monitoring. You can verify if a service is running and healthy by calling its health endpoint.

### Health Endpoint Table

| Service | Health URL | Expected Status |
| :--- | :--- | :--- |
| `discovery-service` | [http://localhost:8761/actuator/health](http://localhost:8761/actuator/health) | `{"status": "UP"}` |
| `api-gateway` | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | `{"status": "UP"}` |
| `identity-service` | [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health) | `{"status": "UP"}` |
| `user-service` | [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health) | `{"status": "UP"}` |
| `auction-service` | [http://localhost:8083/actuator/health](http://localhost:8083/actuator/health) | `{"status": "UP"}` |
| `bidding-service` | [http://localhost:8084/actuator/health](http://localhost:8084/actuator/health) | `{"status": "UP"}` |
| `wallet-service` | [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health) | `{"status": "UP"}` |
| `notification-service` | [http://localhost:8086/actuator/health](http://localhost:8086/actuator/health) | `{"status": "UP"}` |

### Verification via Command Line
You can use `curl` to quickly check the status of a service:
```bash
curl http://localhost:8080/actuator/health
```
If the service is running correctly, it should return a JSON response with status `"UP"`.
