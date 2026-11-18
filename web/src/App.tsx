import { lazy } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import Layout from "./components/Layout";
import Logo from "./components/Logo";
import { useAuth } from "./lib/auth";
import SignInPage from "./pages/SignInPage";

// Each page loads when it's first opened, so the first screen doesn't wait for every chart.
const TrainingPage = lazy(() => import("./pages/TrainingPage"));
const JournalPage = lazy(() => import("./pages/JournalPage"));
const StrengthPage = lazy(() => import("./pages/StrengthPage"));
const EntryPage = lazy(() => import("./pages/EntryPage"));
const SettingsPage = lazy(() => import("./pages/SettingsPage"));
const FoodPage = lazy(() => import("./pages/FoodPage"));
const FoodProgressPage = lazy(() => import("./pages/FoodProgressPage"));
const FoodGoalPage = lazy(() => import("./pages/FoodGoalPage"));
const TellPage = lazy(() => import("./pages/TellPage"));

export default function App() {
  const { user, unreachable, retry } = useAuth();
  const location = useLocation();

  if (user === undefined && unreachable) {
    return (
      <div className="auth">
        <div className="auth-card">
          <div className="wordmark auth-mark"><Logo size={28} />Training Journal</div>
          <h1>Can't reach the journal</h1>
          <p className="auth-lede">
            The computer running it may be asleep or offline. Check that it's on, then try again.
          </p>
          <button type="button" className="btn primary block" onClick={retry}>Try again</button>
        </div>
      </div>
    );
  }

  if (user === undefined) {
    return (
      <div className="splash" aria-busy="true">
        <Logo size={40} />
      </div>
    );
  }

  if (user === null) {
    // Come back to the page you were on after signing in.
    const back = location.pathname.startsWith("/login") || location.pathname.startsWith("/signup")
      ? undefined : location.pathname + location.search;
    return (
      <Routes>
        <Route path="/login" element={<SignInPage mode="login" />} />
        <Route path="/signup" element={<SignInPage mode="signup" />} />
        <Route path="*" element={<Navigate to="/login" replace state={{ back }} />} />
      </Routes>
    );
  }

  const back = (location.state as { back?: string } | null)?.back;
  return (
    <Routes>
      <Route path="/login" element={<Navigate to={back ?? "/training"} replace />} />
      <Route path="/signup" element={<Navigate to="/training" replace />} />
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/training" replace />} />
        <Route path="/training" element={<TrainingPage />} />
        <Route path="/journal" element={<JournalPage />} />
        <Route path="/strength" element={<StrengthPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/food" element={<FoodPage />} />
        <Route path="/food/progress" element={<FoodProgressPage />} />
        <Route path="/food/goal" element={<FoodGoalPage />} />
        <Route path="/tell" element={<TellPage />} />
        <Route path="/entries/new" element={user.demo ? <Navigate to="/journal" replace /> : <EntryPage />} />
        <Route path="/entries/:id" element={<EntryPage />} />
        <Route path="*" element={<p className="state">There's no page here. <a href="/training">Go to Training</a>.</p>} />
      </Route>
    </Routes>
  );
}
