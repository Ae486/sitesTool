import { lazy, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/AppLayout";
import ErrorBoundary from "./components/ErrorBoundary";
import ProtectedRoute from "./components/ProtectedRoute";
import DashboardPage from "./pages/Dashboard";
import FlowsPage from "./pages/Flows";
import HistoryPage from "./pages/History";
import LoginPage from "./pages/Login";
import SitesPage from "./pages/Sites";

const ProxiesPage = lazy(() => import("./pages/proxies"));
const TunnelProxiesPage = lazy(() => import("./pages/TunnelProxies"));

const App = () => (
  <ErrorBoundary>
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="sites" element={<SitesPage />} />
          <Route path="flows" element={<FlowsPage />} />
          <Route path="history" element={<HistoryPage />} />
          <Route
            path="proxies"
            element={
              <Suspense>
                <ProxiesPage />
              </Suspense>
            }
          />
          <Route
            path="proxies/tunnel"
            element={
              <Suspense>
                <TunnelProxiesPage />
              </Suspense>
            }
          />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  </ErrorBoundary>
);

export default App;
