import { Suspense, useEffect, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { useUser } from "../lib/auth";
import Logo from "./Logo";
import { mealForNow } from "../lib/food";

/* Tab bar icons, 24px, drawn on a 24 grid with a 1.75 stroke. */
const icons = {
  training: (
    <path d="M4 20V13M10 20V8M16 20v-5M22 20V4" />
  ),
  journal: (
    <path d="M6 3.5h11a1.5 1.5 0 0 1 1.5 1.5v15a.5.5 0 0 1-.5.5H6.5A1.5 1.5 0 0 1 5 19V4.5a1 1 0 0 1 1-1ZM9 8h6M9 12h6M9 16h3" />
  ),
  food: (
    <path d="M7 3v7a2 2 0 0 0 2 2v9M11 3v7a2 2 0 0 1-2 2M9 3v6M17 21V3c-2 0-3.5 2.5-3.5 6.5S15 13 17 13" />
  ),
  settings: (
    <>
      <circle cx="12" cy="8" r="3.5" />
      <path d="M5 20c.8-3.6 3.6-5.5 7-5.5s6.2 1.9 7 5.5" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
};

function Icon({ name }: { name: keyof typeof icons }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {icons[name]}
    </svg>
  );
}

export default function Layout() {
  const user = useUser();
  const location = useLocation();
  const [sheet, setSheet] = useState(false);

  // Close the "log" sheet whenever the page changes.
  useEffect(() => setSheet(false), [location.pathname, location.search]);
  useEffect(() => {
    if (!sheet) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setSheet(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [sheet]);

  return (
    <div className={`shell${user.demo ? " is-demo" : ""}`}>
      <aside className="sidebar">
        <NavLink to="/training" className="wordmark">
          <Logo />
          Training Journal
        </NavLink>
        <nav aria-label="Main">
          <div className="nav-group-label">Views</div>
          <div className="nav">
            <NavLink to="/training">Training</NavLink>
            <NavLink to="/journal">Journal</NavLink>
            <NavLink to="/strength">Strength</NavLink>
            <NavLink to="/food">Food</NavLink>
          </div>
        </nav>
        {!user.demo && (
          <div>
            <div className="nav-group-label">Add</div>
            <div className="nav">
              <Link to="/entries/new?type=RUN">
                <span className="mark run" /> Log a run
              </Link>
              <Link to="/entries/new?type=STRENGTH">
                <span className="mark strength" /> Log a lift
              </Link>
              <Link to={`/food?add=${mealForNow()}`}>
                <span className="mark food" /> Log food
              </Link>
            </div>
          </div>
        )}
        <div className="sidebar-foot">
          {user.demo && <p>Showing generated sample data. Nothing here is a real person's training.</p>}
          <div className="nav">
            <NavLink to="/settings">
              <span className="avatar" aria-hidden="true">{user.name.slice(0, 1).toUpperCase()}</span>
              {user.demo ? "Demo" : user.name}
            </NavLink>
          </div>
        </div>
      </aside>

      <header className="appbar">
        <NavLink to="/training" className="wordmark">
          <Logo />
          Training Journal
        </NavLink>
        {user.demo && <span className="chip">Demo</span>}
      </header>

      <main className="main">
        {user.demo && (
          <p className="demo-note">
            You're looking at the demo: made-up training, read-only. <Link to="/settings">Make your own journal</Link>
          </p>
        )}
        <Suspense fallback={<p className="state" aria-busy="true">Loading…</p>}>
          <Outlet />
        </Suspense>
      </main>

      <nav className="tabbar" aria-label="Main">
        {/* One tab covers Running and Strength on phones. */}
        <Link to="/training" aria-current={/^\/(training|strength)/.test(location.pathname) ? "page" : undefined}>
          <Icon name="training" /><span>Training</span>
        </Link>
        <NavLink to="/journal"><Icon name="journal" /><span>Journal</span></NavLink>
        {user.demo ? null : (
          <button type="button" className="tab-log" aria-haspopup="dialog" aria-expanded={sheet}
            onClick={() => setSheet(true)}>
            <span className="tab-log-ring"><Icon name="plus" /></span>
            <span>Log</span>
          </button>
        )}
        <NavLink to="/food"><Icon name="food" /><span>Food</span></NavLink>
        <NavLink to="/settings"><Icon name="settings" /><span>You</span></NavLink>
      </nav>

      {sheet && (
        <div className="sheet-backdrop" onClick={() => setSheet(false)}>
          <div className="sheet" role="dialog" aria-modal="true" aria-label="Log an entry" onClick={(e) => e.stopPropagation()}>
            <div className="sheet-grip" aria-hidden="true" />
            <Link to="/entries/new?type=RUN" className="sheet-option" autoFocus>
              <span className="mark run" />
              <span><b>Log a run</b><small>Distance, time, how it felt</small></span>
            </Link>
            <Link to="/entries/new?type=STRENGTH" className="sheet-option">
              <span className="mark strength" />
              <span><b>Log a lift</b><small>Exercises, sets, reps and weight</small></span>
            </Link>
            <Link to={`/food?add=${mealForNow()}`} className="sheet-option">
              <span className="mark food" />
              <span><b>Log food</b><small>Calories, protein, carbs and fat</small></span>
            </Link>
            <button type="button" className="btn ghost block" onClick={() => setSheet(false)}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  );
}
