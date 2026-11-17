export type ActivityType = "RUN" | "STRENGTH";

export interface ExerciseSet {
  reps: number;
  weightLb: number;
}

export interface Exercise {
  name: string;
  sets: ExerciseSet[];
}

export interface Activity {
  id?: string;
  type: ActivityType;
  date: string; // yyyy-mm-dd
  startTime?: string | null; // HH:mm[:ss]
  title: string;
  notes?: string | null;
  effort?: number | null;
  durationSec?: number | null;
  distanceMi?: number | null;
  avgHeartRate?: number | null;
  tags: string[];
  exercises: Exercise[];
  source?: string;
  paceSecPerMi?: number | null;
  volumeLb?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface Week {
  weekStart: string;
  runMiles: number;
  runs: number;
  runSec: number;
  avgPaceSecPerMi: number | null;
  longRunMi: number;
  strengthSessions: number;
  strengthVolumeLb: number;
}

export interface RunRef {
  id: string;
  date: string;
  title: string;
  distanceMi: number;
  durationSec: number;
}

export interface BestEffort {
  label: string;
  distanceMi: number;
  timeSec: number;
  paceSecPerMi: number;
  run: RunRef;
}

export interface LiftSession {
  activityId: string;
  date: string;
  topWeightLb: number;
  topReps: number;
  e1rmLb: number;
}

export interface LiftProgress {
  name: string;
  bodyweight: boolean;
  sessions: LiftSession[];
  bestE1rmLb: number;
  latestE1rmLb: number;
  changeLast8WeeksLb: number;
  bestReps: number;
}

export interface Day {
  date: string;
  runMiles: number;
  strength: boolean;
}

export interface Stats {
  asOf: string;
  summary: {
    weekMiles: number;
    lastWeekMiles: number;
    avgMilesLast4Weeks: number;
    yearMiles: number;
    yearRuns: number;
    weekStrengthSessions: number;
    activeWeekStreak: number;
  };
  weeks: Week[];
  bestEfforts: BestEffort[];
  longestRun: RunRef | null;
  lifts: LiftProgress[];
  days: Day[];
}

export interface Me {
  id: string;
  email: string;
  name: string;
  /** The shared, read-only sample account. */
  demo: boolean;
}

export interface AuthConfig {
  demo: boolean;
}

export type GarminSyncStatus = "OK" | "NEEDS_RELINK" | "FAILED";

export interface GarminStatus {
  /** False when the server has no GARMIN_TOKEN_KEY. */
  available: boolean;
  linked: boolean;
  linkedAt: string | null;
  lastSyncAt: string | null;
  lastSyncStatus: GarminSyncStatus | null;
  lastSyncMessage: string | null;
  lastSyncNew: number | null;
}

export interface GarminLinkResponse {
  needsCode: boolean;
  status: GarminStatus;
}

export type Meal = "BREAKFAST" | "LUNCH" | "DINNER" | "SNACK";
export const MEALS: Meal[] = ["BREAKFAST", "LUNCH", "DINNER", "SNACK"];

export interface FoodEntry {
  id?: string;
  date: string;
  meal: Meal;
  name: string;
  /** Nutrition below is per serving. */
  servings: number;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  totalCalories?: number;
  totalProteinG?: number;
  totalCarbsG?: number;
  totalFatG?: number;
}

export type Direction = "LOSE" | "MAINTAIN" | "GAIN";

export interface NutritionGoal {
  direction: Direction;
  targetWeightLb: number | null;
  weeklyRateLb: number | null;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  startDate?: string | null;
}

export interface NutritionDay {
  date: string;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  entries: number;
}

export interface WeightPoint {
  date: string;
  weightLb: number;
  trendLb: number;
}

export interface NutritionReport {
  asOf: string;
  goal: NutritionGoal | null;
  days: NutritionDay[];
  weights: WeightPoint[];
  summary: {
    currentLb: number | null;
    startLb: number | null;
    changeLb: number | null;
    weeklyRateLb: number | null;
    targetLb: number | null;
    remainingLb: number | null;
    projectedDate: string | null;
    avgCalories7: number | null;
    daysLogged7: number;
  };
}

export type Sex = "FEMALE" | "MALE";
export type ActivityLevel = "SEDENTARY" | "LIGHT" | "MODERATE" | "VERY";

export interface GoalProfile {
  sex: Sex;
  age: number;
  heightIn: number;
  weightLb: number;
  activity: ActivityLevel;
  direction: Direction;
  weeklyRateLb: number | null;
}

export interface GoalSuggestion {
  maintenanceCalories: number;
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
  weeklyRateLb: number;
  notes: string[];
}
