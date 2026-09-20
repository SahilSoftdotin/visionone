import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from 'react-oidc-context';
import { oidcConfig } from '@/lib/auth';
import { AuthBootstrap, LoginRoute, RequireAuth } from './AuthGate';
import { ScrollToTop } from './ScrollToTop';
import { AppShell } from './AppShell';
import { OrganizationRouter } from './OrganizationRouter';
import { OverviewScreen } from '@/features/overview/OverviewScreen';
import { GrowthScreen } from '@/features/growth/GrowthScreen';
import { LeadsScreen } from '@/features/leads/LeadsScreen';
import { FrontDeskScreen } from '@/features/frontdesk/FrontDeskScreen';
import { CalendarScreen } from '@/features/calendar/CalendarScreen';
import { WorkContentScreen } from '@/features/work/WorkContentScreen';
import { ReportsScreen } from '@/features/reports/ReportsScreen';

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
          <ScrollToTop />
          <AuthBootstrap>
            <Routes>
              {/* Public. Keycloak returns to "/", so the callback never lands here. */}
              <Route path="/login" element={<LoginRoute />} />

              <Route element={<RequireAuth />}>
                <Route path="/" element={<OrganizationRouter />} />
                <Route path="/orgs/:orgId" element={<AppShell />}>
                  <Route index element={<Navigate to="overview" replace />} />
                  <Route path="overview" element={<OverviewScreen />} />
                  <Route path="growth" element={<GrowthScreen />} />
                  <Route path="leads" element={<LeadsScreen />} />
                  <Route path="front-desk" element={<FrontDeskScreen />} />
                  <Route path="calendar" element={<CalendarScreen />} />
                  <Route path="work" element={<WorkContentScreen />} />
                  <Route path="reports" element={<ReportsScreen />} />
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
