import { useEffect, useMemo, useState, type FormEvent } from "react";
import { nutrition } from "../lib/api";
import { MEALS, type FoodEntry, type Meal } from "../lib/types";
import { mealName } from "../lib/food";

interface Props {
  date: string;
  meal: Meal;
  /** Editing an existing entry; otherwise adding. */
  entry?: FoodEntry;
  onClose(): void;
  onSaved(): void;
}

type Fields = { name: string; servings: string; calories: string; proteinG: string; carbsG: string; fatG: string };

const toFields = (f?: Partial<FoodEntry>): Fields => ({
  name: f?.name ?? "",
  servings: f?.servings != null ? String(f.servings) : "1",
  calories: f?.calories != null ? String(f.calories) : "",
  proteinG: f?.proteinG ? String(f.proteinG) : "",
  carbsG: f?.carbsG ? String(f.carbsG) : "",
  fatG: f?.fatG ? String(f.fatG) : "",
});

const num = (s: string) => (s.trim() === "" ? 0 : Number(s));

/** Add or edit one food. A sheet from the bottom on phones, a dialog on larger screens. */
export default function FoodSheet({ date, meal: initialMeal, entry, onClose, onSaved }: Props) {
  const [meal, setMeal] = useState<Meal>(entry?.meal ?? initialMeal);
  const [f, setF] = useState<Fields>(toFields(entry));
  const [recent, setRecent] = useState<FoodEntry[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!entry) nutrition.recent().then(setRecent, () => undefined);
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [entry, onClose]);

  const matches = useMemo(() => {
    const q = f.name.trim().toLowerCase();
    return recent.filter((r) => !q || r.name.toLowerCase().includes(q)).slice(0, 8);
  }, [recent, f.name]);

  const set = (k: keyof Fields) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });
  const total = num(f.servings) * num(f.calories);

  const save = async (e: FormEvent) => {
    e.preventDefault();
    const body: FoodEntry = {
      date: entry?.date ?? date,
      meal,
      name: f.name.trim(),
      servings: num(f.servings),
      calories: Math.round(num(f.calories)),
      proteinG: num(f.proteinG),
      carbsG: num(f.carbsG),
      fatG: num(f.fatG),
    };
    if (!body.name) return setError("Add a name.");
    if (!(body.servings > 0)) return setError("Servings has to be more than 0.");
    if ([body.calories, body.proteinG, body.carbsG, body.fatG].some((v) => Number.isNaN(v) || v < 0)) {
      return setError("Use numbers of 0 or more.");
    }
    setBusy(true);
    setError(null);
    try {
      if (entry?.id) await nutrition.update(entry.id, body);
      else await nutrition.add(body);
      onSaved();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!entry?.id) return;
    setBusy(true);
    try {
      await nutrition.remove(entry.id);
      onSaved();
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  };

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <form className="sheet food-sheet" role="dialog" aria-modal="true" aria-label={entry ? "Edit food" : "Add food"}
        onClick={(e) => e.stopPropagation()} onSubmit={save}>
        <div className="sheet-grip" aria-hidden="true" />
        <div className="food-sheet-head">
          <h2>{entry ? "Edit food" : "Add food"}</h2>
          <select className="field meal-select" value={meal} onChange={(e) => setMeal(e.target.value as Meal)}
            aria-label="Meal">
            {MEALS.map((m) => <option key={m} value={m}>{mealName(m)}</option>)}
          </select>
        </div>

        <label className="ff">
          <span>Food</span>
          <input className="field" value={f.name} onChange={set("name")} placeholder="e.g. Greek yogurt" autoFocus={!entry}
            autoComplete="off" maxLength={120} />
        </label>

        {!entry && matches.length > 0 && (
          <div className="recent" role="group" aria-label="Logged before">
            {matches.map((r) => (
              <button key={r.id} type="button" className="recent-item" onClick={() => setF(toFields({ ...r, servings: r.servings }))}>
                <span>{r.name}</span>
                <small>{r.calories} kcal</small>
              </button>
            ))}
          </div>
        )}

        <div className="ff-grid">
          <label className="ff">
            <span>Servings</span>
            <input className="field" inputMode="decimal" value={f.servings} onChange={set("servings")} />
          </label>
          <label className="ff">
            <span>Calories <small>per serving</small></span>
            <input className="field" inputMode="numeric" value={f.calories} onChange={set("calories")} placeholder="0" />
          </label>
          <label className="ff">
            <span><i className="macro-dot p" />Protein g</span>
            <input className="field" inputMode="decimal" value={f.proteinG} onChange={set("proteinG")} placeholder="0" />
          </label>
          <label className="ff">
            <span><i className="macro-dot c" />Carbs g</span>
            <input className="field" inputMode="decimal" value={f.carbsG} onChange={set("carbsG")} placeholder="0" />
          </label>
          <label className="ff">
            <span><i className="macro-dot f" />Fat g</span>
            <input className="field" inputMode="decimal" value={f.fatG} onChange={set("fatG")} placeholder="0" />
          </label>
          <div className="ff total">
            <span>Total</span>
            <b>{Number.isFinite(total) ? Math.round(total) : 0} kcal</b>
          </div>
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="food-sheet-actions">
          {entry && <button type="button" className="btn ghost danger" onClick={remove} disabled={busy}>Delete</button>}
          <button type="button" className="btn ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn primary" disabled={busy}>{entry ? "Save" : "Add"}</button>
        </div>
      </form>
    </div>
  );
}
