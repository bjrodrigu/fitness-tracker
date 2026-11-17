import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import FoodNav from "../components/FoodNav";
import { nutrition } from "../lib/api";
import { useUser } from "../lib/auth";
import type { ActivityLevel, Direction, GoalSuggestion, Sex } from "../lib/types";
import { useAsync } from "../lib/useAsync";

const RATES = [0.25, 0.5, 0.75, 1, 1.5, 2];
const ACTIVITY: { v: ActivityLevel; label: string }[] = [
  { v: "SEDENTARY", label: "Mostly sitting, little exercise" },
  { v: "LIGHT", label: "Exercise 1 to 3 days a week" },
  { v: "MODERATE", label: "Exercise 3 to 5 days a week" },
  { v: "VERY", label: "Hard training 6 or 7 days a week" },
];

const PROFILE_KEY = "journal.goalProfile";

export default function FoodGoalPage() {
  const readOnly = useUser().demo;
  const navigate = useNavigate();
  const { data: r } = useAsync(() => nutrition.report(28), []);

  const [direction, setDirection] = useState<Direction>("LOSE");
  const [target, setTarget] = useState("");
  const [rate, setRate] = useState(0.5);
  const [calories, setCalories] = useState("");
  const [protein, setProtein] = useState("");
  const [carbs, setCarbs] = useState("");
  const [fat, setFat] = useState("");

  // Body details for the calculator. Kept on this device only, never sent to be stored.
  const [sex, setSex] = useState<Sex>("FEMALE");
  const [age, setAge] = useState("");
  const [feet, setFeet] = useState("");
  const [inches, setInches] = useState("");
  const [weight, setWeight] = useState("");
  const [activity, setActivity] = useState<ActivityLevel>("MODERATE");
  const [suggestion, setSuggestion] = useState<GoalSuggestion | null>(null);

  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    try {
      const p = JSON.parse(localStorage.getItem(PROFILE_KEY) ?? "null");
      if (p) {
        setSex(p.sex); setAge(p.age); setFeet(p.feet); setInches(p.inches); setActivity(p.activity);
      }
    } catch {
      /* storage unavailable */
    }
  }, []);

  useEffect(() => {
    if (!r) return;
    const g = r.goal;
    if (g) {
      setDirection(g.direction);
      setTarget(g.targetWeightLb != null ? String(g.targetWeightLb) : "");
      setRate(g.weeklyRateLb ?? 0.5);
      setCalories(String(g.calories)); setProtein(String(g.proteinG)); setCarbs(String(g.carbsG)); setFat(String(g.fatG));
    }
    if (r.summary.currentLb != null) setWeight(String(r.summary.currentLb));
  }, [r]);

  const suggest = async () => {
    setError(null);
    const heightIn = Number(feet) * 12 + Number(inches || 0);
    try {
      localStorage.setItem(PROFILE_KEY, JSON.stringify({ sex, age, feet, inches, activity }));
    } catch {
      /* fine */
    }
    try {
      const s = await nutrition.suggest({
        sex, age: Number(age), heightIn, weightLb: Number(weight), activity, direction,
        weeklyRateLb: direction === "MAINTAIN" ? null : rate,
      });
      setSuggestion(s);
      setCalories(String(s.calories)); setProtein(String(s.proteinG)); setCarbs(String(s.carbsG)); setFat(String(s.fatG));
      if (direction !== "MAINTAIN") setRate(s.weeklyRateLb >= 0.25 ? RATES.reduce((a, b) => (Math.abs(b - s.weeklyRateLb) < Math.abs(a - s.weeklyRateLb) ? b : a)) : 0.25);
    } catch (e) {
      setError((e as Error).message === "Bad Request" || (e as Error).message.startsWith("400")
        ? "Fill in age, height and weight to work out targets." : (e as Error).message);
    }
  };

  const macroKcal = Number(protein || 0) * 4 + Number(carbs || 0) * 4 + Number(fat || 0) * 9;

  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await nutrition.saveGoal({
        direction,
        targetWeightLb: direction === "MAINTAIN" || target.trim() === "" ? null : Number(target),
        weeklyRateLb: direction === "MAINTAIN" ? null : rate,
        calories: Number(calories), proteinG: Number(protein), carbsG: Number(carbs), fatG: Number(fat),
      });
      navigate("/food/progress");
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  return (
    <div className="food goal-page">
      <header className="page-head food-head">
        <div>
          <h1>Goal</h1>
        </div>
        <FoodNav />
      </header>

      <form onSubmit={save}>
        <fieldset className="doc-body" disabled={readOnly}>
          <section className="settings-block">
            <h2>Where you're headed</h2>
            <div className="segmented wide" role="group" aria-label="Goal">
              {(["LOSE", "MAINTAIN", "GAIN"] as Direction[]).map((d) => (
                <button key={d} type="button" aria-pressed={direction === d} onClick={() => setDirection(d)}>
                  {d === "LOSE" ? "Lose" : d === "GAIN" ? "Gain" : "Maintain"}
                </button>
              ))}
            </div>
            {direction !== "MAINTAIN" && (
              <div className="ff-grid two">
                <label className="ff">
                  <span>Goal weight, lb</span>
                  <input className="field" inputMode="decimal" value={target} onChange={(e) => setTarget(e.target.value)} />
                </label>
                <label className="ff">
                  <span>Pace</span>
                  <select className="field" value={rate} onChange={(e) => setRate(Number(e.target.value))}>
                    {RATES.filter((x) => direction === "LOSE" || x <= 1).map((x) => (
                      <option key={x} value={x}>{x} lb a week</option>
                    ))}
                  </select>
                </label>
              </div>
            )}
          </section>
        </fieldset>

          <section className="settings-block">
            <h2>Work out your targets</h2>
            <p className="muted">Optional. Estimates what you burn in a day, then adjusts for your pace.</p>
            <div className="ff-grid">
              <label className="ff">
                <span>Formula uses</span>
                <select className="field" value={sex} onChange={(e) => setSex(e.target.value as Sex)}>
                  <option value="FEMALE">Female</option>
                  <option value="MALE">Male</option>
                </select>
              </label>
              <label className="ff">
                <span>Age</span>
                <input className="field" inputMode="numeric" value={age} onChange={(e) => setAge(e.target.value)} />
              </label>
              <label className="ff">
                <span>Height, ft</span>
                <input className="field" inputMode="numeric" value={feet} onChange={(e) => setFeet(e.target.value)} />
              </label>
              <label className="ff">
                <span>and in</span>
                <input className="field" inputMode="numeric" value={inches} onChange={(e) => setInches(e.target.value)} />
              </label>
              <label className="ff">
                <span>Weight, lb</span>
                <input className="field" inputMode="decimal" value={weight} onChange={(e) => setWeight(e.target.value)} />
              </label>
            </div>
            <label className="ff">
              <span>Activity</span>
              <select className="field" value={activity} onChange={(e) => setActivity(e.target.value as ActivityLevel)}>
                {ACTIVITY.map((a) => <option key={a.v} value={a.v}>{a.label}</option>)}
              </select>
            </label>
            <div className="settings-actions">
              <button type="button" className="btn" onClick={suggest}>Work out targets</button>
            </div>
            {suggestion && (
              <div className="suggestion">
                <p>
                  You burn about <b>{suggestion.maintenanceCalories.toLocaleString()} kcal</b> a day.{" "}
                  {direction === "MAINTAIN" ? "Eating that keeps your weight steady."
                    : `Eating ${suggestion.calories.toLocaleString()} should ${direction === "LOSE" ? "lose" : "gain"} about ${suggestion.weeklyRateLb} lb a week.`}
                </p>
                {suggestion.notes.map((n) => <p key={n} className="note">{n}</p>)}
              </div>
            )}
          </section>

        <fieldset className="doc-body" disabled={readOnly}>
          <section className="settings-block">
            <h2>Daily targets</h2>
            <div className="ff-grid">
              <label className="ff">
                <span>Calories</span>
                <input className="field" inputMode="numeric" value={calories} onChange={(e) => setCalories(e.target.value)} required />
              </label>
              <label className="ff">
                <span><i className="macro-dot p" />Protein g</span>
                <input className="field" inputMode="numeric" value={protein} onChange={(e) => setProtein(e.target.value)} required />
              </label>
              <label className="ff">
                <span><i className="macro-dot c" />Carbs g</span>
                <input className="field" inputMode="numeric" value={carbs} onChange={(e) => setCarbs(e.target.value)} required />
              </label>
              <label className="ff">
                <span><i className="macro-dot f" />Fat g</span>
                <input className="field" inputMode="numeric" value={fat} onChange={(e) => setFat(e.target.value)} required />
              </label>
            </div>
            {macroKcal > 0 && Number(calories) > 0 && Math.abs(macroKcal - Number(calories)) > 50 && (
              <p className="muted small">Those macros add up to {macroKcal.toLocaleString()} kcal, not {Number(calories).toLocaleString()}.</p>
            )}
          </section>
        </fieldset>

        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="settings-actions">
          {readOnly
            ? <p className="muted">The demo goal is read-only, but you can still try the calculator.</p>
            : <button type="submit" className="btn primary" disabled={busy}>Save goal</button>}
        </div>
        <p className="muted small fine-print">
          These are estimates. After two or three weeks, compare your actual rate on the Progress page and adjust.
          If you have a medical condition or are pregnant, check targets with a doctor or dietitian.
        </p>
      </form>
    </div>
  );
}
