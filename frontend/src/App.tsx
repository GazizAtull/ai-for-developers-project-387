import { useState } from 'react';
import GuestPage from './GuestPage';
import HomePage from './HomePage';
import OwnerPage from './OwnerPage';

export type View = 'home' | 'guest' | 'owner';

const TABS: { view: View; label: string }[] = [
  { view: 'home', label: 'Главная' },
  { view: 'guest', label: 'Запись' },
  { view: 'owner', label: 'Владелец' },
];

export default function App({ initialView = 'home' }: { initialView?: View }) {
  const [view, setView] = useState<View>(initialView);
  return (
    <div className="app">
      <header className="header">
        <h1>Запись на звонок</h1>
        <nav className="tabs">
          {TABS.map((t) => (
            <button key={t.view} className={view === t.view ? 'tab active' : 'tab'} onClick={() => setView(t.view)}>
              {t.label}
            </button>
          ))}
        </nav>
      </header>
      <main>
        {view === 'home' && <HomePage onBook={() => setView('guest')} />}
        {view === 'guest' && <GuestPage />}
        {view === 'owner' && <OwnerPage />}
      </main>
    </div>
  );
}
