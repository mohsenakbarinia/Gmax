import React from 'react';
import { HudState } from '../types';

interface OpticalScopeProps {
  hud: HudState;
}

export const OpticalScope: React.FC<OpticalScopeProps> = ({ hud }) => {
  const offsetX = hud.scopeSwayX * 1400;
  const offsetY = -hud.scopeSwayY * 1400;

  return (
    <div className="fixed inset-0 z-10 pointer-events-none overflow-hidden">
      {/* Dark Optical Housing Vignette + Parallax Tube */}
      <svg
        className="w-full h-full"
        viewBox="0 0 1000 600"
        preserveAspectRatio="xMidYMid slice"
      >
        <defs>
          <mask id="scope-lens-mask">
            <rect width="1000" height="600" fill="white" />
            <circle cx={500 + offsetX * 0.45} cy={300 + offsetY * 0.45} r="235" fill="black" />
          </mask>
          <radialGradient id="lens-vignette" cx="50%" cy="50%" r="50%">
            <stop offset="76%" stopColor="rgba(0,0,0,0)" />
            <stop offset="95%" stopColor="rgba(0,0,0,0.45)" />
            <stop offset="100%" stopColor="rgba(0,0,0,0.95)" />
          </radialGradient>
        </defs>

        {/* Outer Blackout */}
        <rect width="1000" height="600" fill="#040706" mask="url(#scope-lens-mask)" />

        {/* Subtle Optical Chromatic Ring & Inner Vignette */}
        <circle
          cx={500 + offsetX * 0.45}
          cy={300 + offsetY * 0.45}
          r="235"
          fill="url(#lens-vignette)"
          stroke="rgba(160, 210, 220, 0.22)"
          strokeWidth="3"
        />

        {/* Precision Mil-Dot Reticle */}
        <g stroke="rgba(18, 22, 20, 0.92)" strokeWidth="1.6">
          <line x1="265" y1="300" x2="472" y2="300" />
          <line x1="528" y1="300" x2="735" y2="300" />
          <line x1="500" y1="65" x2="500" y2="272" />
          <line x1="500" y1="328" x2="500" y2="535" />
          {/* Fine center cross */}
          <line x1="472" y1="300" x2="528" y2="300" strokeWidth="0.9" />
          <line x1="500" y1="272" x2="500" y2="328" strokeWidth="0.9" />
        </g>

        {/* Illuminated Amber Center Dot & BDC Drop Ticks */}
        <circle cx="500" cy="300" r="2.6" fill={hud.holdingBreath ? '#5CE6B0' : '#D9A05B'} />
        {[-90, -60, -30, 30, 60, 90].map((dx) => (
          <circle key={`mx-${dx}`} cx={500 + dx} cy={300} r="2.1" fill="#121614" />
        ))}
        {[28, 56, 88, 124].map((dy) => (
          <line
            key={`my-${dy}`}
            x1={500 - 12}
            y1={300 + dy}
            x2={500 + 12}
            y2={300 + dy}
            stroke="#121614"
            strokeWidth="1.8"
          />
        ))}
      </svg>

      {/* Laser Rangefinder & Zoom Readout */}
      <div className="absolute left-1/2 top-[68%] -translate-x-1/2 flex items-center gap-4 px-3.5 py-1 rounded-full bg-black/60 border border-[#D9A05B]/35 font-mono-hud text-xs text-[#E6E9E5]">
        <span className="text-[#D9A05B]">{hud.scopeZoom.toFixed(1)}x</span>
        <span className="opacity-40">|</span>
        <span className="text-[#7DE8C1]">
          {hud.rangefinderMeters !== null ? `${hud.rangefinderMeters}m` : '---m'}
        </span>
        <span className="opacity-40">|</span>
        <span>WIND {hud.windSpeedMps.toFixed(1)} m/s</span>
      </div>
    </div>
  );
};
