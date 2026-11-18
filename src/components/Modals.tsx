import React from 'react';
import { Award, Trash2, X } from 'lucide-react';
import {
  CareerStats,
  GameMode,
  GameSettings,
  GraphicsQuality,
  GyroMode,
  HIT_ZONE_LABEL,
  HUNTING_CONTRACTS,
  QUALITY_PROFILES,
  SPECIES_LABEL,
  TrophyRecord,
  WEATHER_PRESETS,
  WeatherPreset
} from '../types';

interface SettingsModalProps {
  settings: GameSettings;
  gameMode: GameMode;
  onUpdate: (next: GameSettings) => void;
  onResume: () => void;
  onRestart: () => void;
  onReturnTitle: () => void;
  onClose: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  settings,
  gameMode,
  onUpdate,
  onResume,
  onRestart,
  onReturnTitle,
  onClose
}) => {
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 pointer-events-auto">
      <div className="w-full max-w-lg max-h-[88vh] overflow-y-auto rounded-3xl bg-[#0D1B15] border border-[#D9A05B]/35 p-5 shadow-2xl text-[#E6E9E5]">
        <div className="flex items-center justify-between pb-3 border-b border-white/10">
          <h2 className="font-display text-lg font-bold tracking-wider text-[#D9A05B]">
            FIELD SETTINGS
          </h2>
          <button
            onClick={onClose}
            aria-label="Close Settings"
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-4 py-4 text-xs">
          {/* Weather Preset */}
          <div>
            <div className="font-mono-hud text-[10px] uppercase tracking-wider text-[#9EB0A6] mb-1.5">
              Weather & Time of Day
            </div>
            <div className="grid grid-cols-2 gap-1.5">
              {WEATHER_PRESETS.map((w) => (
                <button
                  key={w.id}
                  onClick={() => onUpdate({ ...settings, weatherPreset: w.id as WeatherPreset })}
                  className={`px-3 py-2 rounded-xl border text-left transition ${
                    settings.weatherPreset === w.id
                      ? 'bg-[#D9A05B]/20 border-[#D9A05B] text-white'
                      : 'bg-black/30 border-white/10 text-[#A8B8AF]'
                  }`}
                >
                  <div className="font-semibold">{w.label}</div>
                </button>
              ))}
            </div>
          </div>

          {/* Graphics Quality */}
          <div>
            <div className="font-mono-hud text-[10px] uppercase tracking-wider text-[#9EB0A6] mb-1.5">
              Graphics Quality
            </div>
            <div className="grid grid-cols-3 gap-1.5">
              {(['BALANCED', 'ULTRA_REALISTIC', 'PHOTO_MAX'] as GraphicsQuality[]).map((q) => (
                <button
                  key={q}
                  onClick={() => onUpdate({ ...settings, graphicsQuality: q })}
                  className={`px-2 py-2 rounded-xl border text-center transition ${
                    settings.graphicsQuality === q
                      ? 'bg-[#2C543E] border-[#6CB288] text-white'
                      : 'bg-black/30 border-white/10 text-[#A8B8AF]'
                  }`}
                >
                  {QUALITY_PROFILES[q].label}
                </button>
              ))}
            </div>
          </div>

          {/* Gyroscope Mode */}
          <div>
            <div className="font-mono-hud text-[10px] uppercase tracking-wider text-[#9EB0A6] mb-1.5">
              Gyroscope Fine Aim
            </div>
            <div className="grid grid-cols-3 gap-1.5">
              {(['OFF', 'SCOPE_ONLY', 'ALWAYS_ON'] as GyroMode[]).map((g) => (
                <button
                  key={g}
                  onClick={() => onUpdate({ ...settings, gyroMode: g })}
                  className={`px-2 py-2 rounded-xl border text-center transition ${
                    settings.gyroMode === g
                      ? 'bg-[#D9A05B]/25 border-[#D9A05B] text-white'
                      : 'bg-black/30 border-white/10 text-[#A8B8AF]'
                  }`}
                >
                  {g === 'OFF' ? 'Off' : g === 'SCOPE_ONLY' ? 'Scope Only' : 'Always On'}
                </button>
              ))}
            </div>
          </div>

          {/* Sensitivity & Audio Sliders */}
          <div className="space-y-3 pt-1">
            <label className="block">
              <div className="flex justify-between text-[#B4C2BA] mb-1">
                <span>Look Sensitivity</span>
                <span className="font-mono-hud">{settings.normalSensitivity.toFixed(2)}x</span>
              </div>
              <input
                type="range"
                min={0.4}
                max={2.0}
                step={0.05}
                value={settings.normalSensitivity}
                onChange={(e) => onUpdate({ ...settings, normalSensitivity: parseFloat(e.target.value) })}
                className="w-full accent-[#D9A05B]"
              />
            </label>

            <label className="block">
              <div className="flex justify-between text-[#B4C2BA] mb-1">
                <span>Scoped ADS Sensitivity</span>
                <span className="font-mono-hud">{settings.adsSensitivity.toFixed(2)}x</span>
              </div>
              <input
                type="range"
                min={0.15}
                max={1.2}
                step={0.05}
                value={settings.adsSensitivity}
                onChange={(e) => onUpdate({ ...settings, adsSensitivity: parseFloat(e.target.value) })}
                className="w-full accent-[#D9A05B]"
              />
            </label>

            <label className="block">
              <div className="flex justify-between text-[#B4C2BA] mb-1">
                <span>Master Audio Volume</span>
                <span className="font-mono-hud">{Math.round(settings.masterVolume * 100)}%</span>
              </div>
              <input
                type="range"
                min={0}
                max={1}
                step={0.05}
                value={settings.masterVolume}
                onChange={(e) => onUpdate({ ...settings, masterVolume: parseFloat(e.target.value) })}
                className="w-full accent-[#D9A05B]"
              />
            </label>
          </div>

          {/* Toggles */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-1">
            {[
              {
                label: 'Left Fire Button',
                val: settings.leftFireButtonEnabled,
                toggle: () =>
                  onUpdate({ ...settings, leftFireButtonEnabled: !settings.leftFireButtonEnabled })
              },
              {
                label: 'Haptic Feedback',
                val: settings.hapticsEnabled,
                toggle: () => onUpdate({ ...settings, hapticsEnabled: !settings.hapticsEnabled })
              },
              {
                label: 'FPS Counter',
                val: settings.showFpsCounter,
                toggle: () => onUpdate({ ...settings, showFpsCounter: !settings.showFpsCounter })
              }
            ].map((item) => (
              <button
                key={item.label}
                onClick={item.toggle}
                className={`px-3 py-2 rounded-xl border text-center font-medium transition ${
                  item.val
                    ? 'bg-[#2C543E]/70 border-[#6CB288] text-white'
                    : 'bg-black/30 border-white/10 text-[#9EB0A6]'
                }`}
              >
                {item.label}: {item.val ? 'ON' : 'OFF'}
              </button>
            ))}
          </div>
        </div>

        {/* Session Actions */}
        {gameMode !== 'TITLE_ORBIT' && (
          <div className="flex flex-wrap gap-2 pt-3 border-t border-white/10">
            <button
              onClick={onResume}
              className="flex-1 py-2.5 rounded-xl bg-[#D9A05B] text-[#0A120F] font-bold text-xs"
            >
              Resume Hunt
            </button>
            <button
              onClick={onRestart}
              className="px-4 py-2.5 rounded-xl bg-white/10 text-white font-semibold text-xs"
            >
              Restart Valley
            </button>
            <button
              onClick={onReturnTitle}
              className="px-4 py-2.5 rounded-xl bg-white/5 border border-white/15 text-[#B4C2BA] text-xs"
            >
              Title Screen
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

interface TrophyModalProps {
  stats: CareerStats;
  trophies: TrophyRecord[];
  onDeleteTrophy: (id: string) => void;
  onClose: () => void;
}

export const TrophyModal: React.FC<TrophyModalProps> = ({
  stats,
  trophies,
  onDeleteTrophy,
  onClose
}) => {
  const activeContract = HUNTING_CONTRACTS[stats.activeContractIndex % HUNTING_CONTRACTS.length];

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 pointer-events-auto">
      <div className="w-full max-w-xl max-h-[88vh] flex flex-col rounded-3xl bg-[#0D1B15] border border-[#D9A05B]/35 p-5 shadow-2xl text-[#E6E9E5]">
        <div className="flex items-center justify-between pb-3 border-b border-white/10">
          <div className="flex items-center gap-2">
            <Award className="w-5 h-5 text-[#D9A05B]" />
            <h2 className="font-display text-lg font-bold tracking-wider text-[#D9A05B]">
              TROPHY LOG & CONTRACTS
            </h2>
          </div>
          <button
            onClick={onClose}
            aria-label="Close Trophy Log"
            className="p-2 rounded-xl bg-white/5 hover:bg-white/10 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Career Stat Tiles */}
        <div className="grid grid-cols-4 gap-2 my-3">
          {[
            { label: 'Harvests', val: stats.totalHarvests },
            { label: 'Gold', val: stats.goldTrophies },
            { label: 'Longest Vital', val: `${stats.longestVitalShotMeters}m` },
            { label: 'Contracts', val: stats.completedContractsCount }
          ].map((s) => (
            <div
              key={s.label}
              className="rounded-xl bg-black/35 border border-white/10 p-2.5 text-center"
            >
              <div className="font-mono-hud text-base font-bold text-[#D9A05B]">{s.val}</div>
              <div className="text-[10px] text-[#9EB0A6]">{s.label}</div>
            </div>
          ))}
        </div>

        {/* Active Contract */}
        <div className="rounded-2xl bg-[#142A20] border border-[#D9A05B]/35 p-3 mb-3">
          <div className="text-[10px] font-mono-hud uppercase text-[#D9A05B]">
            Active Contract • {activeContract.title}
          </div>
          <div className="text-xs text-[#E6E9E5] mt-0.5">{activeContract.description}</div>
        </div>

        {/* Trophy List */}
        <div className="flex-1 overflow-y-auto space-y-2 pr-1">
          {trophies.length === 0 ? (
            <div className="text-center py-8 text-xs text-[#9EB0A6]">
              No trophies logged yet. Stalk the valley upwind, steady your breath, and land a clean
              vital-organ shot.
            </div>
          ) : (
            trophies.map((t) => (
              <div
                key={t.id}
                className="flex items-center justify-between gap-2 rounded-xl bg-black/35 border border-white/10 px-3 py-2.5"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs font-bold text-white">{SPECIES_LABEL[t.species]}</span>
                    <span
                      className={`px-2 py-0.5 rounded-full text-[9px] font-mono-hud font-bold ${
                        t.rating === 'GOLD'
                          ? 'bg-[#D9A05B]/25 text-[#F5C67E]'
                          : t.rating === 'SILVER'
                            ? 'bg-slate-400/20 text-slate-200'
                            : 'bg-amber-900/40 text-amber-300'
                      }`}
                    >
                      {t.rating}
                    </span>
                  </div>
                  <div className="text-[11px] font-mono-hud text-[#9EB0A6] mt-0.5">
                    {HIT_ZONE_LABEL[t.hitZone]} • {t.distanceMeters}m •{' '}
                    {t.weapon === 'RIFLE_308' ? '.308 Rifle' : 'Recurve Bow'} • {t.scorePoints} pts
                  </div>
                </div>
                <button
                  onClick={() => onDeleteTrophy(t.id)}
                  aria-label="Delete Trophy"
                  className="p-2 rounded-lg text-white/40 hover:text-red-400 hover:bg-red-950/40 transition"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
