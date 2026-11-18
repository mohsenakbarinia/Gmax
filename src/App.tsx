import React, { useEffect, useRef, useState } from 'react';
import { HudOverlay } from './components/HudOverlay';
import { MobileControls } from './components/MobileControls';
import { SettingsModal, TrophyModal } from './components/Modals';
import { OpticalScope } from './components/OpticalScope';
import { StartScreen } from './components/StartScreen';
import { GameEngine } from './engine/GameEngine';
import {
  CareerStats,
  DEFAULT_SETTINGS,
  DEFAULT_STATS,
  GameSettings,
  HudState,
  INITIAL_HUD,
  TrophyRecord
} from './types';

export const App: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const engineRef = useRef<GameEngine | null>(null);

  const [hud, setHud] = useState<HudState>(INITIAL_HUD);
  const [settings, setSettings] = useState<GameSettings>(DEFAULT_SETTINGS);
  const [stats, setStats] = useState<CareerStats>(DEFAULT_STATS);
  const [trophies, setTrophies] = useState<TrophyRecord[]>([]);
  const [showSettings, setShowSettings] = useState(false);
  const [showTrophies, setShowTrophies] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const engine = new GameEngine(
      canvas,
      (nextHud) => setHud(nextHud),
      (nextStats, nextTrophies) => {
        setStats(nextStats);
        setTrophies(nextTrophies);
      }
    );
    engineRef.current = engine;
    setSettings(engine.settings);
    setStats(engine.stats);
    setTrophies(engine.trophies);

    return () => {
      engine.dispose();
      engineRef.current = null;
    };
  }, []);

  const updateSettings = (next: GameSettings) => {
    setSettings(next);
    engineRef.current?.applySettings(next);
  };

  const requestFullscreenLandscape = async () => {
    try {
      if (!document.fullscreenElement && document.documentElement.requestFullscreen) {
        await document.documentElement.requestFullscreen();
      }
      const orient = screen.orientation as unknown as { lock?: (o: string) => Promise<void> };
      if (orient?.lock) {
        await orient.lock('landscape').catch(() => undefined);
      }
    } catch {
      // Safe fallback when fullscreen/orientation lock is blocked by browser
    }
    engineRef.current?.startHunt();
  };

  return (
    <div className="relative w-full h-full overflow-hidden bg-[#0A120F]">
      {/* Persistent WebGL2 3D Alpine Valley Canvas */}
      <canvas ref={canvasRef} className="block w-full h-full" />

      {/* Title Screen over live 3D valley orbit */}
      {hud.gameMode === 'TITLE_ORBIT' && (
        <StartScreen
          hud={hud}
          settings={settings}
          stats={stats}
          onPlayFullscreen={() => void requestFullscreenLandscape()}
          onSelectWeather={(w) => updateSettings({ ...settings, weatherPreset: w })}
          onSelectQuality={(q) => updateSettings({ ...settings, graphicsQuality: q })}
          onOpenTrophies={() => setShowTrophies(true)}
          onOpenSettings={() => setShowSettings(true)}
        />
      )}

      {/* Active Hunt Overlays */}
      {hud.gameMode !== 'TITLE_ORBIT' && (
        <>
          {hud.scoped && hud.activeWeapon === 'RIFLE_308' && <OpticalScope hud={hud} />}

          {hud.gameMode === 'PLAYING' && engineRef.current && (
            <MobileControls hud={hud} settings={settings} engine={engineRef.current} />
          )}

          <HudOverlay
            hud={hud}
            settings={settings}
            stats={stats}
            onOpenSettings={() => {
              engineRef.current?.pauseHunt();
              setShowSettings(true);
            }}
            onOpenTrophies={() => {
              engineRef.current?.pauseHunt();
              setShowTrophies(true);
            }}
          />
        </>
      )}

      {/* Modals */}
      {showSettings && (
        <SettingsModal
          settings={settings}
          gameMode={hud.gameMode}
          onUpdate={updateSettings}
          onResume={() => {
            setShowSettings(false);
            engineRef.current?.resumeHunt();
          }}
          onRestart={() => {
            setShowSettings(false);
            engineRef.current?.resetValley(false);
          }}
          onReturnTitle={() => {
            setShowSettings(false);
            engineRef.current?.returnToTitle();
          }}
          onClose={() => {
            setShowSettings(false);
            if (hud.gameMode === 'PAUSED') engineRef.current?.resumeHunt();
          }}
        />
      )}

      {showTrophies && (
        <TrophyModal
          stats={stats}
          trophies={trophies}
          onDeleteTrophy={(id) => engineRef.current?.deleteTrophy(id)}
          onClose={() => {
            setShowTrophies(false);
            if (hud.gameMode === 'PAUSED') engineRef.current?.resumeHunt();
          }}
        />
      )}
    </div>
  );
};

export default App;
