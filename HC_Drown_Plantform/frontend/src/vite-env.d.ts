/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue';
  const component: DefineComponent<{}, {}, any>;
  export default component;
}

interface Window {
  CESIUM_BASE_URL: string;
}

declare module 'cesium' {
  export type Viewer = any;
  export const Viewer: any;

  export type ScreenSpaceEventHandler = any;
  export const ScreenSpaceEventHandler: any;

  export type Entity = any;
  export const Entity: any;

  export type Cartesian2 = any;
  export const Cartesian2: any;

  export type Cartesian3 = any;
  export const Cartesian3: any;

  export type Color = any;
  export const Color: any;

  export type Terrain = any;
  export const Terrain: any;

  export type Math = any;
  export const Math: any;

  export type Cartographic = any;
  export const Cartographic: any;

  export type UrlTemplateImageryProvider = any;
  export const UrlTemplateImageryProvider: any;

  export const defined: any;
  export const VerticalOrigin: any;
  export const LabelStyle: any;
  export const ScreenSpaceEventType: any;
}
