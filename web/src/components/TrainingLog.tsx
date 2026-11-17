import { Link } from "react-router-dom";
import type { Day } from "../lib/types";
import { formatMiles, parseDate, shortDate } from "../lib/format";

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/**
 * The last few weeks as a paper-style running log: one row per week, one cell per day,
 * shaded by miles, with a green bar on strength days.
 */
export default function TrainingLog({ days, asOf, weeks = 8 }: { days: Day[]; asOf: string; weeks?: number }) {
  const byDate = new Map(days.map((d) => [d.date, d]));
  const today = parseDate(asOf);
  const monday = new Date(today);
  monday.setDate(today.getDate() - ((today.getDay() + 6) % 7));

  const maxMiles = Math.max(8, ...days.map((d) => d.runMiles));
  const rows = Array.from({ length: weeks }, (_, i) => {
    const start = new Date(monday);
    start.setDate(monday.getDate() - 7 * (weeks - 1 - i));
    const cells = DAYS.map((_, j) => {
      const d = new Date(start);
      d.setDate(start.getDate() + j);
      const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
      return { iso, day: byDate.get(iso), future: d > today, today: d.getTime() === today.getTime() };
    });
    const miles = cells.reduce((s, c) => s + (c.day?.runMiles ?? 0), 0);
    const lifts = cells.filter((c) => c.day?.strength).length;
    return { start: cells[0].iso, cells, miles, lifts };
  });

  return (
    <div>
      <table className="log">
        <caption className="visually-hidden">Miles run per day for the last {weeks} weeks</caption>
        <thead>
          <tr>
            <th className="week" scope="col">Week of</th>
            {DAYS.map((d) => (
              <th key={d} scope="col">{d}</th>
            ))}
            <th className="total" scope="col">Total</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.start}>
              <td className="week">
                <Link to={`/journal#week-${r.start}`}>{shortDate(r.start)}</Link>
              </td>
              {r.cells.map((c) => {
                const mi = c.day?.runMiles ?? 0;
                const share = Math.min(1, mi / maxMiles);
                const cls = ["cell", c.future && "future", c.today && "today", mi > 0 && "has-run", share > 0.45 && "deep"]
                  .filter(Boolean)
                  .join(" ");
                const label = `${shortDate(c.iso)}: ${mi > 0 ? `${formatMiles(mi)} miles` : "no run"}${c.day?.strength ? ", strength session" : ""}`;
                return (
                  <td key={c.iso} className={cls} title={label} aria-label={label}>
                    {mi > 0 && (
                      <span className="fill" style={{ background: `color-mix(in srgb, var(--run) ${Math.round(18 + 82 * share)}%, var(--run-soft))` }} />
                    )}
                    {mi > 0 && <span className="mi">{formatMiles(mi)}</span>}
                    {c.day?.strength && <span className="lift" />}
                  </td>
                );
              })}
              <td className="total">
                {formatMiles(r.miles)}
                <span>{r.lifts > 0 ? `${r.lifts} lift${r.lifts > 1 ? "s" : ""}` : " "}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="log-legend" aria-hidden="true">
        <span><i /> Miles, darker is longer</span>
        <span><i className="l" /> Strength session</span>
      </div>
    </div>
  );
}
