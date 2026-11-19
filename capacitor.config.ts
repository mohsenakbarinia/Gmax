import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.gmax.wildwood',
  appName: 'Wildwood Reality',
  webDir: 'dist',
  android: {
    allowMixedContent: true,
    backgroundColor: '#000000'
  }
};

export default config;
