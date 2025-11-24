import { Navigate, Route, Routes } from "react-router-dom";
import AppLayout from "./components/AppLayout";
import ProtectedRoute from "./components/ProtectedRoute";
import DashboardPage from "./pages/Dashboard";
import FlowsPage from "./pages/Flows";
import HistoryPage from "./pages/History";
import LoginPage from "./pages/Login";
import SitesPage from "./pages/Sites";

const App = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<ProtectedRoute />}>
      <Route element={<AppLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="sites" element={<SitesPage />} />
        <Route path="flows" element={<FlowsPage />} />
        <Route path="history" element={<HistoryPage />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
);

export default App;
