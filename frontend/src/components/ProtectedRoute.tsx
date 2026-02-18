import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { fetchAuthStatus } from "../api/auth";
import useAuthStore from "../store/auth";

const ProtectedRoute = () => {
  const location = useLocation();
  const { token, setAuth } = useAuthStore();
  const [checking, setChecking] = useState(!token);

  useEffect(() => {
    if (token) return;
    fetchAuthStatus()
      .then((status) => {
        if (status.auth_disabled && status.dev_user) {
          setAuth("dev-mode", status.dev_user);
        }
      })
      .catch(() => {})
      .finally(() => setChecking(false));
  }, [token, setAuth]);

  if (checking) return null;

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
