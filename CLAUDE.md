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
