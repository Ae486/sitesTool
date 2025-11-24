import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User } from "../types/user";

interface AuthState {
  token: string | null;
  user: User | null;
  setAuth: (token: string, user: User) => void;
  logout: () => void;
}

const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      logout: () => set({ token: null, user: null }),
    }),
    { name: "nav-checkin-auth" },
  ),
);

export default useAuthStore;
