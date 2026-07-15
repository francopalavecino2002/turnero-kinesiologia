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

export interface Professional {
  id: number;
  firstName: string;
  lastName: string;
  services: Service[];
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
}

export interface JwtPayload {
  sub: string;
  role: Role;
  exp: number;
  iat: number;
}
