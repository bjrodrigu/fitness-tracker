import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { useAsync } from "../lib/useAsync";
import type { Activity, ActivityType } from "../lib/types";
import { formatDate, formatLb, formatMiles, formatPace, parseDate, shortDate, summarizeSets, weekStartOf } from "../lib/format";

const FILTERS: { label: string; value: ActivityType | undefined }[] = [
  { label: "All", value: undefined },
  { label: "Runs", value: "RUN" },
  { label: "Strength", value: "STRENGTH" },
];

function useDebounced<T>(value: T, ms: number) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

function weekRange(start: string) {
  const end = parseDate(start);
  end.setDate(end.getDate() + 6);
  const sameMonth = parseDate(start).getMonth() === end.getMonth();
  const endLabel = sameMonth ? String(end.getDate()) : end.toLocaleDateString("en-US", { month: "short", day: "numeric" });
  return `${shortDate(start)} – ${endLabel}`;
}

function Metric({ a }: { a: Activity }) {
  if (a.type === "RUN") {
    return (
      <span className="metric">
        <b>{formatMiles(a.distanceMi, 2)}</b> mi{a.paceSecPerMi ? `  ${formatPace(a.paceSecPerMi)}/mi` : ""}
      </span>
    );
  }
  const main = a.exercises[0];
  return (
    <span className="metric">
      {main ? `${main.name} ${summarizeSets(main.sets)}` : ""}
      {a.exercises.length > 1 ? `, +${a.exercises.length - 1}` : ""}
    </span>
  );
}

export default function JournalPage() {
  const [params, setParams] = useSearchParams();
  const type = (params.get("type") as ActivityType | null) ?? undefined;
  const [text, setText] = useState(params.get("q") ?? "");
  const q = useDebounced(text.trim(), 250);
  const location = useLocation();

  useEffect(() => {
    const next = new URLSearchParams(params);
    if (q) next.set("q", q);
    else next.delete("q");
    if (next.toString() !== params.toString()) setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);

  const { data, error, loading } = useAsync(() => api.list({ type, q: q || undefined }), [type, q]);

  const groups = useMemo(() => {
    const m = new Map<string, Activity[]>();
    for (const a of data ?? []) {
      const k = weekStartOf(a.date);
      if (!m.has(k)) m.set(k, []);
      m.get(k)!.push(a);
    }
    return [...m.entries()];
  }, [data]);

  // Jump to a week when arriving from the training log.
  useEffect(() => {
    if (location.hash && data) document.getElementById(location.hash.slice(1))?.scrollIntoView();
  }, [location.hash, data]);

  const setType = (t: ActivityType | undefined) => {
    const next = new URLSearchParams(params);
    if (t) next.set("type", t);
    else next.delete("type");
    setParams(next, { replace: true });
  };

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Journal</h1>
          <p>Every run and lift, newest first.</p>
        </div>
        <div className="page-actions">
          <Link className="btn" to="/entries/new?type=STRENGTH">Log a lift</Link>
          <Link className="btn primary" to="/entries/new?type=RUN">Log a run</Link>
        </div>
      </header>

      <div className="toolbar">
        <div className="segmented" role="group" aria-label="Show">
          {FILTERS.map((f) => (
            <button key={f.label} type="button" aria-pressed={type === f.value} onClick={() => setType(f.value)}>
              {f.label}
            </button>
          ))}
        </div>
        <label className="visually-hidden" htmlFor="journal-search">Search entries</label>
        <input
          id="journal-search"
          className="field search"
          type="search"
          placeholder="Search titles, notes, tags, lifts"
          value={text}
          onChange={(e) => setText(e.target.value)}
        />
      </div>

      {error && <p className="state error">Couldn't load entries: {error}</p>}
      {!error && loading && !data && <p className="state">Loading…</p>}
      {data && data.length === 0 && (
        <p className="state">
          {q || type ? "No entries match. Try a different search or filter." : "No entries yet. Log your first run or lift to start the journal."}
        </p>
      )}

      {groups.map(([week, entries]) => {
        const runs = entries.filter((a) => a.type === "RUN");
        const miles = runs.reduce((s, a) => s + (a.distanceMi ?? 0), 0);
        const lifts = entries.length - runs.length;
        const parts = [];
        if (runs.length) parts.push(`${formatMiles(miles)} mi in ${runs.length} run${runs.length > 1 ? "s" : ""}`);
        if (lifts) parts.push(`${lifts} strength session${lifts > 1 ? "s" : ""}`);
        return (
          <section key={week} className="week-group" id={`week-${week}`} aria-label={`Week of ${shortDate(week)}`}>
            <div className="week-head">
              <h2>{weekRange(week)}</h2>
              <p>{parts.join(", ")}</p>
            </div>
            {entries.map((a) => (
              <Link key={a.id} to={`/entries/${a.id}`} className="entry">
                <span className="day">{formatDate(a.date, { weekday: "short", day: "numeric" })}</span>
                <span className={`mark ${a.type === "RUN" ? "run" : "strength"}`} aria-label={a.type === "RUN" ? "Run" : "Strength"} />
                <span className="title">{a.title}</span>
                <Metric a={a} />
                {(a.notes || a.tags.length > 0) && (
                  <span className="sub">
                    {a.tags.filter((t) => t === "race" || t === "time trial").map((t) => (
                      <span key={t} className={`chip ${t.replace(" ", "-")}`}>{t}</span>
                    ))}
                    {a.notes && <span className="note">{a.notes.split("\n")[0]}</span>}
                    {a.type === "STRENGTH" && !a.notes && a.volumeLb ? (
                      <span className="note">{formatLb(a.volumeLb)} lb total</span>
                    ) : null}
                  </span>
                )}
              </Link>
            ))}
          </section>
        );
      })}
    </>
  );
}
