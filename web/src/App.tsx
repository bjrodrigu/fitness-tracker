import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import JournalPage from "./pages/JournalPage";
import EntryPage from "./pages/EntryPage";
import TrainingPage from "./pages/TrainingPage";
import StrengthPage from "./pages/StrengthPage";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/training" replace />} />
        <Route path="/training" element={<TrainingPage />} />
        <Route path="/journal" element={<JournalPage />} />
        <Route path="/strength" element={<StrengthPage />} />
        <Route path="/entries/new" element={<EntryPage />} />
        <Route path="/entries/:id" element={<EntryPage />} />
        <Route path="*" element={<p className="state">There's no page here. Use the links on the left.</p>} />
      </Route>
    </Routes>
  );
}
