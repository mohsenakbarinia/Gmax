import React, { useEffect, useState } from 'react';
import {
  Award,
  Crosshair,
  Maximize2,
  RotateCw,
  Settings,
  Sparkles,
  Sun
} from 'lucide-react';
import {
  CareerStats,
  GameSettings,
  GraphicsQuality,
  HUNTING_CONTRACTS,
  HudState,
  QUALITY_PROFILES,
  WEATHER_PRESETS,
  WeatherPreset
} from '../types';

interface StartScreenProps {
  hud: HudState;
  settings: GameSettings;
  stats: CareerStats;
  onPlayFullscreen: () => void;
  onSelectWeather: (w: WeatherPreset) => void;
  onSelectQuality: (q: GraphicsQuality) => void;
  onOpenTrophies: () => void;
  onOpenSettings: () => void;
}

export const StartScreen: React.FC<StartScreenProps> = ({
  hud,
  settings,
  stats,
  onPlayFullscreen,
  onSelectWeather,
  onSelectQuality,
  onOpenTrophies,
  onOpenSettings
}) => {
  const [isPortrait, setIsPortrait] = useState(
    typeof window !== 'undefined' ? window.innerHeight > window.innerWidth : false
  );
  const [dismissedPortrait, setDismissedPortrait] = useState(false);

  useEffect(() => {
    const check = () => setIsPortrait(window.innerHeight > window.innerWidth);
    window.addEventListener('resize', check);
    return () => window.removeEventListener('resize', check);
  }, []);

  const contract = HUNTING_CONTRACTS[stats.activeContractIndex % HUNTING_CONTRACTS.length];

  return (
    <div className="fixed inset-0 z-20 flex flex-col justify-between p-4 sm:p-6 bg-gradient-to-b from-[#08110d]/70 via-[#08110d]/25 to-[#08110d]/85 pointer-events-auto">
      {/* Top Bar */}
      <div className="flex items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 text-[#D9A05B] text-[10px] sm:text-xs font-mono-hud tracking-[0.26em] uppercase">
            <Sparkles className="w-3.5 h-3.5" />
            <span>ALPINE FOREST HUNTING SIMULATION</span>
          </div>
          <h1 className="font-display text-3xl sm:text-5xl font-bold tracking-[0.14em] text-[#F2F4F0] drop-shadow-lg mt-1">
            WILDWOOD: <span className="text-[#D9A05B]">REALITY</span>
          </h1>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={onOpenTrophies}
            className="flex items-center gap-2 px-3.5 py-2.5 rounded-xl bg-[#0D1B15]/85 border border-[#D9A05B]/35 text-[#E6E9E5] hover:bg-[#173025] active:scale-95 transition text-xs font-semibold backdrop-blur-md"
          >
            <Award className="w-4 h-4 text-[#D9A05B]" />
            <span className="hidden sm:inline">Trophies</span>
            <span className="font-mono-hud text-[#D9A05B]">{stats.totalHarvests}</span>
          </button>
          <button
            onClick={onOpenSettings}
            aria-label="Open Field Settings"
            className="p-2.5 rounded-xl bg-[#0D1B15]/85 border border-white/15 text-[#E6E9E5] hover:bg-[#173025] active:scale-95 transition backdrop-blur-md"
          >
            <Settings className="w-5 h-5" />
          </button>
        </div>
      </div>

      {/* Portrait Rotate Hint */}
      {isPortrait && !dismissedPortrait && (
        <div className="mx-auto max-w-sm rounded-2xl bg-[#0D1B15]/92 border border-[#D9A05B]/45 px-4 py-3 text-center backdrop-blur-md shadow-xl">
          <div className="flex items-center justify-center gap-2 text-[#D9A05B] text-xs font-semibold">
            <RotateCw className="w-4 h-4" />
            <span>Rotate to Landscape for Best Experience</span>
          </div>
          <button
            onClick={() => setDismissedPortrait(true)}
            className="mt-1.5 text-[11px] text-[#9EB0A6] underline"
          >
            Continue Anyway
          </button>
        </div>
      )}

      {/* Center-Bottom Controls & Presets */}
      <div className="grid grid-cols-1 md:grid-cols-12 gap-3 items-end">
        {/* Weather & Quality Quick Presets */}
        <div className="md:col-span-7 flex flex-col gap-2.5 bg-[#0A1611]/78 border border-white/10 rounded-2xl p-3.5 backdrop-blur-md">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-1.5 text-[11px] font-mono-hud uppercase tracking-wider text-[#9EB0A6]">
              <Sun className="w-3.5 h-3.5 text-[#D9A05B]" />
              Atmosphere & Time
            </span>
            <span className="text-[10px] font-mono-hud text-[#D9A05B]">
              {QUALITY_PROFILES[settings.graphicsQuality].label}
            </span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
            {WEATHER_PRESETS.map((w) => {
              const active = settings.weatherPreset === w.id;
              return (
                <button
                  key={w.id}
                  onClick={() => onSelectWeather(w.id)}
                  className={`px-2.5 py-2 rounded-xl text-left transition border text-xs ${
                    active
                      ? 'bg-[#D9A05B]/20 border-[#D9A05B] text-[#F5EFE6]'
                      : 'bg-black/30 border-white/10 text-[#B4C2BA] hover:bg-white/5'
                  }`}
                >
                  <div className="font-semibold truncate">{w.label}</div>
                  <div className="text-[10px] opacity-70 truncate">{w.subtitle}</div>
                </button>
              );
            })}
          </div>
          <div className="flex flex-wrap gap-1.5 pt-0.5">
            {(['BALANCED', 'ULTRA_REALISTIC', 'PHOTO_MAX'] as GraphicsQuality[]).map((qId) => {
              const active = settings.graphicsQuality === qId;
              return (
                <button
                  key={qId}
                  onClick={() => onSelectQuality(qId)}
                  className={`px-3 py-1 rounded-full text-[11px] font-medium transition border ${
                    active
                      ? 'bg-[#2C543E] border-[#6CB288] text-white'
                      : 'bg-black/30 border-white/10 text-[#9EB0A6] hover:bg-white/5'
                  }`}
                >
                  {QUALITY_PROFILES[qId].label}
                </button>
              );
            })}
          </div>
        </div>

        {/* Active Contract + Play CTA */}
        <div className="md:col-span-5 flex flex-col gap-2.5">
          <div className="rounded-2xl bg-[#0A1611]/80 border border-[#D9A05B]/30 px-3.5 py-2.5 backdrop-blur-md">
            <div className="flex items-center justify-between text-[10px] font-mono-hud uppercase tracking-wider text-[#D9A05B]">
              <span className="flex items-center gap-1.5">
                <Crosshair className="w-3.5 h-3.5" />
                Active Contract
              </span>
              <span>#{contract.id + 1}</span>
            </div>
            <div className="text-xs font-semibold text-[#F2F4F0] mt-0.5">{contract.title}</div>
            <div className="text-[11px] text-[#A8B8AF] leading-snug">{contract.description}</div>
          </div>

          {!hud.texturesReady && (
            <div className="rounded-xl bg-[#0A1611]/85 border border-white/10 px-3.5 py-2 backdrop-blur-md">
              <div className="flex justify-between text-[10px] font-mono-hud text-[#A8B8AF] mb-1">
                <span>Synthesizing 512px PBR Materials</span>
                <span>{Math.round(hud.textureProgress * 100)}%</span>
              </div>
              <div className="h-1.5 w-full rounded-full bg-white/10 overflow-hidden">
                <div
                  className="h-full bg-gradient-to-r from-[#D9A05B] to-[#6CB288] transition-all duration-200"
                  style={{ width: `${Math.round(hud.textureProgress * 100)}%` }}
                />
              </div>
            </div>
          )}

          <button
            onClick={onPlayFullscreen}
            className="w-full flex items-center justify-center gap-2.5 py-3.5 px-6 rounded-2xl bg-gradient-to-r from-[#D9A05B] to-[#B97B36] text-[#0A120F] font-bold tracking-[0.14em] text-sm shadow-lg shadow-[#D9A05B]/25 hover:brightness-110 active:scale-[0.98] transition"
          >
            <Maximize2 className="w-4 h-4" />
            <span>PLAY FULLSCREEN</span>
          </button>
        </div>
      </div>
    </div>
  );
};
