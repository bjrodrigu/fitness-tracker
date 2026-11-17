import type { Meal } from "./types";
import { toIsoDate } from "./format";

export const mealName = (m: Meal) => ({ BREAKFAST: "Breakfast", LUNCH: "Lunch", DINNER: "Dinner", SNACK: "Snacks" })[m];

/** Guess the meal from the time of day, for the quick "Log food" button. */
export function mealForNow(d = new Date()): Meal {
  const h = d.getHours();
  if (h < 10) return "BREAKFAST";
  if (h < 15) return "LUNCH";
  if (h < 21) return "DINNER";
  return "SNACK";
}

export const todayIso = () => toIsoDate(new Date());

export function addDays(iso: string, n: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  return toIsoDate(new Date(y, m - 1, d + n));
}

export const kcal = (n: number) => Math.round(n).toLocaleString("en-US");
export const grams = (n: number) => `${Math.round(n)}`;
