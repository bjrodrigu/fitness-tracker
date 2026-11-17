import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import Logo from "../components/Logo";
import { useAuth } from "../lib/auth";

export default function SignInPage({ mode }: { mode: "login" | "signup" }) {
  const { login, signup, demo, config } = useAuth();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const isSignup = mode === "signup";

  const run = async (f: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await f();
    } catch (e) {
      setError((e as Error).message);
      setBusy(false);
    }
  };

  const submit = (e: FormEvent) => {
    e.preventDefault();
    run(() => (isSignup ? signup(name, email, password) : login(email, password)));
  };

  return (
    <div className="auth">
      <div className="auth-card">
        <div className="wordmark auth-mark">
          <Logo size={28} />
          Training Journal
        </div>
        <h1>{isSignup ? "Create an account" : "Log in"}</h1>
        <p className="auth-lede">
          {isSignup
            ? "Keep your runs and lifts in one place, with notes. Link Garmin later to bring in your runs."
            : "Your runs, lifts and notes."}
        </p>

        <form onSubmit={submit} className="auth-form" noValidate={false}>
          {isSignup && (
            <label>
              <span>Name</span>
              <input className="field" value={name} onChange={(e) => setName(e.target.value)} autoComplete="name"
                required maxLength={60} />
            </label>
          )}
          <label>
            <span>Email</span>
            <input className="field" type="email" inputMode="email" autoCapitalize="none" autoCorrect="off"
              spellCheck={false} value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" required />
          </label>
          <label>
            <span>Password</span>
            <input className="field" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              autoComplete={isSignup ? "new-password" : "current-password"} minLength={isSignup ? 8 : undefined}
              required />
            {isSignup && <small>At least 8 characters.</small>}
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="btn primary block" type="submit" disabled={busy}>
            {isSignup ? "Create account" : "Log in"}
          </button>
        </form>

        <p className="auth-switch">
          {isSignup ? (
            <>Already have an account? <Link to="/login">Log in</Link></>
          ) : (
            <>New here? <Link to="/signup">Create an account</Link></>
          )}
        </p>

        {config.demo && (
          <div className="auth-demo">
            <button type="button" className="btn block" disabled={busy} onClick={() => run(demo)}>
              Try the demo
            </button>
            <p>Six months of made-up training. You can look around but not change anything.</p>
          </div>
        )}
      </div>
    </div>
  );
}
