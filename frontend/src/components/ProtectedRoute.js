import { jsx as _jsx } from "react/jsx-runtime";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import useAuthStore from "../store/auth";
const ProtectedRoute = () => {
    const location = useLocation();
    const isAuthenticated = useAuthStore((state) => Boolean(state.token));
    if (!isAuthenticated) {
        return _jsx(Navigate, { to: "/login", replace: true, state: { from: location } });
    }
    return _jsx(Outlet, {});
};
export default ProtectedRoute;
