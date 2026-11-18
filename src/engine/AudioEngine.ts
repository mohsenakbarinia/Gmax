import { Species, SurfaceType, WeatherPreset } from '../types';

/**
 * 100% Procedural Web Audio API Spatial Sound Synthesizer (zero external audio URLs).
 * Synthesizes .308 rifle cracks + alpine valley echoes, bolt cycling, bowstring draw/twang,
 * surface-aware footsteps, wildlife calls, wind/rain ambience, thunder, and breath heartbeat.
 */
export class AudioEngine {
  private ctx: AudioContext | null = null;
  private masterGain: GainNode | null = null;
  private windGain: GainNode | null = null;
  private rainGain: GainNode | null = null;
  private noiseBuffer: AudioBuffer | null = null;
  private masterVolume = 0.85;
  private weather: WeatherPreset = 'GOLDEN_HOUR';
  private started = false;

  start(): void {
    if (this.started && this.ctx) {
      if (this.ctx.state === 'suspended') void this.ctx.resume();
      return;
    }
    const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtx) return;

    try {
      this.ctx = new AudioCtx();
      this.masterGain = this.ctx.createGain();
      this.masterGain.gain.value = this.masterVolume;
      this.masterGain.connect(this.ctx.destination);

      // Shared 2-second white noise buffer
      const sr = this.ctx.sampleRate;
      this.noiseBuffer = this.ctx.createBuffer(1, sr * 2, sr);
      const data = this.noiseBuffer.getChannelData(0);
      for (let i = 0; i < data.length; i++) {
        data[i] = Math.random() * 2 - 1;
      }

      // Continuous filtered alpine wind
      const windSrc = this.ctx.createBufferSource();
      windSrc.buffer = this.noiseBuffer;
      windSrc.loop = true;
      const windFilter = this.ctx.createBiquadFilter();
      windFilter.type = 'bandpass';
      windFilter.frequency.value = 310;
      windFilter.Q.value = 1.6;
      this.windGain = this.ctx.createGain();
      this.windGain.gain.value = 0.08;
      windSrc.connect(windFilter).connect(this.windGain).connect(this.masterGain);
      windSrc.start();

      // Continuous filtered rain bed
      const rainSrc = this.ctx.createBufferSource();
      rainSrc.buffer = this.noiseBuffer;
      rainSrc.loop = true;
      const rainFilter = this.ctx.createBiquadFilter();
      rainFilter.type = 'highpass';
      rainFilter.frequency.value = 1400;
      this.rainGain = this.ctx.createGain();
      this.rainGain.gain.value = this.weather === 'RAINY_STORM' ? 0.14 : 0.0;
      rainSrc.connect(rainFilter).connect(this.rainGain).connect(this.masterGain);
      rainSrc.start();

      this.started = true;
    } catch {
      // Ignore autoplay errors until user gesture unlocks audio
    }
  }

  setMasterVolume(vol: number): void {
    this.masterVolume = vol;
    if (this.masterGain && this.ctx) {
      this.masterGain.gain.setTargetAtTime(vol, this.ctx.currentTime, 0.05);
    }
  }

  setWeather(weather: WeatherPreset): void {
    this.weather = weather;
    if (!this.ctx || !this.windGain || !this.rainGain) return;
    const now = this.ctx.currentTime;
    this.windGain.gain.setTargetAtTime(weather === 'RAINY_STORM' ? 0.16 : 0.075, now, 0.3);
    this.rainGain.gain.setTargetAtTime(weather === 'RAINY_STORM' ? 0.16 : 0.0, now, 0.3);
  }

  playRifleShot(): void {
    if (!this.ctx || !this.masterGain || !this.noiseBuffer) return;
    const now = this.ctx.currentTime;

    // Supersonic crack + low chest punch
    const noise = this.ctx.createBufferSource();
    noise.buffer = this.noiseBuffer;
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(4800, now);
    filter.frequency.exponentialRampToValueAtTime(240, now + 0.55);

    const env = this.ctx.createGain();
    env.gain.setValueAtTime(0.95, now);
    env.gain.exponentialRampToValueAtTime(0.001, now + 0.65);
    noise.connect(filter).connect(env).connect(this.masterGain);
    noise.start(now);
    noise.stop(now + 0.7);

    // Sub thump
    const osc = this.ctx.createOscillator();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(145, now);
    osc.frequency.exponentialRampToValueAtTime(32, now + 0.18);
    const subEnv = this.ctx.createGain();
    subEnv.gain.setValueAtTime(0.8, now);
    subEnv.gain.exponentialRampToValueAtTime(0.001, now + 0.22);
    osc.connect(subEnv).connect(this.masterGain);
    osc.start(now);
    osc.stop(now + 0.24);

    // Rolling mountain valley echo
    const echo = this.ctx.createBufferSource();
    echo.buffer = this.noiseBuffer;
    const echoFilter = this.ctx.createBiquadFilter();
    echoFilter.type = 'bandpass';
    echoFilter.frequency.value = 520;
    const echoEnv = this.ctx.createGain();
    echoEnv.gain.setValueAtTime(0.001, now + 0.32);
    echoEnv.gain.linearRampToValueAtTime(0.24, now + 0.45);
    echoEnv.gain.exponentialRampToValueAtTime(0.001, now + 1.9);
    echo.connect(echoFilter).connect(echoEnv).connect(this.masterGain);
    echo.start(now + 0.3);
    echo.stop(now + 2.0);
  }

  playBowRelease(): void {
    if (!this.ctx || !this.masterGain) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    osc.type = 'triangle';
    osc.frequency.setValueAtTime(210, now);
    osc.frequency.exponentialRampToValueAtTime(72, now + 0.22);
    const env = this.ctx.createGain();
    env.gain.setValueAtTime(0.32, now);
    env.gain.exponentialRampToValueAtTime(0.001, now + 0.24);
    osc.connect(env).connect(this.masterGain);
    osc.start(now);
    osc.stop(now + 0.25);
  }

  playFootstep(surface: SurfaceType, volumeScale: number): void {
    if (!this.ctx || !this.masterGain || !this.noiseBuffer) return;
    const now = this.ctx.currentTime;
    const src = this.ctx.createBufferSource();
    src.buffer = this.noiseBuffer;
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'bandpass';
    filter.frequency.value =
      surface === 'SHALLOW_WATER'
        ? 950
        : surface === 'ROCK'
          ? 1500
          : surface === 'DIRT'
            ? 620
            : 440;
    const env = this.ctx.createGain();
    env.gain.setValueAtTime(0.16 * volumeScale, now);
    env.gain.exponentialRampToValueAtTime(0.001, now + (surface === 'SHALLOW_WATER' ? 0.16 : 0.08));
    src.connect(filter).connect(env).connect(this.masterGain);
    src.start(now);
    src.stop(now + 0.18);
  }

  playWildlifeCall(species: Species, distMeters: number, pan: number): void {
    if (!this.ctx || !this.masterGain) return;
    const now = this.ctx.currentTime;
    const atten = Math.max(0.04, 1 - distMeters / 140) * 0.28;
    const osc = this.ctx.createOscillator();
    const env = this.ctx.createGain();
    const panner = this.ctx.createStereoPanner();
    panner.pan.value = pan;

    if (species === 'ROYAL_STAG') {
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(340, now);
      osc.frequency.linearRampToValueAtTime(780, now + 0.45);
      osc.frequency.exponentialRampToValueAtTime(240, now + 1.35);
      env.gain.setValueAtTime(0.001, now);
      env.gain.linearRampToValueAtTime(atten, now + 0.2);
      env.gain.exponentialRampToValueAtTime(0.001, now + 1.4);
    } else if (species === 'TIMBER_WOLF') {
      osc.type = 'sine';
      osc.frequency.setValueAtTime(290, now);
      osc.frequency.linearRampToValueAtTime(520, now + 0.6);
      osc.frequency.linearRampToValueAtTime(380, now + 1.6);
      env.gain.setValueAtTime(0.001, now);
      env.gain.linearRampToValueAtTime(atten, now + 0.3);
      env.gain.exponentialRampToValueAtTime(0.001, now + 1.65);
    } else {
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(115, now);
      osc.frequency.exponentialRampToValueAtTime(70, now + 0.28);
      env.gain.setValueAtTime(atten * 0.9, now);
      env.gain.exponentialRampToValueAtTime(0.001, now + 0.3);
    }

    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.value = 1100;
    osc.connect(filter).connect(env).connect(panner).connect(this.masterGain);
    osc.start(now);
    osc.stop(now + 1.7);
  }

  playThunder(): void {
    if (!this.ctx || !this.masterGain || !this.noiseBuffer) return;
    const now = this.ctx.currentTime;
    const src = this.ctx.createBufferSource();
    src.buffer = this.noiseBuffer;
    const filter = this.ctx.createBiquadFilter();
    filter.type = 'lowpass';
    filter.frequency.setValueAtTime(180, now);
    const env = this.ctx.createGain();
    env.gain.setValueAtTime(0.001, now);
    env.gain.linearRampToValueAtTime(0.55, now + 0.25);
    env.gain.exponentialRampToValueAtTime(0.001, now + 2.8);
    src.connect(filter).connect(env).connect(this.masterGain);
    src.start(now);
    src.stop(now + 2.9);
  }

  playHeartbeat(): void {
    if (!this.ctx || !this.masterGain) return;
    const now = this.ctx.currentTime;
    for (const offset of [0, 0.17]) {
      const osc = this.ctx.createOscillator();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(62, now + offset);
      osc.frequency.exponentialRampToValueAtTime(34, now + offset + 0.12);
      const env = this.ctx.createGain();
      env.gain.setValueAtTime(0.28, now + offset);
      env.gain.exponentialRampToValueAtTime(0.001, now + offset + 0.14);
      osc.connect(env).connect(this.masterGain);
      osc.start(now + offset);
      osc.stop(now + offset + 0.15);
    }
  }

  playVitalConfirm(): void {
    if (!this.ctx || !this.masterGain) return;
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(520, now);
    osc.frequency.exponentialRampToValueAtTime(880, now + 0.16);
    const env = this.ctx.createGain();
    env.gain.setValueAtTime(0.22, now);
    env.gain.exponentialRampToValueAtTime(0.001, now + 0.28);
    osc.connect(env).connect(this.masterGain);
    osc.start(now);
    osc.stop(now + 0.3);
  }

  dispose(): void {
    if (this.ctx) {
      void this.ctx.close();
      this.ctx = null;
    }
  }
}
