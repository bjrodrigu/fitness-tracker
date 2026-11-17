import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useAsync } from "../lib/useAsync";
import { formatDate, formatDuration, formatLb, formatMiles, formatPace, shortDate } from "../lib/format";
import TrainingLog from "../components/TrainingLog";
import { PaceTrendChart, Sparkline, WeeklyMileageChart } from "../components/Charts";

export default function TrainingPage() {
  const { data: s, error, loading } = useAsync(() => api.stats(16, 140), []);

  if (error) return <p className="state error">Couldn't load stats: {error}. Check that the API is running, then reload.</p>;
  if (loading && !s) return <p className="state">Loading…</p>;
  if (!s) return null;

  const empty = s.weeks.every((w) => w.runs === 0 && w.strengthSessions === 0);
  const sum = s.summary;

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Training</h1>
          <p>{formatDate(s.asOf, { weekday: "long", month: "long", day: "numeric" })}</p>
        </div>
        <div className="page-actions">
          <Link className="btn" to="/entries/new?type=STRENGTH">Log a lift</Link>
          <Link className="btn primary" to="/entries/new?type=RUN">Log a run</Link>
        </div>
      </header>

      {empty ? (
        <p className="state">
          Nothing logged in the last 16 weeks. <Link to="/entries/new?type=RUN">Log a run</Link>, or{" "}
          <Link to="/settings">link Garmin</Link> to bring in your runs.
        </p>
      ) : (
        <>
          <div className="summary">
            <div>
              <div className="value">{formatMiles(sum.weekMiles)}<small>mi</small></div>
              <div className="label">This week</div>
            </div>
            <div>
              <div className="value">{formatMiles(sum.lastWeekMiles)}<small>mi</small></div>
              <div className="label">Last week</div>
            </div>
            <div>
              <div className="value">{formatMiles(sum.avgMilesLast4Weeks)}<small>mi</small></div>
              <div className="label">4-week average</div>
            </div>
            <div>
              <div className="value">{Math.round(sum.yearMiles)}<small>mi</small></div>
              <div className="label">{sum.yearRuns} runs in {s.asOf.slice(0, 4)}</div>
            </div>
            <div>
              <div className="value">{sum.activeWeekStreak}<small>weeks</small></div>
              <div className="label">Training every week</div>
            </div>
          </div>

          <section className="section" aria-labelledby="log-h">
            <div className="section-head">
              <h2 id="log-h">Last 8 weeks</h2>
              <p>Miles per day</p>
            </div>
            <TrainingLog days={s.days} asOf={s.asOf} weeks={8} />
          </section>

          <section className="section grid-2">
            <div className="panel">
              <div className="chart-title">
                <h2>Weekly mileage</h2>
                <p>16 weeks</p>
              </div>
              <WeeklyMileageChart weeks={s.weeks} />
            </div>
            <div className="panel">
              <div className="chart-title">
                <h2>Average pace</h2>
                <p>All runs, per week</p>
              </div>
              <PaceTrendChart weeks={s.weeks} />
            </div>
          </section>

          <section className="section grid-2">
            <div className="panel">
              <div className="chart-title">
                <h2>Best efforts</h2>
                <p>Fastest run at each distance</p>
              </div>
              {s.bestEfforts.length === 0 ? (
                <p className="muted">Run a 5K or longer to see best efforts here.</p>
              ) : (
                <table className="table">
                  <thead>
                    <tr>
                      <th scope="col">Distance</th>
                      <th scope="col" className="num">Time</th>
                      <th scope="col" className="num">Pace</th>
                      <th scope="col">Run</th>
                    </tr>
                  </thead>
                  <tbody>
                    {s.bestEfforts.map((b) => (
                      <tr key={b.label}>
                        <td>{b.label}</td>
                        <td className="num pr-time">{formatDuration(b.timeSec)}</td>
                        <td className="num">{formatPace(b.paceSecPerMi)}</td>
                        <td>
                          <Link to={`/entries/${b.run.id}`}>{b.run.title}</Link>
                          <div className="muted">{shortDate(b.run.date)}</div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
              {s.longestRun && (
                <p className="muted" style={{ margin: "14px 0 0" }}>
                  Longest run: <Link to={`/entries/${s.longestRun.id}`}>{formatMiles(s.longestRun.distanceMi, 2)} mi</Link> on{" "}
                  {shortDate(s.longestRun.date)}
                </p>
              )}
            </div>
            <div className="panel">
              <div className="chart-title">
                <h2>Strength</h2>
                <p><Link to="/strength">Estimated max, change over 8 weeks</Link></p>
              </div>
              {s.lifts.filter((l) => !l.bodyweight).length === 0 ? (
                <p className="muted">Log a strength session to track lifts.</p>
              ) : (
                s.lifts
                  .filter((l) => !l.bodyweight)
                  .slice(0, 4)
                  .map((l) => (
                    <Link key={l.name} to={`/strength?lift=${encodeURIComponent(l.name)}`} className="lift-mini" style={{ textDecoration: "none" }}>
                      <div>
                        <b>{l.name}</b>
                        <div className="muted">{formatLb(l.latestE1rmLb)} lb</div>
                      </div>
                      <Sparkline values={l.sessions.slice(-16).map((x) => x.e1rmLb)} />
                      <div className={`delta${l.changeLast8WeeksLb > 0 ? "" : " flat"}`}>
                        {l.changeLast8WeeksLb > 0 ? `+${formatLb(l.changeLast8WeeksLb)} lb` : "No change"}
                      </div>
                    </Link>
                  ))
              )}
            </div>
          </section>
        </>
      )}
    </>
  );
}
