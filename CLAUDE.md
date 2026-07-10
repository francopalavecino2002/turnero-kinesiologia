# Turnero Kinesiología

## Descripción del proyecto

Sistema de gestión de turnos para un consultorio de kinesiología. Permite a
pacientes reservar turnos con profesionales, a los profesionales gestionar su
agenda, y a los administradores tener visibilidad y control general del
consultorio.

## Stack tecnológico

- **Backend**: Spring Boot (Java)
- **Frontend**: Angular
- **Base de datos**: PostgreSQL
- **Migraciones**: Flyway
- **Estructura**: monorepo (`backend/`, `frontend/`, `docs/`)

## Convenciones

- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, etc.)
- **Ramas**: `feature/<nombre>` para funcionalidades nuevas, `fix/<nombre>`
  para correcciones.
- **Idioma del código**: inglés (nombres de clases, variables, métodos,
  endpoints, etc.)
- **Idioma de commits y documentación**: español.

## Reglas de dominio

- **Profesionales**: actualmente 3, con expectativa de crecer a más.
- **Boxes físicos**: actualmente 2, con expectativa de crecer a 3.
- **Roles del sistema**: `paciente`, `profesional`, `administrador`.
- **Obra social**: el consultorio no trabaja con obras sociales (todos los
  turnos son particulares).
- **Notificaciones**: se envían por WhatsApp y email tanto a pacientes como a
  profesionales (ej. confirmación, recordatorio o cancelación de turnos).

## Modelo de datos

_Pendiente de definir._

## Endpoints

_Pendiente de definir._

## Decisiones de diseño — gestión de datos

- Servicios, profesionales y disponibilidades son entidades administrables
  vía interfaz (CRUD), NO datos hardcodeados en el código.
- Modelo de permisos (auto-gestión con supervisión):
  - Cada profesional se registra y gestiona lo suyo: qué servicios ofrece
    y sus horarios de disponibilidad.
  - El administrador (dueña del consultorio) puede editar los datos de
    cualquier profesional, darlo de alta/baja, y gestionar servicios.
  - Regla: un profesional edita sus propios datos; el admin edita los de
    cualquiera (ownership + rol de administración).
- Reglas de negocio del dominio:
  - Duración del turno según el servicio (60 min general, 30 min EMSELLA).
  - Máximo 2 turnos solapados en el mismo horario (capacidad = 2 boxes,
    configurable a futuro).
  - El turno debe caer dentro de la disponibilidad del profesional.
  - Un profesional solo atiende servicios que ofrece.