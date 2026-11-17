import { describe, expect, it } from "vitest";
import { e1rm, formatDuration, formatPace, parseDuration, summarizeSets, weekStartOf } from "./format";

describe("pace and duration", () => {
  it("formats pace as m:ss", () => {
    expect(formatPace(545)).toBe("9:05");
    expect(formatPace(null)).toBe("–");
  });

  it("formats durations with and without hours", () => {
    expect(formatDuration(1500)).toBe("25:00");
    expect(formatDuration(3725)).toBe("1:02:05");
  });

  it("parses minutes, m:ss and h:mm:ss", () => {
    expect(parseDuration("45")).toBe(2700);
    expect(parseDuration("45:30")).toBe(2730);
    expect(parseDuration("1:02:05")).toBe(3725);
    expect(parseDuration("1:75")).toBeNull();
    expect(parseDuration("abc")).toBeNull();
  });
});

describe("dates", () => {
  it("finds the Monday of a week", () => {
    expect(weekStartOf("2026-11-18")).toBe("2026-11-16"); // Wednesday
    expect(weekStartOf("2026-11-22")).toBe("2026-11-16"); // Sunday
    expect(weekStartOf("2026-11-16")).toBe("2026-11-16");
  });
});

describe("strength", () => {
  it("uses Epley for estimated max", () => {
    expect(e1rm(5, 150)).toBeCloseTo(175);
    expect(e1rm(1, 225)).toBe(225);
  });

  it("summarizes sets compactly", () => {
    expect(summarizeSets([{ reps: 5, weightLb: 185 }, { reps: 5, weightLb: 185 }, { reps: 5, weightLb: 185 }])).toBe("3 × 5 @ 185");
    expect(summarizeSets([{ reps: 5, weightLb: 185 }, { reps: 4, weightLb: 185 }])).toBe("5, 4 @ 185");
    expect(summarizeSets([{ reps: 8, weightLb: 0 }, { reps: 8, weightLb: 0 }])).toBe("2 × 8");
  });
});
