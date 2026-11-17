import { Link, NavLink, Outlet } from "react-router-dom";
import { useAsync } from "../lib/useAsync";
import { api } from "../lib/api";

function Logo() {
  return (
    <svg width="22" height="22" viewBox="0 0 32 32" aria-hidden="true">
      <rect width="32" height="32" rx="7" fill="var(--ink)" />
      <rect x="7" y="17" width="4" height="8" rx="1" fill="var(--run)" />
      <rect x="14" y="11" width="4" height="14" rx="1" fill="var(--run)" />
      <rect x="21" y="7" width="4" height="18" rx="1" fill="var(--lift)" />
    </svg>
  );
}

export default function Layout() {
  // Only used to say when the journal is showing generated sample entries.
  const { data } = useAsync(() => api.list(), []);
  const demo = data?.some((a) => a.source === "demo");

  return (
    <div className="shell">
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
          </div>
        </nav>
        <div>
          <div className="nav-group-label">Add</div>
          <div className="nav">
            <Link to="/entries/new?type=RUN">
              <span className="mark run" /> Log a run
            </Link>
            <Link to="/entries/new?type=STRENGTH">
              <span className="mark strength" /> Log a lift
            </Link>
          </div>
        </div>
        <div className="sidebar-foot">
          {demo && <p>Showing generated sample data. Nothing here is a real person's training.</p>}
          <p>
            <a href="/api/docs">API docs</a>
          </p>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}
