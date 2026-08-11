export type Role = 'PATIENT' | 'PROFESSIONAL' | 'ADMIN';

export type AppointmentStatus =
  | 'BOOKED'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'NO_SHOW';

export interface AuthResponse {
  token: string;
  id: number;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
  mustChangePassword: boolean;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone: string;
  // Opt-out reminder preference. The backend currently forces `true` and ignores
  // this field (unknown properties are dropped); wiring it through server-side is
  // a follow-up so unchecking actually persists.
  notificationsEnabled?: boolean;
}

export interface RegisterResponse {
  id: number;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
  phone: string;
  notificationsEnabled: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserInfoResponse {
  id: number;
  email: string;
  role: Role;
  firstName: string;
  lastName: string;
  mustChangePassword: boolean;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface AppointmentResponse {
  id: number;
  dateTime: string;
  status: AppointmentStatus;
  serviceName: string;
  serviceDurationMinutes: number;
  professionalFirstName: string;
  professionalLastName: string;
  patientFirstName: string;
  patientLastName: string;
}

export interface CreateAppointmentRequest {
  professionalId: number;
  serviceId: number;
  dateTime: string;
}

export interface GuestPatientRequest {
  name: string;
  phone: string;
  email?: string;
}

// Manual booking by staff (PROFESSIONAL/ADMIN) on behalf of a patient. Exactly one of patientId
// or guestPatient must be set - never both, never neither.
export interface StaffBookAppointmentRequest {
  professionalId: number;
  serviceId: number;
  dateTime: string;
  patientId?: number;
  guestPatient?: GuestPatientRequest;
}

export interface PatientSearchResult {
  id: number;
  fullName: string;
  email: string | null;
  phone: string;
  registered: boolean;
}

export interface AvailableSlot {
  startTime: string;
  endTime: string;
}

export interface Service {
  id: number;
  name: string;
  durationMinutes: number;
  active: boolean;
}

// Bare service info as returned nested inside ProfessionalResponse (no `active` flag - the
// backend only nests services this way for a professional's own offered-services list).
export interface ServiceSummary {
  id: number;
  name: string;
  durationMinutes: number;
}

export interface Professional {
  id: number;
  firstName: string;
  lastName: string;
  services: ServiceSummary[];
}

export interface Patient {
  id: number;
  firstName: string;
  lastName: string;
  phone: string;
  notificationsEnabled: boolean;
}

export interface ErrorResponse {
  status: number;
  message: string;
  timestamp: string;
  // Machine-readable discriminator for messages the UI must react to (e.g. the
  // EMAIL_NOT_VERIFIED login rejection, so the user can request a new link).
  code?: string;
}

export interface MessageResponse {
  message: string;
}

export interface EmailRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface AgendaEntry {
  id: number | null;
  dateTime: string;
  endTime: string;
  serviceName: string;
  professionalId: number;
  professionalFirstName: string;
  professionalLastName: string;
  status: AppointmentStatus;
  patientFirstName: string | null;
  patientLastName: string | null;
  ownedByCurrentUser: boolean;
}

export interface MonthDaySummary {
  day: number;
  count: number;
}

export interface MonthSummaryResponse {
  days: MonthDaySummary[];
}

export type ServiceAdmin = Service;

export interface ServiceCreateRequest {
  name: string;
  durationMinutes: number;
}

export interface ServiceUpdateRequest {
  name: string;
  durationMinutes: number;
}

export interface ProfessionalAdmin {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  active: boolean;
  services: ServiceAdmin[];
}

export interface ProfessionalCreatedResponse extends ProfessionalAdmin {
  temporaryPassword: string;
}

export interface ProfessionalCreateRequest {
  firstName: string;
  lastName: string;
  email: string;
  serviceIds: number[];
}

export interface ProfessionalUpdateRequest {
  firstName: string;
  lastName: string;
  serviceIds: number[];
}

export interface JwtPayload {
  sub: string;
  role: Role;
  exp: number;
  iat: number;
}

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface Availability {
  id: number;
  professionalId: number;
  professionalName: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  serviceId: number | null;
  serviceName: string | null;
}

export interface CreateAvailabilityRequest {
  professionalId: number;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  serviceId: number | null;
}

export interface UpdateAvailabilityRequest {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  serviceId: number | null;
}

export interface RecurringBlockAdmin {
  id: number;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  description: string;
  active: boolean;
  serviceId: number | null;
  serviceName: string | null;
  professionalId: number | null;
  professionalName: string | null;
  affectedAppointmentsCount: number;
}

export interface RecurringBlockCreateRequest {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  description: string;
  serviceId: number | null;
  professionalId: number | null;
}

export interface RecurringBlockUpdateRequest {
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  description: string;
  serviceId: number | null;
  professionalId: number | null;
}
