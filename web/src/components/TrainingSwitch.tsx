import { NavLink } from "react-router-dom";

/** On phones, Training and Strength share one tab; this switches between them. */
export default function TrainingSwitch() {
  return (
    <nav className="segmented phone-only" aria-label="Training views">
      <NavLink to="/training">Running</NavLink>
      <NavLink to="/strength">Strength</NavLink>
    </nav>
  );
}
