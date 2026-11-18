export type WeatherPreset =
  | 'GOLDEN_HOUR'
  | 'MISTY_DAWN'
  | 'RAINY_STORM'
  | 'MOONLIT_NIGHT';

export type GraphicsQuality = 'BALANCED' | 'ULTRA_REALISTIC' | 'PHOTO_MAX';
export type GyroMode = 'OFF' | 'SCOPE_ONLY' | 'ALWAYS_ON';
export type WeaponType = 'RIFLE_308' | 'RECURVE_BOW';
export type Stance = 'STAND' | 'CROUCH' | 'PRONE';
export type Species = 'ROYAL_STAG' | 'WILD_BOAR' | 'TIMBER_WOLF';
export type HitZone = 'HEART' | 'LUNGS' | 'HEAD' | 'BODY';
export type TrophyRating = 'GOLD' | 'SILVER' | 'BRONZE';
export type SurfaceType = 'GRASS' | 'DIRT' | 'ROCK' | 'SHALLOW_WATER';
export type GameMode = 'TITLE_ORBIT' | 'PLAYING' | 'PAUSED';

export interface WeatherMeta {
  id: WeatherPreset;
  label: string;
  subtitle: string;
}

export const WEATHER_PRESETS: WeatherMeta[] = [
  { id: 'GOLDEN_HOUR', label: 'Golden Hour', subtitle: 'Low amber sun & god rays' },
  { id: 'MISTY_DAWN', label: 'Misty Dawn', subtitle: 'Cool alpine mist & wet sheen' },
  { id: 'RAINY_STORM', label: 'Rainy Storm', subtitle: 'Overcast rain & thunder' },
  { id: 'MOONLIT_NIGHT', label: 'Moonlit Night', subtitle: 'Starlit indigo & fireflies' }
];

export interface QualityProfile {
  id: GraphicsQuality;
  label: string;
  pixelRatioCap: number;
  shadowMapSize: number;
  grassDensity: number;
  godRays: boolean;
}

export const QUALITY_PROFILES: Record<GraphicsQuality, QualityProfile> = {
  BALANCED: {
    id: 'BALANCED',
    label: 'Balanced (60 FPS)',
    pixelRatioCap: 1.25,
    shadowMapSize: 1024,
    grassDensity: 0.55,
    godRays: false
  },
  ULTRA_REALISTIC: {
    id: 'ULTRA_REALISTIC',
    label: 'Ultra Realistic',
    pixelRatioCap: 1.65,
    shadowMapSize: 2048,
    grassDensity: 0.85,
    godRays: true
  },
  PHOTO_MAX: {
    id: 'PHOTO_MAX',
    label: 'Photo Mode Max',
    pixelRatioCap: 2.0,
    shadowMapSize: 2048,
    grassDensity: 1.0,
    godRays: true
  }
};

export const STANCE_CONFIG: Record<
  Stance,
  { label: string; eyeHeight: number; speedMult: number; noiseMult: number; visMult: number }
> = {
  STAND: { label: 'Stand', eyeHeight: 1.72, speedMult: 1.0, noiseMult: 1.0, visMult: 1.0 },
  CROUCH: { label: 'Crouch', eyeHeight: 1.12, speedMult: 0.56, noiseMult: 0.42, visMult: 0.52 },
  PRONE: { label: 'Prone', eyeHeight: 0.52, speedMult: 0.3, noiseMult: 0.16, visMult: 0.22 }
};

export const SPECIES_LABEL: Record<Species, string> = {
  ROYAL_STAG: 'Royal Red Stag',
  WILD_BOAR: 'Wild Forest Boar',
  TIMBER_WOLF: 'Timber Wolf'
};

export const HIT_ZONE_LABEL: Record<HitZone, string> = {
  HEART: 'HEART SHOT',
  LUNGS: 'LUNG SHOT',
  HEAD: 'HEAD SHOT',
  BODY: 'BODY SHOT'
};

export interface HuntingContract {
  id: number;
  title: string;
  description: string;
  requiredSpecies?: Species;
  requiredWeapon?: WeaponType;
  requireVital: boolean;
  minDistance: number;
  maxDistance: number;
}

export const HUNTING_CONTRACTS: HuntingContract[] = [
  {
    id: 0,
    title: 'Alpine Marksman',
    description: 'Harvest a Royal Red Stag from >85m with a clean Vital Shot',
    requiredSpecies: 'ROYAL_STAG',
    requireVital: true,
    minDistance: 85,
    maxDistance: 1500
  },
  {
    id: 1,
    title: 'Silent Bow Stalk',
    description: 'Silent Bow Hunt: Harvest a Wild Boar within 55m',
    requiredSpecies: 'WILD_BOAR',
    requiredWeapon: 'RECURVE_BOW',
    requireVital: false,
    minDistance: 1,
    maxDistance: 55
  },
  {
    id: 2,
    title: 'Apex Predator',
    description: 'Harvest a Timber Wolf with a clean Vital Shot',
    requiredSpecies: 'TIMBER_WOLF',
    requireVital: true,
    minDistance: 1,
    maxDistance: 1500
  },
  {
    id: 3,
    title: 'Long Valley Shot',
    description: 'Harvest any game from >110m using the .308 Bolt Rifle',
    requiredWeapon: 'RIFLE_308',
    requireVital: false,
    minDistance: 110,
    maxDistance: 1500
  },
  {
    id: 4,
    title: 'Ghost of the Pines',
    description: 'Harvest a Royal Red Stag with the Recurve Bow',
    requiredSpecies: 'ROYAL_STAG',
    requiredWeapon: 'RECURVE_BOW',
    requireVital: false,
    minDistance: 1,
    maxDistance: 1500
  },
  {
    id: 5,
    title: 'Ethical Precision',
    description: 'Land any Vital Shot from >60m in the alpine valley',
    requireVital: true,
    minDistance: 60,
    maxDistance: 1500
  }
];

