import { Routes } from '@angular/router';
import { authGuard, guestGuard, roleGuard } from './core/guards/auth.guard';

/**
 * Feature screens are lazily loaded so the login bundle stays small and each
 * module is fetched only when its route is first visited.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'signup',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/signup.component').then(m => m.SignupComponent)
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./features/auth/verify-email.component').then(m => m.VerifyEmailComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./features/auth/forgot-password.component').then(m => m.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./features/auth/reset-password.component').then(m => m.ResetPasswordComponent)
  },

  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'mes-projets',
        loadComponent: () => import('./features/products/my-projects.component').then(m => m.MyProjectsComponent)
      },
      {
        path: 'production',
        loadComponent: () => import('./features/products/product-board.component').then(m => m.ProductBoardComponent),
        data: { projectType: 'PRODUCTION' }
      },
      {
        path: 'npi',
        loadComponent: () => import('./features/products/product-board.component').then(m => m.ProductBoardComponent),
        data: { projectType: 'NPI' }
      },
      {
        path: 'products/:id',
        loadComponent: () => import('./features/products/product-detail.component').then(m => m.ProductDetailComponent)
      },
      {
        path: 'taches',
        loadComponent: () => import('./features/tasks/tasks.component').then(m => m.TasksComponent)
      },
      {
        path: 'blocages',
        loadComponent: () => import('./features/issues/issues.component').then(m => m.IssuesComponent)
      },
      {
        path: 'issues/:id',
        loadComponent: () => import('./features/issues/issues.component').then(m => m.IssuesComponent)
      },
      {
        path: 'modifications',
        loadComponent: () => import('./features/changes/changes.component').then(m => m.ChangesComponent)
      },
      {
        path: 'changes/:id',
        loadComponent: () => import('./features/changes/changes.component').then(m => m.ChangesComponent)
      },
      {
        path: 'documents',
        loadComponent: () => import('./features/documents/documents.component').then(m => m.DocumentsComponent)
      },
      {
        path: 'control',
        loadComponent: () => import('./features/control/control.component').then(m => m.ControlComponent)
      },
      {
        path: 'notifications',
        loadComponent: () => import('./features/notifications/notifications.component').then(m => m.NotificationsComponent)
      },
      {
        path: 'profil',
        loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent)
      },
      {
        path: 'admin',
        canActivate: [roleGuard],
        data: { roles: ['ADMIN'] },
        loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent)
      }
    ]
  },

  { path: '**', redirectTo: 'dashboard' }
];
