import { useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import FoodNav from "../components/FoodNav";
import FoodSheet from "../components/FoodSheet";
import { nutrition } from "../lib/api";
import { useUser } from "../lib/auth";
import { addDays, grams, kcal, mealName, todayIso } from "../lib/food";
import { formatDate } from "../lib/format";
import { MEALS, type FoodEntry, type Meal, type NutritionGoal } from "../lib/types";
import { useAsync } from "../lib/useAsync";

export default function FoodPage() {
  const readOnly = useUser().demo;
  const [params, setParams] = useSearchParams();
  const today = todayIso();
  const date = params.get("date") ?? today;
  const addMeal = params.get("add") as Meal | null;
  const [editing, setEditing] = useState<FoodEntry | null>(null);

  const day = useAsync(() => nutrition.day(date), [date]);
  const report = useAsync(() => nutrition.report(56), []);

  const go = (d: string) => setParams(d === today ? {} : { date: d }, { replace: true });
  const openAdd = (m: Meal) => setParams({ ...(date === today ? {} : { date }), add: m }, { replace: true });
  const closeSheet = () => {
    setEditing(null);
    if (addMeal) setParams(date === today ? {} : { date }, { replace: true });
  };
  const saved = () => {
    closeSheet();
    day.reload();
    report.reload();
  };

  const entries = day.data ?? [];
  const goal = report.data?.goal ?? null;
  const weight = report.data?.weights.find((w) => w.date === date);
  const totals = entries.reduce(
    (t, e) => ({
      calories: t.calories + (e.totalCalories ?? 0),
      proteinG: t.proteinG + (e.totalProteinG ?? 0),
      carbsG: t.carbsG + (e.totalCarbsG ?? 0),
      fatG: t.fatG + (e.totalFatG ?? 0),
    }),
    { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 },
  );

  const label = date === today ? "Today" : date === addDays(today, -1) ? "Yesterday"
    : formatDate(date, { weekday: "long", month: "short", day: "numeric" });

  return (
    <div className="food">
      <header className="page-head food-head">
        <div>
          <h1>Food</h1>
        </div>
        <FoodNav />
      </header>

      <div className="day-switch">
        <button type="button" className="btn ghost icon" onClick={() => go(addDays(date, -1))} aria-label="Previous day">‹</button>
        <div className="day-label">
          <b>{label}</b>
          {date !== today && <button type="button" className="linklike" onClick={() => go(today)}>Back to today</button>}
        </div>
        <button type="button" className="btn ghost icon" onClick={() => go(addDays(date, 1))} aria-label="Next day"
          disabled={date >= today}>›</button>
      </div>

      {day.error ? (
        <p className="state error">Couldn't load this day: {day.error}</p>
      ) : (
        <>
          <Budget totals={totals} goal={goal} />

          <WeightRow date={date} weightLb={weight?.weightLb ?? null} readOnly={readOnly} onSaved={report.reload} />

          {MEALS.map((m) => {
            const list = entries.filter((e) => e.meal === m);
            const mealKcal = list.reduce((s, e) => s + (e.totalCalories ?? 0), 0);
            return (
              <section key={m} className="meal">
                <div className="meal-head">
                  <h2>{mealName(m)}</h2>
                  <span className="muted">{list.length ? `${kcal(mealKcal)} kcal` : ""}</span>
                </div>
                {list.map((e) => (
                  <button key={e.id} type="button" className="food-row" onClick={() => !readOnly && setEditing(e)}
                    disabled={readOnly}>
                    <span className="food-name">
                      {e.name}
                      {e.servings !== 1 && <small> × {e.servings}</small>}
                    </span>
                    <span className="food-kcal">{kcal(e.totalCalories ?? 0)}</span>
                    <span className="food-macros">
                      P {grams(e.totalProteinG ?? 0)} · C {grams(e.totalCarbsG ?? 0)} · F {grams(e.totalFatG ?? 0)}
                    </span>
                  </button>
                ))}
                {!readOnly && (
                  <button type="button" className="add-food" onClick={() => openAdd(m)}>
                    + Add to {mealName(m).toLowerCase()}
                  </button>
                )}
              </section>
            );
          })}
        </>
      )}

      {(addMeal || editing) && !readOnly && (
        <FoodSheet date={date} meal={editing?.meal ?? addMeal ?? "SNACK"} entry={editing ?? undefined}
          onClose={closeSheet} onSaved={saved} />
      )}
    </div>
  );
}

function Bar({ value, target, kind }: { value: number; target: number; kind: string }) {
  const pct = target > 0 ? Math.min(100, (value / target) * 100) : 0;
  const over = target > 0 && value > target;
  return (
    <div className={`bar ${kind}${over ? " over" : ""}`} aria-hidden="true">
      <i style={{ width: `${pct}%` }} />
    </div>
  );
}

function Budget({ totals, goal }: { totals: { calories: number; proteinG: number; carbsG: number; fatG: number }; goal: NutritionGoal | null }) {
  if (!goal) {
    return (
      <div className="budget">
        <div className="budget-main">
          <div className="budget-big">{kcal(totals.calories)}<small> kcal eaten</small></div>
        </div>
        <p className="muted">
          <Link to="/food/goal">Set a goal</Link> to see how much is left each day.
        </p>
      </div>
    );
  }
  const left = goal.calories - totals.calories;
  const macros = [
    { k: "p", name: "Protein", v: totals.proteinG, t: goal.proteinG },
    { k: "c", name: "Carbs", v: totals.carbsG, t: goal.carbsG },
    { k: "f", name: "Fat", v: totals.fatG, t: goal.fatG },
  ];
  return (
    <div className="budget">
      <div className="budget-main">
        <div className="budget-big">
          {kcal(Math.abs(left))}
          <small> kcal {left >= 0 ? "left" : "over"}</small>
        </div>
        <div className="muted">{kcal(totals.calories)} of {kcal(goal.calories)}</div>
      </div>
      <Bar value={totals.calories} target={goal.calories} kind="kcal" />
      <div className="macros">
        {macros.map((m) => (
          <div key={m.k} className="macro">
            <div className="macro-top">
              <span><i className={`macro-dot ${m.k}`} />{m.name}</span>
              <span><b>{grams(m.v)}</b> / {m.t} g</span>
            </div>
            <Bar value={m.v} target={m.t} kind={m.k} />
          </div>
        ))}
      </div>
    </div>
  );
}

function WeightRow({ date, weightLb, readOnly, onSaved }: { date: string; weightLb: number | null; readOnly: boolean; onSaved(): void }) {
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState("");
  const [error, setError] = useState<string | null>(null);

  const start = () => {
    setValue(weightLb != null ? String(weightLb) : "");
    setError(null);
    setEditing(true);
  };
  const save = async (e: FormEvent) => {
    e.preventDefault();
    const lb = Number(value);
    try {
      if (value.trim() === "") await nutrition.unweigh(date);
      else if (!(lb >= 60 && lb <= 700)) return setError("Enter a weight in pounds.");
      else await nutrition.weigh(date, lb);
      setEditing(false);
      onSaved();
    } catch (err) {
      setError((err as Error).message);
    }
  };

  if (editing) {
    return (
      <form className="weight-row" onSubmit={save}>
        <label htmlFor="weigh-in">Weight</label>
        <input id="weigh-in" className="field" inputMode="decimal" value={value} onChange={(e) => setValue(e.target.value)}
          autoFocus placeholder="lb" />
        <button type="submit" className="btn primary">Save</button>
        <button type="button" className="btn ghost" onClick={() => setEditing(false)}>Cancel</button>
        {error && <p className="form-error">{error}</p>}
      </form>
    );
  }
  return (
    <div className="weight-row">
      <span className="muted">Weight</span>
      <b>{weightLb != null ? `${weightLb} lb` : "Not logged"}</b>
      {!readOnly && (
        <button type="button" className="btn small" onClick={start}>{weightLb != null ? "Edit" : "Log weight"}</button>
      )}
    </div>
  );
}
