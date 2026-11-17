import { Link, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { useAsync } from "../lib/useAsync";
import { formatLb, shortDate } from "../lib/format";
import { LiftChart } from "../components/Charts";

export default function StrengthPage() {
  const { data: s, error, loading } = useAsync(() => api.stats(16, 7), []);
  const [params, setParams] = useSearchParams();

  if (error) return <p className="state error">Couldn't load lifts: {error}</p>;
  if (loading && !s) return <p className="state">Loading…</p>;
  if (!s) return null;

  const lifts = s.lifts;
  const selected = lifts.find((l) => l.name === params.get("lift")) ?? lifts.find((l) => !l.bodyweight) ?? lifts[0];

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Strength</h1>
          <p>Estimated one-rep max per lift, from your best set each session.</p>
        </div>
        <div className="page-actions">
          <Link className="btn primary" to="/entries/new?type=STRENGTH">Log a lift</Link>
        </div>
      </header>

      {!selected ? (
        <p className="state">No strength sessions yet. Log one and your lifts will show up here.</p>
      ) : (
        <div className="lift-layout">
          <div className="lift-list" role="group" aria-label="Lifts">
            {lifts.map((l) => (
              <button
                key={l.name}
                type="button"
                aria-pressed={l === selected}
                onClick={() => setParams({ lift: l.name }, { replace: true })}
              >
                <span className="name">{l.name}</span>
                <span className="val">{l.bodyweight ? `${l.bestReps} reps` : `${formatLb(l.latestE1rmLb)} lb`}</span>
                <span className="meta">
                  {l.sessions.length} sessions
                  {!l.bodyweight && l.changeLast8WeeksLb !== 0
                    ? `, ${l.changeLast8WeeksLb > 0 ? "+" : ""}${formatLb(l.changeLast8WeeksLb)} lb in 8 weeks`
                    : ""}
                </span>
              </button>
            ))}
          </div>

          <div>
            <div className="panel">
              <div className="lift-head">
                <h2>{selected.name}</h2>
                {selected.bodyweight ? (
                  <div className="big">{selected.bestReps}<small>best reps</small></div>
                ) : (
                  <div className="big">{formatLb(selected.bestE1rmLb)}<small>lb best estimated max</small></div>
                )}
              </div>
              {selected.bodyweight ? (
                <p className="muted">Bodyweight lift. Add weight to a set to track an estimated max.</p>
              ) : (
                <>
                  <LiftChart sessions={selected.sessions} />
                  <p className="muted" style={{ margin: "8px 0 0", fontSize: "var(--step--1)" }}>
                    Line: estimated max (Epley, weight × (1 + reps ÷ 30)). Dots: weight of the top set.
                  </p>
                </>
              )}
            </div>

            <section className="section">
              <div className="section-head">
                <h2>Recent sessions</h2>
              </div>
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col" className="num">Top set</th>
                    {!selected.bodyweight && <th scope="col" className="num">Estimated max</th>}
                  </tr>
                </thead>
                <tbody>
                  {[...selected.sessions].reverse().slice(0, 10).map((x) => (
                    <tr key={x.activityId + x.date}>
                      <td><Link to={`/entries/${x.activityId}`}>{shortDate(x.date)}</Link></td>
                      <td className="num">
                        {x.topReps}
                        {x.topWeightLb > 0 ? ` × ${formatLb(x.topWeightLb)} lb` : " reps"}
                      </td>
                      {!selected.bodyweight && <td className="num">{formatLb(x.e1rmLb)} lb</td>}
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>
        </div>
      )}
    </>
  );
}
