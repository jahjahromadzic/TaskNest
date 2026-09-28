import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { TitleStrategy, provideRouter, withViewTransitions } from '@angular/router';
import { routes } from './app.routes';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';
import { ThemeService } from './theme/theme.service';
import { I18nService } from './i18n/i18n.service';
import { TranslatedTitleStrategy } from './i18n/title-strategy';
import { RealtimeService } from './services/realtime.service';
import { skipWhenOnlyQueryChanges } from './view-transitions';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withViewTransitions({ onViewTransitionCreated: skipWhenOnlyQueryChanges })),
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideAppInitializer(() => inject(I18nService).start()),
    provideAppInitializer(() => inject(ThemeService).start()),
    { provide: TitleStrategy, useExisting: TranslatedTitleStrategy },
    provideAppInitializer(() => inject(AuthService).restoreSession()),
    provideAppInitializer(() => inject(RealtimeService).start()),
  ],
};
