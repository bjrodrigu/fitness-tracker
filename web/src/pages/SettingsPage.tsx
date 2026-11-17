import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { garmin } from "../lib/api";
import { useAuth, useUser } from "../lib/auth";
import { useAsync } from "../lib/useAsync";
import type { GarminStatus } from "../lib/types";

function ago(iso: string): string {
  const mins = Math.round((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins} min ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours} hour${hours === 1 ? "" : "s"} ago`;
  const days = Math.round(hours / 24);
  return `${days} day${days === 1 ? "" : "s"} ago`;
}

function isStandalone() {
  return window.matchMedia("(display-mode: standalone)").matches ||
    (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

export default function SettingsPage() {
  const user = useUser();
  const { logout } = useAuth();
  const installed = isStandalone();
  const ios = /iPhone|iPad|iPod/.test(navigator.userAgent);

  return (
    <div className="settings">
      <header className="page-head">
        <div>
          <h1>Settings</h1>
        </div>
      </header>

      <section className="settings-block">
        <h2>Account</h2>
        <div className="settings-row">
          <div>
            <div className="strong">{user.name}</div>
            <div className="muted">{user.demo ? "Demo account, read-only" : user.email}</div>
          </div>
          <button type="button" className="btn" onClick={logout}>Log out</button>
        </div>
        {user.demo && (
          <p className="muted">
            Want your own journal? Log out, then create an account.
          </p>
        )}
      </section>

      <section className="settings-block">
        <h2>Garmin</h2>
        {user.demo ? (
          <p className="muted">Create an account to link Garmin.</p>
        ) : (
          <GarminSettings />
        )}
      </section>

      {!installed && (
        <section className="settings-block">
          <h2>Use it like an app</h2>
          <p className="muted">
            {ios
              ? "In Safari, tap Share, then Add to Home Screen. The journal then opens full screen from its own icon."
              : "In your browser's menu, choose Install app or Add to Home screen. The journal then opens in its own window."}
          </p>
        </section>
      )}

      <p className="settings-foot muted">
        <a href="/api/docs">API docs</a>
      </p>
    </div>
  );
}

function GarminSettings() {
  const { data, error, loading, reload } = useAsync(() => garmin.status(), []);
  const [status, setStatus] = useState<GarminStatus | null>(null);
  const s = status ?? data;

  if (error) return <p className="state error">Couldn't load Garmin settings: {error}</p>;
  if (loading && !s) return <p className="muted">Loading…</p>;
  if (!s) return null;
  if (!s.available) {
    return <p className="muted">Garmin linking is off on this server. Whoever runs it needs to set GARMIN_TOKEN_KEY.</p>;
  }
  if (!s.linked || s.lastSyncStatus === "NEEDS_RELINK") {
    return (
      <>
        {s.lastSyncStatus === "NEEDS_RELINK" && <p className="form-error">{s.lastSyncMessage}</p>}
        <GarminLinkForm onLinked={(next) => { setStatus(next); reload(); }} />
      </>
    );
  }
  return <GarminLinked status={s} onChange={setStatus} />;
}

function GarminLinked({ status, onChange }: { status: GarminStatus; onChange(s: GarminStatus | null): void }) {
  const [busy, setBusy] = useState<"sync" | "unlink" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const syncNow = async () => {
    setBusy("sync");
    setError(null);
    try {
      onChange(await garmin.sync());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(null);
    }
  };

  const unlink = async () => {
    if (!confirm("Unlink Garmin? Runs already imported stay in your journal.")) return;
    setBusy("unlink");
    try {
      await garmin.unlink();
      onChange({ ...status, linked: false, lastSyncStatus: null, lastSyncMessage: null });
    } catch (e) {
      setError((e as Error).message);
      setBusy(null);
    }
  };

  const last = status.lastSyncAt
    ? status.lastSyncStatus === "OK"
      ? `Last synced ${ago(status.lastSyncAt)}: ${status.lastSyncNew ?? 0} new ${status.lastSyncNew === 1 ? "entry" : "entries"}.`
      : `Last try ${ago(status.lastSyncAt)} didn't work: ${status.lastSyncMessage}`
    : "Not synced yet.";

  return (
    <>
      <div className="settings-row">
        <div>
          <div className="strong"><span className="dot ok" /> Linked</div>
          <div className="muted">{last}</div>
          <div className="muted">New runs come in every few hours on their own.</div>
        </div>
      </div>
      {error && <p className="form-error" role="alert">{error}</p>}
      <div className="settings-actions">
        <button type="button" className="btn primary" disabled={busy !== null} onClick={syncNow}>
          {busy === "sync" ? "Syncing…" : "Sync now"}
        </button>
        <button type="button" className="btn ghost danger" disabled={busy !== null} onClick={unlink}>
          Unlink Garmin
        </button>
      </div>
      <p className="muted small">
        Imported runs keep the notes, titles and tags you add here. <Link to="/journal">Open the journal</Link>
      </p>
    </>
  );
}

function GarminLinkForm({ onLinked }: { onLinked(s: GarminStatus): void }) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [needsCode, setNeedsCode] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = needsCode ? await garmin.code(code) : await garmin.link(email, password);
      setPassword("");
      if (res.needsCode) {
        setNeedsCode(true);
      } else {
        onLinked(res.status);
      }
    } catch (err) {
      setError((err as Error).message);
      if (needsCode) {
        setNeedsCode(false);
        setCode("");
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="auth-form" onSubmit={submit}>
      {needsCode ? (
        <label>
          <span>Code from Garmin</span>
          <input className="field" inputMode="numeric" autoComplete="one-time-code" value={code}
            onChange={(e) => setCode(e.target.value)} required maxLength={12} autoFocus />
          <small>Garmin just sent a code to your email or phone.</small>
        </label>
      ) : (
        <>
          <p className="muted">
            Sign in with your Garmin Connect account to bring in your runs and strength sessions, then keep them
            syncing every few hours.
          </p>
          <label>
            <span>Garmin email</span>
            <input className="field" type="email" inputMode="email" autoCapitalize="none" autoCorrect="off"
              spellCheck={false} value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="off" required />
          </label>
          <label>
            <span>Garmin password</span>
            <input className="field" type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              autoComplete="off" required />
            <small>
              Sent to Garmin once to sign in, never saved. The journal keeps only Garmin's login token, encrypted.
            </small>
          </label>
        </>
      )}
      {error && <p className="form-error" role="alert">{error}</p>}
      <button className="btn primary block" type="submit" disabled={busy}>
        {busy ? (needsCode ? "Checking…" : "Linking and importing…") : needsCode ? "Finish linking" : "Link Garmin"}
      </button>
      {busy && !needsCode && <p className="muted small">The first import reads a year of activities and can take a minute.</p>}
    </form>
  );
}
