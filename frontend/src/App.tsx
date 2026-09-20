import { useState } from "react";
import { AcquisitionPanel } from "./components/AcquisitionPanel";
import { SettlementsExtract } from "./components/SettlementsExtract";

type Tab = "nova" | "extrato";

export default function App() {
  const [tab, setTab] = useState<Tab>("nova");

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>SRM Credit Engine</h1>
          <div className="subtitle">Mesa de operações — precificação e liquidação de recebíveis</div>
        </div>
        <nav className="tabs">
          <button className="tab-button" data-active={tab === "nova"} onClick={() => setTab("nova")}>
            Nova operação
          </button>
          <button className="tab-button" data-active={tab === "extrato"} onClick={() => setTab("extrato")}>
            Extrato
          </button>
        </nav>
      </header>

      {tab === "nova" ? <AcquisitionPanel /> : <SettlementsExtract />}
    </div>
  );
}
