import { Link } from "react-router-dom";
import { CaloriesChart, WeightChart } from "../components/Charts";
import FoodNav from "../components/FoodNav";
import { nutrition } from "../lib/api";
import { kcal } from "../lib/food";
import { formatDate } from "../lib/format";
import { useAsync } from "../lib/useAsync";

const signed = (n: number, digits = 1) => `${n > 0 ? "+" : n < 0 ? "−" : ""}${Math.abs(n).toFixed(digits)}`;

export default function FoodProgressPage() {
  const { data: r, error, loading } = useAsync(() => nutrition.report(56), []);

  return (
    <div className="food">
      <header className="page-head food-head">
        <div>
          <h1>Progress</h1>
        </div>
        <FoodNav />
      </header>

      {error ? (
        <p className="state error">Couldn't load progress: {error}</p>
      ) : loading && !r ? (
        <p className="state">Loading…</p>
      ) : r ? (
        <>
          <div className="summary">
            <div>
              <div className="value">{r.summary.currentLb ?? "–"}<small>lb</small></div>
              <div className="label">7-day average</div>
            </div>
            <div>
              <div className="value">{r.summary.changeLb != null ? signed(r.summary.changeLb) : "–"}<small>lb</small></div>
              <div className="label">{r.goal?.startDate ? `Since ${formatDate(r.goal.startDate, { month: "short", day: "numeric" })}` : "Since first weigh-in"}</div>
            </div>
            <div>
              <div className="value">{r.summary.weeklyRateLb != null ? signed(r.summary.weeklyRateLb, 2) : "–"}<small>lb/wk</small></div>
              <div className="label">Last 4 weeks{r.goal?.weeklyRateLb ? `, plan ${r.goal.direction === "LOSE" ? "−" : r.goal.direction === "GAIN" ? "+" : ""}${r.goal.weeklyRateLb}` : ""}</div>
            </div>
            <div>
              <div className="value">{r.summary.remainingLb != null ? Math.abs(r.summary.remainingLb).toFixed(1) : "–"}<small>lb</small></div>
              <div className="label">
                {r.summary.projectedDate
                  ? `To go, about ${formatDate(r.summary.projectedDate, { month: "short", day: "numeric", year: "numeric" })}`
                  : "To go"}
              </div>
            </div>
            <div>
              <div className="value">{r.summary.avgCalories7 != null ? kcal(r.summary.avgCalories7) : "–"}<small>kcal</small></div>
              <div className="label">
                Daily average, last 7 days{r.goal ? ` (target ${kcal(r.goal.calories)})` : ""}
              </div>
            </div>
          </div>

          {!r.goal && (
            <p className="muted goal-hint"><Link to="/food/goal">Set a goal</Link> to see how far there is to go.</p>
          )}

          <section className="section panel">
            <div className="chart-title">
              <h2>Weight</h2>
              <p>Dots are weigh-ins; the line is the 7-day average</p>
            </div>
            {r.weights.length > 1 ? (
              <WeightChart points={r.weights} target={r.summary.targetLb} />
            ) : (
              <p className="muted">Log your weight on a few days to see the trend. <Link to="/food">Log today's</Link></p>
            )}
          </section>

          <section className="section panel">
            <div className="chart-title">
              <h2>Calories</h2>
              <p>Last 8 weeks{r.goal ? ", dashed line is your target" : ""}</p>
            </div>
            <CaloriesChart days={r.days} target={r.goal?.calories ?? null} />
          </section>

          <p className="muted small fine-print">
            Weight moves a few pounds day to day with water and food, so the 7-day average is the number to watch.
          </p>
        </>
      ) : null}
    </div>
  );
}
