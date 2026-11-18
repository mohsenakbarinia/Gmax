import React, { useRef, useState } from 'react';
import {
  Crosshair,
  Eye,
  Flashlight,
  RefreshCw,
  Repeat,
  User,
  Wind,
  X,
  ArrowUp
} from 'lucide-react';
import { GameEngine } from '../engine/GameEngine';
import { GameSettings, HudState } from '../types';

interface MobileControlsProps {
  hud: HudState;
  settings: GameSettings;
  engine: GameEngine;
}

export const MobileControls: React.FC<MobileControlsProps> = ({ hud, settings, engine }) => {
  // Multi-touch pointer isolation: joystick pointer vs look pointer
  const joyPointerId = useRef<number | null>(null);
  const joyOrigin = useRef({ x: 0, y: 0 });
  const [joyVisual, setJoyVisual] = useState<{
    active: boolean;
    ox: number;
    oy: number;
    kx: number;
    ky: number;
    sprint: boolean;
  }>({ active: false, ox: 0, oy: 0, kx: 0, ky: 0, sprint: false });

  const lookPointerId = useRef<number | null>(null);
  const lookLast = useRef({ x: 0, y: 0 });

  const onLeftDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (joyPointerId.current !== null) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    joyPointerId.current = e.pointerId;
    joyOrigin.current = { x: e.clientX, y: e.clientY };
    setJoyVisual({ active: true, ox: e.clientX, oy: e.clientY, kx: 0, ky: 0, sprint: false });
    engine.setJoystick(0, 0, false);
  };

  const onLeftMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (joyPointerId.current !== e.pointerId) return;
    const maxR = 56;
    const dx = e.clientX - joyOrigin.current.x;
    const dy = e.clientY - joyOrigin.current.y;
    const dist = Math.hypot(dx, dy);
    const clamped = Math.min(dist, maxR);
    const angle = Math.atan2(dy, dx);
    const kx = dist > 0.001 ? Math.cos(angle) * clamped : 0;
    const ky = dist > 0.001 ? Math.sin(angle) * clamped : 0;
    const nx = kx / maxR;
    const ny = -ky / maxR; // up is positive forward
    const sprintLock = ny > 0.85 && Math.abs(nx) < 0.45;
    setJoyVisual((prev) => ({ ...prev, kx, ky, sprint: sprintLock }));
    engine.setJoystick(nx, ny, sprintLock);
  };

  const onLeftUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (joyPointerId.current !== e.pointerId) return;
    joyPointerId.current = null;
    setJoyVisual((prev) => ({ ...prev, active: false, kx: 0, ky: 0, sprint: false }));
    engine.setJoystick(0, 0, false);
  };

  const onRightDown = (e: React.PointerEvent<HTMLDivElement>) => {
    if (lookPointerId.current !== null) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    lookPointerId.current = e.pointerId;
    lookLast.current = { x: e.clientX, y: e.clientY };
  };

  const onRightMove = (e: React.PointerEvent<HTMLDivElement>) => {
    if (lookPointerId.current !== e.pointerId) return;
    const dx = e.clientX - lookLast.current.x;
    const dy = e.clientY - lookLast.current.y;
    lookLast.current = { x: e.clientX, y: e.clientY };
    engine.addTouchLookDelta(dx, dy);
  };

  const onRightUp = (e: React.PointerEvent<HTMLDivElement>) => {
    if (lookPointerId.current !== e.pointerId) return;
    lookPointerId.current = null;
  };

  const bowArcDash = 2 * Math.PI * 30;
  const bowArcOffset = bowArcDash * (1 - hud.bowDrawTension);

  const renderFireButton = (side: 'right' | 'left') => (
    <button
      onPointerDown={(e) => {
        e.stopPropagation();
        engine.onFirePress();
      }}
      onPointerUp={(e) => {
        e.stopPropagation();
        engine.onFireRelease();
      }}
      onPointerCancel={(e) => {
        e.stopPropagation();
        engine.onFireRelease();
      }}
      aria-label="Fire Weapon"
      className={`relative w-16 h-16 sm:w-20 sm:h-20 rounded-full flex items-center justify-center border-2 shadow-xl backdrop-blur-md active:scale-95 transition ${
        side === 'right'
          ? 'bg-[#D9A05B]/30 border-[#D9A05B] text-[#FFF4E0]'
          : 'bg-[#D9A05B]/20 border-[#D9A05B]/75 text-[#FFF4E0]'
      }`}
    >
      {hud.activeWeapon === 'RECURVE_BOW' && hud.bowDrawing && (
        <svg className="absolute inset-0 w-full h-full -rotate-90" viewBox="0 0 68 68">
          <circle
            cx="34"
            cy="34"
            r="30"
            fill="none"
            stroke="#5CE6B0"
            strokeWidth="4"
            strokeDasharray={bowArcDash}
            strokeDashoffset={bowArcOffset}
          />
        </svg>
      )}
      <Crosshair className="w-7 h-7" />
    </button>
  );

  return (
    <div className="fixed inset-0 z-20 pointer-events-none select-none">
      {/* Left Half: Floating Analog Joystick Touch Surface */}
      <div
        className="absolute left-0 top-14 bottom-0 w-[44%] pointer-events-auto"
        onPointerDown={onLeftDown}
        onPointerMove={onLeftMove}
        onPointerUp={onLeftUp}
        onPointerCancel={onLeftUp}
      />

      {/* Right Half: Velocity-Curved Look Pad */}
      <div
        className="absolute right-0 top-14 bottom-0 w-[56%] pointer-events-auto"
        onPointerDown={onRightDown}
        onPointerMove={onRightMove}
        onPointerUp={onRightUp}
        onPointerCancel={onRightUp}
      />

      {/* Floating Joystick Visual */}
      {joyVisual.active && (
        <div
          className="fixed pointer-events-none -translate-x-1/2 -translate-y-1/2"
          style={{ left: joyVisual.ox, top: joyVisual.oy }}
        >
          <div className="w-28 h-28 rounded-full border-2 border-white/25 bg-black/25 backdrop-blur-xs flex items-center justify-center">
            <div
              className={`w-12 h-12 rounded-full border-2 transition-colors ${
                joyVisual.sprint
                  ? 'bg-[#D9A05B]/70 border-[#FFF1D6]'
                  : 'bg-white/30 border-white/60'
              }`}
              style={{ transform: `translate(${joyVisual.kx}px, ${joyVisual.ky}px)` }}
            />
          </div>
          {joyVisual.sprint && (
            <div className="text-center text-[10px] font-mono-hud text-[#D9A05B] mt-1 font-bold tracking-wider">
              SPRINT LOCK
            </div>
          )}
        </div>
      )}

      {/* Optional Left-Side Fire Button */}
      {settings.leftFireButtonEnabled && (
        <div className="absolute left-5 bottom-36 pointer-events-auto">{renderFireButton('left')}</div>
      )}

      {/* Left-Bottom Utility Pill (Hunter Sense & Flashlight) */}
      <div className="absolute left-4 bottom-4 flex items-center gap-2 pointer-events-auto">
        <button
          onClick={() => engine.activateHunterSense()}
          aria-label="Hunter Sense"
          className={`relative w-12 h-12 rounded-2xl flex items-center justify-center border backdrop-blur-md transition active:scale-95 ${
            hud.hunterSenseActive
              ? 'bg-[#1D7A6E]/60 border-[#5CE6B0] text-[#E6FFF8]'
              : hud.hunterSenseCooldownRatio > 0
                ? 'bg-black/45 border-white/10 text-white/40'
                : 'bg-[#0D1B15]/75 border-white/20 text-[#E6E9E5]'
          }`}
        >
          <Eye className="w-5 h-5" />
          {hud.hunterSenseActive && (
            <span className="absolute -top-2 -right-2 px-1.5 py-0.5 rounded-full bg-[#5CE6B0] text-[#081512] text-[9px] font-mono-hud font-bold">
              {Math.ceil(hud.hunterSenseRemaining)}s
            </span>
          )}
        </button>

        <button
          onClick={() => engine.toggleFlashlight()}
          aria-label="Toggle Flashlight"
          className={`w-12 h-12 rounded-2xl flex items-center justify-center border backdrop-blur-md transition active:scale-95 ${
            hud.flashlightOn
              ? 'bg-[#D9A05B]/35 border-[#D9A05B] text-[#FFF1D6]'
              : 'bg-[#0D1B15]/75 border-white/20 text-[#E6E9E5]'
          }`}
        >
          <Flashlight className="w-5 h-5" />
        </button>
      </div>

      {/* Scope Zoom Slider & Hold Breath (shown while scoped) */}
      {hud.scoped && hud.activeWeapon === 'RIFLE_308' && (
        <div className="absolute left-5 top-1/2 -translate-y-1/2 flex flex-col items-center gap-3 pointer-events-auto bg-[#0A1611]/75 border border-white/15 rounded-2xl px-2.5 py-3 backdrop-blur-md">
          <span className="text-[10px] font-mono-hud text-[#D9A05B]">{hud.scopeZoom.toFixed(0)}x</span>
          <input
            type="range"
            min={4}
            max={12}
            step={0.5}
            value={hud.scopeZoom}
            onChange={(e) => engine.setScopeZoom(parseFloat(e.target.value))}
            aria-label="Scope Magnification"
            className="h-24 w-2 accent-[#D9A05B] cursor-pointer"
            style={{ writingMode: 'vertical-lr', direction: 'rtl' }}
          />
          <button
            onPointerDown={(e) => {
              e.stopPropagation();
              engine.setHoldingBreath(true);
            }}
            onPointerUp={(e) => {
              e.stopPropagation();
              engine.setHoldingBreath(false);
            }}
            onPointerCancel={() => engine.setHoldingBreath(false)}
            aria-label="Hold Breath"
            className={`w-12 h-12 rounded-xl flex flex-col items-center justify-center border transition ${
              hud.holdingBreath
                ? 'bg-[#2C6B5B]/70 border-[#5CE6B0] text-white'
                : 'bg-black/45 border-white/20 text-[#C8D4CE]'
            }`}
          >
            <Wind className="w-4 h-4" />
            <div className="w-7 h-1 bg-white/15 rounded-full overflow-hidden mt-1">
              <div
                className="h-full bg-[#5CE6B0]"
                style={{ width: `${Math.round(hud.breathStamina * 100)}%` }}
              />
            </div>
          </button>
        </div>
      )}

      {/* Right-Side Ergonomic Action Cluster */}
      <div className="absolute right-4 bottom-4 flex flex-col items-end gap-2.5 pointer-events-auto">
        {/* Cancel Bow Draw */}
        {hud.bowDrawing && (
          <button
            onClick={() => engine.cancelBowDraw()}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-red-900/75 border border-red-400/50 text-xs font-semibold text-white backdrop-blur-md"
          >
            <X className="w-3.5 h-3.5" />
            Cancel Draw
          </button>
        )}

        {/* Upper row: Weapon Switch, Reload, Scope */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => engine.switchWeapon()}
            aria-label="Switch Weapon"
            className="h-12 px-3 rounded-2xl bg-[#0D1B15]/80 border border-white/20 text-[#E6E9E5] flex items-center gap-1.5 text-xs font-mono-hud backdrop-blur-md active:scale-95 transition"
          >
            <Repeat className="w-4 h-4 text-[#D9A05B]" />
            <span>{hud.activeWeapon === 'RIFLE_308' ? '.308' : 'BOW'}</span>
          </button>

          {hud.activeWeapon === 'RIFLE_308' && (
            <button
              onClick={() => engine.reloadWeapon()}
              aria-label="Reload Rifle"
              className="w-12 h-12 rounded-2xl bg-[#0D1B15]/80 border border-white/20 text-[#E6E9E5] flex items-center justify-center backdrop-blur-md active:scale-95 transition"
            >
              <RefreshCw className={`w-4 h-4 ${hud.reloading ? 'animate-spin text-[#D9A05B]' : ''}`} />
            </button>
          )}

          <button
            onClick={() => engine.toggleScope()}
            aria-label="Toggle Scope"
            className={`w-12 h-12 rounded-2xl flex items-center justify-center border backdrop-blur-md active:scale-95 transition ${
              hud.scoped
                ? 'bg-[#D9A05B]/35 border-[#D9A05B] text-[#FFF1D6]'
                : 'bg-[#0D1B15]/80 border-white/20 text-[#E6E9E5]'
            }`}
          >
            <Crosshair className="w-5 h-5" />
          </button>
        </div>

        {/* Lower row: Stance, Jump, Primary Fire */}
        <div className="flex items-end gap-2.5">
          <button
            onClick={() => engine.cycleStance()}
            aria-label="Cycle Stance"
            className="h-12 px-3 rounded-2xl bg-[#0D1B15]/80 border border-white/20 text-[#E6E9E5] flex items-center gap-1.5 text-xs font-semibold backdrop-blur-md active:scale-95 transition"
          >
            <User className="w-4 h-4 text-[#8EC9A6]" />
            <span>{hud.stance}</span>
          </button>

          <button
            onClick={() => engine.jump()}
            aria-label="Jump"
            className="w-12 h-12 rounded-2xl bg-[#0D1B15]/80 border border-white/20 text-[#E6E9E5] flex items-center justify-center backdrop-blur-md active:scale-95 transition"
          >
            <ArrowUp className="w-5 h-5" />
          </button>

          {renderFireButton('right')}
        </div>
      </div>
    </div>
  );
};
