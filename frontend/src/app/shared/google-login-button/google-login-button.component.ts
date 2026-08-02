import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-google-login-button',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './google-login-button.component.html',
  styleUrl: './google-login-button.component.scss',
})
export class GoogleLoginButtonComponent {
  private readonly auth = inject(AuthService);

  protected readonly authorizationUrl = this.auth.getOAuth2AuthorizationUrl();
}
