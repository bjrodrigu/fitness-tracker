import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  ResponsiveContainer,
  Scatter,
  ComposedChart,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { LiftSession, Week } from "../lib/types";
import { formatLb, formatPace, shortDate } from "../lib/format";

const axis = { stroke: "var(--ink-3)", fontSize: 12, tickLine: false, axisLine: false } as const;

function Tip({ title, lines }: { title: string; lines: string[] }) {
  return (
    <div className="chart-tip">
      <b>{title}</b>
      {lines.map((l) => (
        <div key={l}>{l}</div>
      ))}
    </div>
  );
}

export function WeeklyMileageChart({ weeks }: { weeks: Week[] }) {
  const data = weeks.map((w, i) => ({ ...w, label: shortDate(w.weekStart), current: i === weeks.length - 1 }));
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={data} margin={{ top: 4, right: 0, bottom: 0, left: -18 }}>
        <CartesianGrid vertical={false} stroke="var(--rule)" />
        <XAxis dataKey="label" {...axis} interval="preserveStartEnd" minTickGap={18} />
        <YAxis {...axis} allowDecimals={false} />
        <Tooltip
          cursor={{ fill: "var(--rule)", opacity: 0.6 }}
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const w = payload[0].payload as Week & { current: boolean };
            return (
              <Tip
                title={`Week of ${shortDate(w.weekStart)}${w.current ? " (so far)" : ""}`}
                lines={[`${w.runMiles} mi in ${w.runs} runs`, `Long run ${w.longRunMi} mi`]}
              />
            );
          }}
        />
        <Bar dataKey="runMiles" radius={[3, 3, 0, 0]} maxBarSize={28} isAnimationActive={false}>
          {data.map((d) => (
            <Cell key={d.weekStart} fill="var(--run)" fillOpacity={d.current ? 0.45 : 1} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

export function PaceTrendChart({ weeks }: { weeks: Week[] }) {
  const data = weeks
    .filter((w) => w.avgPaceSecPerMi != null)
    .map((w) => ({ ...w, label: shortDate(w.weekStart) }));
  const paces = data.map((d) => d.avgPaceSecPerMi!);
  const step = Math.max(...paces) - Math.min(...paces) > 60 ? 30 : 15;
  const lo = Math.floor((Math.min(...paces) - 5) / step) * step;
  const hi = Math.ceil((Math.max(...paces) + 5) / step) * step;
  const ticks = Array.from({ length: (hi - lo) / step + 1 }, (_, i) => lo + i * step);
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
        <CartesianGrid vertical={false} stroke="var(--rule)" />
        <XAxis dataKey="label" {...axis} interval="preserveStartEnd" minTickGap={18} />
        {/* Reversed so that faster (smaller) paces sit higher. */}
        <YAxis {...axis} reversed domain={[lo, hi]} ticks={ticks} tickFormatter={(v) => formatPace(v)} />
        <Tooltip
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const w = payload[0].payload as Week;
            return <Tip title={`Week of ${shortDate(w.weekStart)}`} lines={[`${formatPace(w.avgPaceSecPerMi)} /mi average`, `${w.runMiles} mi`]} />;
          }}
        />
        <Line dataKey="avgPaceSecPerMi" isAnimationActive={false} stroke="var(--run)" strokeWidth={2} dot={{ r: 2.5, fill: "var(--run)" }} activeDot={{ r: 4 }} />
      </LineChart>
    </ResponsiveContainer>
  );
}

export function LiftChart({ sessions, height = 260 }: { sessions: LiftSession[]; height?: number }) {
  const data = sessions.map((s) => ({ ...s, t: new Date(s.date + "T00:00:00").getTime() }));
  const vals = data.flatMap((d) => [d.e1rmLb, d.topWeightLb]);
  const lo = Math.floor((Math.min(...vals) - 10) / 10) * 10;
  const hi = Math.ceil((Math.max(...vals) + 10) / 10) * 10;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <ComposedChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -12 }}>
        <CartesianGrid vertical={false} stroke="var(--rule)" />
        <XAxis
          dataKey="t"
          type="number"
          scale="time"
          domain={["dataMin", "dataMax"]}
          {...axis}
          tickFormatter={(t) => new Date(t).toLocaleDateString("en-US", { month: "short", day: "numeric" })}
          minTickGap={24}
        />
        <YAxis {...axis} domain={[lo, hi]} />
        <Tooltip
          content={({ active, payload }) => {
            if (!active || !payload?.length) return null;
            const s = payload[0].payload as LiftSession;
            return <Tip title={shortDate(s.date)} lines={[`Top set ${s.topReps} × ${formatLb(s.topWeightLb)} lb`, `Estimated max ${formatLb(s.e1rmLb)} lb`]} />;
          }}
        />
        <Scatter isAnimationActive={false} dataKey="topWeightLb" fill="var(--ink-3)" shape="circle" legendType="none" />
        <Line isAnimationActive={false} dataKey="e1rmLb" stroke="var(--lift)" strokeWidth={2} dot={false} activeDot={{ r: 4 }} />
      </ComposedChart>
    </ResponsiveContainer>
  );
}

export function Sparkline({ values, color = "var(--lift)" }: { values: number[]; color?: string }) {
  const data = values.map((v, i) => ({ i, v }));
  return (
    <ResponsiveContainer width="100%" height={28}>
      <LineChart data={data} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
        <YAxis hide domain={["dataMin", "dataMax"]} />
        <Line dataKey="v" stroke={color} strokeWidth={1.5} dot={false} isAnimationActive={false} />
      </LineChart>
    </ResponsiveContainer>
  );
}