export function matchesContract(
  contract: HuntingContract,
  species: Species,
  weapon: WeaponType,
  hitZone: HitZone,
  distanceMeters: number
): boolean {
  if (contract.requiredSpecies && contract.requiredSpecies !== species) return false;
  if (contract.requiredWeapon && contract.requiredWeapon !== weapon) return false;
  if (contract.requireVital && hitZone === 'BODY') return false;
  if (distanceMeters < contract.minDistance || distanceMeters > contract.maxDistance) return false;
  return true;
}

export interface GameSettings {
  graphicsQuality: GraphicsQuality;
  weatherPreset: WeatherPreset;
  normalSensitivity: number;
  adsSensitivity: number;
  gyroMode: GyroMode;
  leftFireButtonEnabled: boolean;
  hapticsEnabled: boolean;
  masterVolume: number;
  showFpsCounter: boolean;
}

export const DEFAULT_SETTINGS: GameSettings = {
  graphicsQuality: 'ULTRA_REALISTIC',
  weatherPreset: 'GOLDEN_HOUR',
  normalSensitivity: 1.0,
  adsSensitivity: 0.42,
  gyroMode: 'SCOPE_ONLY',
  leftFireButtonEnabled: false,
  hapticsEnabled: true,
  masterVolume: 0.85,
  showFpsCounter: false
};

export interface CompassMarker {
  bearingDeg: number;
  distanceMeters: number;
  kind: 'TRACK' | 'BLOOD' | 'ANIMAL';
}

export interface TrophyBanner {
  headline: string;
  subline: string;
  rating: TrophyRating;
  contractCompleted: boolean;
  timestampMs: number;
}

export interface TrophyRecord {
  id: string;
  species: Species;
  weapon: WeaponType;
  hitZone: HitZone;
  rating: TrophyRating;
  distanceMeters: number;
  scorePoints: number;
  weatherPreset: WeatherPreset;
  createdAtMs: number;
}

export interface CareerStats {
  totalHarvests: number;
  goldTrophies: number;
  longestVitalShotMeters: number;
  completedContractsCount: number;
  activeContractIndex: number;
}

export const DEFAULT_STATS: CareerStats = {
  totalHarvests: 0,
  goldTrophies: 0,
  longestVitalShotMeters: 0,
  completedContractsCount: 0,
  activeContractIndex: 0
};

export interface HudState {
  gameMode: GameMode;
  textureProgress: number;
  texturesReady: boolean;
  headingDeg: number;
  windDirectionDeg: number;
  windSpeedMps: number;
  stance: Stance;
  sprintLocked: boolean;
  sprinting: boolean;
  stealthVisibility: number;
  stealthNoise: number;
  activeWeapon: WeaponType;
  rifleAmmo: number;
  rifleMagSize: number;
  reloading: boolean;
  reloadProgress: number;
  bowDrawing: boolean;
  bowDrawTension: number;
  scoped: boolean;
  scopeZoom: number;
  rangefinderMeters: number | null;
  holdingBreath: boolean;
  breathStamina: number;
  hunterSenseActive: boolean;
  hunterSenseRemaining: number;
  hunterSenseCooldownRatio: number;
  flashlightOn: boolean;
  fps: number;
  compassMarkers: CompassMarker[];
  statusAlert: string | null;
  trophyBanner: TrophyBanner | null;
  woundedFlash: number;
  scopeSwayX: number;
  scopeSwayY: number;
}

export const INITIAL_HUD: HudState = {
  gameMode: 'TITLE_ORBIT',
  textureProgress: 0,
  texturesReady: false,
  headingDeg: 0,
  windDirectionDeg: 42,
  windSpeedMps: 3.4,
  stance: 'STAND',
  sprintLocked: false,
  sprinting: false,
  stealthVisibility: 0.35,
  stealthNoise: 0.1,
  activeWeapon: 'RIFLE_308',
  rifleAmmo: 5,
  rifleMagSize: 5,
  reloading: false,
  reloadProgress: 0,
  bowDrawing: false,
  bowDrawTension: 0,
  scoped: false,
  scopeZoom: 6.0,
  rangefinderMeters: null,
  holdingBreath: false,
  breathStamina: 1.0,
  hunterSenseActive: false,
  hunterSenseRemaining: 0,
  hunterSenseCooldownRatio: 0,
  flashlightOn: false,
  fps: 60,
  compassMarkers: [],
  statusAlert: null,
  trophyBanner: null,
  woundedFlash: 0,
  scopeSwayX: 0,
  scopeSwayY: 0
};
