import React from 'react';
import { Award, Compass, Eye, Settings, Volume2, Wind } from 'lucide-react';
import { CareerStats, GameSettings, HUNTING_CONTRACTS, HudState } from '../types';

interface HudOverlayProps {
  hud: HudState;
  settings: GameSettings;
  stats: CareerStats;
  onOpenSettings: () => void;
  onOpenTrophies: () => void;
}

function cardinalLabel(deg: number): string {
  const dirs = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
  return dirs[Math.round(deg / 45) % 8];
}

export const HudOverlay: React.FC<HudOverlayProps> = ({
  hud,
  settings,
  stats,
  onOpenSettings,
  onOpenTrophies
}) => {
  const activeContract = HUNTING_CONTRACTS[stats.activeContractIndex % HUNTING_CONTRACTS.length];

  return (
    <div className="fixed inset-0 z-30 pointer-events-none p-3 sm:p-4 flex flex-col justify-between">
      {/* Top Bar: Contract, Compass + Wind, Settings/Trophy */}
      <div className="flex items-start justify-between gap-2">
        {/* Top-Left: Active Contract + Stealth Telemetry */}
        <div className="flex flex-col gap-1.5 max-w-[200px] sm:max-w-[250px]">
          <div className="rounded-xl bg-[#0A1611]/75 border border-white/12 px-2.5 py-1.5 backdrop-blur-md">
            <div className="text-[9px] font-mono-hud uppercase tracking-wider text-[#D9A05B]">
              Contract • {activeContract.title}
            </div>
            <div className="text-[11px] text-[#DCE3DF] leading-tight line-clamp-2">
              {activeContract.description}
            </div>
          </div>

          <div className="flex items-center gap-2 rounded-xl bg-[#0A1611]/72 border border-white/10 px-2.5 py-1.5 backdrop-blur-md">
            <div className="flex items-center gap-1.5 flex-1">
              <Eye className="w-3.5 h-3.5 text-[#8EC9A6]" />
              <div className="h-1.5 flex-1 rounded-full bg-white/10 overflow-hidden">
                <div
                  className="h-full bg-[#8EC9A6] transition-all"
                  style={{ width: `${Math.round(hud.stealthVisibility * 100)}%` }}
                />
              </div>
            </div>
            <div className="flex items-center gap-1.5 flex-1">
              <Volume2 className="w-3.5 h-3.5 text-[#D9A05B]" />
              <div className="h-1.5 flex-1 rounded-full bg-white/10 overflow-hidden">
                <div
                  className="h-full bg-[#D9A05B] transition-all"
                  style={{ width: `${Math.round(hud.stealthNoise * 100)}%` }}
                />
              </div>
            </div>
          </div>
        </div>

        {/* Top-Center: Tactical Compass & Wind Cone */}
        <div className="flex flex-col items-center gap-1">
          <div className="relative flex items-center gap-3 px-4 py-1.5 rounded-full bg-[#0A1611]/80 border border-white/15 backdrop-blur-md font-mono-hud text-xs">
            <Compass className="w-3.5 h-3.5 text-[#D9A05B]" />
            <span className="font-bold text-white w-14 text-center">
              {cardinalLabel(hud.headingDeg)} {Math.round(hud.headingDeg)}°
            </span>
            <span className="opacity-30">|</span>
            <div className="flex items-center gap-1 text-[#7DE8C1]">
              <Wind
                className="w-3.5 h-3.5 transition-transform"
                style={{ transform: `rotate(${hud.windDirectionDeg - hud.headingDeg}deg)` }}
              />
              <span>{hud.windSpeedMps.toFixed(1)} m/s</span>
            </div>
            {settings.showFpsCounter && (
              <>
                <span className="opacity-30">|</span>
                <span className="text-[#D9A05B]">{hud.fps} FPS</span>
              </>
            )}
          </div>

          {/* Wildlife Bearing Pills */}
          {hud.compassMarkers.length > 0 && (
            <div className="flex items-center gap-1.5">
              {hud.compassMarkers.slice(0, 3).map((m, idx) => (
                <span
                  key={idx}
                  className={`px-2 py-0.5 rounded-full text-[10px] font-mono-hud border backdrop-blur-md ${
                    m.kind === 'BLOOD'
                      ? 'bg-red-950/80 border-red-400/50 text-red-200'
                      : m.kind === 'ANIMAL'
                        ? 'bg-amber-950/80 border-[#D9A05B]/60 text-[#FFE1B3]'
                        : 'bg-[#0D2B26]/80 border-[#5CE6B0]/50 text-[#A8FFF0]'
                  }`}
                >
                  {cardinalLabel(m.bearingDeg)} {m.distanceMeters}m
                </span>
              ))}
            </div>
          )}
        </div>

        {/* Top-Right: Ammo + Trophies + Settings */}
        <div className="flex items-center gap-1.5 pointer-events-auto">
          <div className="hidden sm:flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#0A1611]/80 border border-white/12 font-mono-hud text-xs backdrop-blur-md">
            <span className="text-[#D9A05B] font-bold">
              {hud.activeWeapon === 'RIFLE_308' ? '.308' : 'BOW'}
            </span>
            <span className="text-white">
              {hud.activeWeapon === 'RIFLE_308'
                ? hud.reloading
                  ? 'RELOAD'
                  : `${hud.rifleAmmo}/${hud.rifleMagSize}`
                : '∞'}
            </span>
          </div>

          <button
            onClick={onOpenTrophies}
            aria-label="Open Trophy Log"
            className="p-2.5 rounded-xl bg-[#0A1611]/80 border border-[#D9A05B]/35 text-[#D9A05B] hover:bg-[#173025] active:scale-95 transition backdrop-blur-md"
          >
            <Award className="w-4 h-4" />
          </button>

          <button
            onClick={onOpenSettings}
            aria-label="Open Settings"
            className="p-2.5 rounded-xl bg-[#0A1611]/80 border border-white/15 text-[#E6E9E5] hover:bg-[#173025] active:scale-95 transition backdrop-blur-md"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Subtle Hip-Fire Center Dot (when not scoped) */}
      {!hud.scoped && hud.gameMode === 'PLAYING' && (
        <div className="fixed left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 pointer-events-none">
          <div className="w-1.5 h-1.5 rounded-full bg-white/75 shadow-[0_0_6px_rgba(0,0,0,0.85)]" />
        </div>
      )}

      {/* Center Status Alerts & Vital Trophy Banner */}
      <div className="fixed left-1/2 top-20 -translate-x-1/2 flex flex-col items-center gap-2 pointer-events-none">
        {hud.statusAlert && (
          <div className="px-4 py-1.5 rounded-full bg-red-950/85 border border-red-400/50 text-xs font-semibold text-red-100 backdrop-blur-md shadow-lg">
            {hud.statusAlert}
          </div>
        )}

        {hud.trophyBanner && (
          <div className="px-5 py-3 rounded-2xl bg-[#0B1913]/92 border border-[#D9A05B] text-center shadow-2xl backdrop-blur-md">
            <div className="font-display text-sm sm:text-base font-bold tracking-wider text-[#D9A05B]">
              {hud.trophyBanner.headline}
            </div>
            <div className="text-xs text-[#E6E9E5] mt-0.5">{hud.trophyBanner.subline}</div>
            {hud.trophyBanner.contractCompleted && (
              <div className="mt-1 inline-block px-2.5 py-0.5 rounded-full bg-[#2C6B5B] text-[10px] font-mono-hud text-[#B8FFE8]">
                CONTRACT COMPLETED!
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
