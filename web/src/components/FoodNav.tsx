import { NavLink } from "react-router-dom";

/** Day / Progress / Goal switch at the top of the food pages. */
export default function FoodNav() {
  return (
    <nav className="segmented food-nav" aria-label="Food views">
      <NavLink to="/food" end>Day</NavLink>
      <NavLink to="/food/progress">Progress</NavLink>
      <NavLink to="/food/goal">Goal</NavLink>
    </nav>
  );
}
