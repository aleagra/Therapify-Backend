# Therapify Backend

API REST para la plataforma Therapify. Gestiona la persistencia de usuarios, perfiles de psicólogos, autenticación mediante JWT con verificación por correo, agenda y disponibilidad de turnos, y almacenamiento de reseñas.

## Despliegue

- **API en producción:** [https://therapify-backend.onrender.com](https://therapify-backend.onrender.com)
- **Frontend asociado:** [https://therapifyy.vercel.app](https://therapifyy.vercel.app) ([Repositorio Frontend](https://github.com/aleagra/Therapify))

## Funcionalidades

- **Autenticación y autorización:** Registro de pacientes y profesionales, login con generación de tokens JWT (HMAC256), verificación de cuenta por correo electrónico (SMTP) y recuperación de contraseña.
- **Gestión de turnos (Appointments):** Creación, cancelación y consulta de turnos, validando solapamiento de horarios y disponibilidad del profesional.
- **Directorio y perfiles:** Operaciones CRUD para perfiles de psicólogos (especialidades, tarifas, disponibilidad horaria) y pacientes.
- **Sistema de reseñas:** Registro de puntuaciones y comentarios vinculados a profesionales.
- **Caché en memoria:** Integración de Caffeine Cache para optimizar consultas frecuentes.
- **Cuentas demo:** Inicialización y reseteo de perfiles de prueba preconfigurados.

## Stack Tecnológico

- **Lenguaje:** Java 21
- **Framework:** Spring Boot 4
- **Seguridad:** Spring Security y Auth0 Java JWT
- **Persistencia:** Spring Data JPA y Hibernate
- **Base de datos:** PostgreSQL 16 (producción y local) / H2 (testing)
- **Mailing:** Spring Mail (JavaMailSender vía SMTP)
- **Caché:** Spring Cache con Caffeine
- **Contenedores:** Docker y Docker Compose
- **Construcción:** Apache Maven

## Instalación y Configuración

### Prerrequisitos

- Java 21 instalado (o Docker)
- PostgreSQL local o contenedor Docker

### 1. Clonar el repositorio

```bash
git clone https://github.com/aleagra/Therapify-Backend.git
cd Therapify-Backend
```

### 2. Configurar variables de entorno

Crear un archivo `.env` en la raíz del proyecto tomando como referencia `.env.example`:

```env
# PostgreSQL
POSTGRES_DB=therapify
POSTGRES_USER=postgres
POSTGRES_PASSWORD=tu_password

# Datasource Spring
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/therapify
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=tu_password
SPRING_JPA_HIBERNATE_DDL_AUTO=update

# Configuración SMTP (Gmail)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=tu_correo@gmail.com
SPRING_MAIL_PASSWORD=tu_app_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true

# Cuentas demo
THERAPIFY_DEMO_DOCTOR_EMAIL=demo.terapeuta@therapify.com
THERAPIFY_DEMO_PATIENT_EMAIL=demo.paciente@therapify.com
```

### 3. Ejecución

#### Opción A: Con Docker Compose (levanta base de datos y aplicación)

```bash
docker compose up --build
```

#### Opción B: Ejecución local con Maven (requiere base de datos activa en puerto 5432)

En Linux/macOS:
```bash
./mvnw spring-boot:run
```

En Windows:
```cmd
mvnw.cmd spring-boot:run
```

La API quedará disponible en `http://localhost:8080`.

## Endpoints Principales

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/auth/register` | Registro de nuevo usuario (paciente o psicólogo) |
| `POST` | `/auth/login` | Autenticación y retorno de token JWT |
| `GET` | `/auth/verify-email` | Verificación de token recibido por correo |
| `POST` | `/auth/forgot-password` | Envío de correo para restablecimiento de clave |
| `GET` | `/users/doctors` | Listado y filtrado de profesionales |
| `GET` | `/users/{id}` | Datos de perfil de un usuario |
| `POST` | `/appointments` | Reserva de un nuevo turno |
| `GET` | `/appointments/doctor/{id}` | Agenda de turnos de un profesional |
| `DELETE` | `/appointments/{id}` | Cancelación de un turno agendado |
| `POST` | `/reviews` | Publicación de valoración a un profesional |
| `GET` | `/health` | Chequeo de estado del servicio |

## Estructura de Carpetas

```text
├── src/
│   ├── main/
│   │   ├── java/com/example/therapify/
│   │   │   ├── config/        # Configuración de seguridad, CORS, caché y JWT
│   │   │   ├── controller/    # Endpoints REST (Auth, Appointments, Users, etc.)
│   │   │   ├── dtos/          # Data Transfer Objects para request y response
│   │   │   ├── enums/         # Enumeradores (roles, estados de turnos)
│   │   │   ├── exception/     # Manejo global de excepciones y respuestas HTTP
│   │   │   ├── model/         # Entidades JPA (User, Doctor, Appointment, Review)
│   │   │   ├── repository/    # Interfaces Spring Data JPA
│   │   │   ├── service/       # Lógica de negocio y servicios auxiliares
│   │   │   └── util/          # Utilidades generales y generadores de token
│   │   └── resources/
│   │       └── application.properties # Parámetros de configuración de la app
│   └── test/                  # Tests unitarios y de integración con H2
├── docker-compose.yml         # Orquestación de contenedor app y postgres
├── Dockerfile                 # Imagen multi-stage de la aplicación
├── pom.xml                    # Definición de dependencias Maven
└── mvnw / mvnw.cmd            # Maven Wrapper
```

## Licencia

Este proyecto está bajo la Licencia MIT.
