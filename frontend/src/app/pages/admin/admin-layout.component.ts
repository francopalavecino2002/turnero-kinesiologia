import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

interface AdminNavItem {
  label: string;
  route: string;
  icon: string;
}

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  // Shared by the desktop sidebar and the mobile tab strip so the two never drift apart.
  readonly navItems: AdminNavItem[] = [
    { label: 'Servicios', route: '/admin/services', icon: 'medical_services' },
    { label: 'Profesionales', route: '/admin/professionals', icon: 'people' },
    { label: 'Disponibilidades', route: '/admin/availability', icon: 'schedule' },
    { label: 'Bloques recurrentes', route: '/admin/recurring-blocks', icon: 'date_range' },
  ];
}
