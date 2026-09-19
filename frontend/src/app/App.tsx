import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from 'react-oidc-context';
import { oidcConfig } from '@/lib/auth';
import { AuthBootstrap, LoginRoute, RequireAuth } from './AuthGate';
import { AppShell } from './AppShell';
import { ComingSoon } from './ComingSoon';
import { OrganizationRouter } from './OrganizationRouter';
import { OverviewScreen } from '@/features/overview/OverviewScreen';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export function App() {
  return (
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <AuthBootstrap>
            <Routes>
              {/* Public. Keycloak returns to "/", so the callback never lands here. */}
              <Route path="/login" element={<LoginRoute />} />

              <Route element={<RequireAuth />}>
                <Route path="/" element={<OrganizationRouter />} />
                <Route path="/orgs/:orgId" element={<AppShell />}>
                  <Route index element={<Navigate to="overview" replace />} />
                  <Route path="overview" element={<OverviewScreen />} />
                  <Route path="growth" element={<ComingSoon />} />
                  <Route path="leads" element={<ComingSoon />} />
                  <Route path="front-desk" element={<ComingSoon />} />
                  <Route path="work" element={<ComingSoon />} />
                  <Route path="reports" element={<ComingSoon />} />
                </Route>
              </Route>

              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthBootstrap>
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>
  );
}
