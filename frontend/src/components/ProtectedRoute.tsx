import { Navigate, Outlet, useLocation } from "react-router-dom";
import useAuthStore from "../store/auth";

const ProtectedRoute = () => {
  const location = useLocation();
  const isAuthenticated = useAuthStore((state) => Boolean(state.token));

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
