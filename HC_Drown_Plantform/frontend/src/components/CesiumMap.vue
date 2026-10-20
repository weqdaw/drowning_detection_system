<template>
  <div class="map-panel">
    <div class="map-heading">
      <div class="map-heading-title">
        <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3V6Zm6-3v15m6-12v15"/></svg>
        水域空间态势
      </div>
      <small>HUANGCUN / 3D GIS</small>
    </div>
    <div class="map-controls">
      <button class="btn-map-action" :class="{ active: isDrawing }" @click="toggleDrawing">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" style="display:inline-block; vertical-align:middle; margin-right:4px;">
          <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z"/>
        </svg>
        <span>{{ isDrawing ? '请点击地图选择坐标...' : '新增设备打点' }}</span>
      </button>
      <button class="btn-map-action" :class="{ active: isDrawingPolygon }" @click="toggleDrawingPolygon" style="margin-top: 4px;">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" style="display:inline-block; vertical-align:middle; margin-right:4px;">
          <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
        </svg>
        <span>{{ isDrawingPolygon ? '鼠标左键打点，右键完成...' : '绘制监控巡逻区域' }}</span>
      </button>
    </div>

    <!-- Real-time Linkage Warning Banner Overlay -->
    <div v-if="pendingAlert" class="alarm-banner-overlay">
      <div class="alarm-banner-content">
        <svg class="ui-icon alarm-blink-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 10 18H2L12 3Zm0 6v5m0 3v.1"/></svg>
        <span class="alarm-msg-text">
          <strong>系统警告：</strong>
          检测到 <strong>[{{ pendingAlert.waterAreaName || '重点防区' }} - {{ pendingAlert.sourceDeviceName || '监测点' }}]</strong>
          触发 <strong>[{{ pendingAlert.alertType === 'DROWNING' ? '人员落水/疑似溺水' : pendingAlert.alertType }}]</strong> 警告！
        </span>
        <button class="alarm-confirm-btn" @click="$emit('confirm-alert', pendingAlert.id)">
          立即派发救援
        </button>
      </div>
    </div>

    <!-- Lifeguard positioning prompt banner -->
    <div v-if="isDrawingLifeguard" class="alarm-banner-overlay" style="background: rgba(16, 185, 129, 0.95); border-color: #10b981;">
      <div class="alarm-banner-content">
        <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/></svg>
        <span class="alarm-msg-text" style="color: #ffffff;">
          <strong>救生员位置标记模式激活：</strong> 请在三维地图上直接点击左键，完成最新位置打点！
        </span>
      </div>
    </div>

    <div ref="cesiumContainer" class="cesium-container"></div>
    <div class="map-legend" aria-label="地图图例">
      <span><i></i>监控点位</span><span><i></i>巡逻区域</span><span><i></i>风险告警</span>
    </div>
    <div class="map-frame" aria-hidden="true"><i></i><i></i><i></i><i></i></div>
    <div class="map-orientation" aria-hidden="true">
      <svg viewBox="0 0 32 32" fill="none"><circle cx="16" cy="16" r="13"/><path d="m16 7 4 12-4-2-4 2 4-12Z"/></svg>
      <span>3D 空间视域</span>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, onMounted, ref, watch, PropType } from 'vue';
import * as Cesium from 'cesium';

export interface DeviceData {
  id: number;
  deviceCode: string;
  deviceName: string;
  deviceType: string;
  location: string;
  locationDesc: string;
  status: string;
  ipAddress?: string;
  port?: number;
  waterAreaId?: number;
  manufacturer?: string;
  model?: string;
  lastHeartbeat?: string;
  extraConfig?: any;
}

export default defineComponent({
  name: 'CesiumMap',
  props: {
    devices: {
      type: Array as PropType<DeviceData[]>,
      default: () => []
    },
    pendingAlert: {
      type: Object as PropType<any>,
      default: null
    },
    lifeguards: {
      type: Array as PropType<any[]>,
      default: () => []
    },
    isDrawingLifeguard: {
      type: Boolean,
      default: false
    }
  },
  emits: ['map-click', 'confirm-alert', 'select-device', 'draw-polygon-complete', 'lifeguard-map-click'],
  setup(props, { emit }) {
    const cesiumContainer = ref<HTMLDivElement | null>(null);
    let viewer: Cesium.Viewer | null = null;
    const isDrawing = ref(false);
    const isDrawingPolygon = ref(false);

    let activePolygonPoints: Cesium.Cartesian3[] = [];
    let tempPointsEntities: Cesium.Entity[] = [];
    let activePolylineEntity: Cesium.Entity | null = null;

    let clickHandler: Cesium.ScreenSpaceEventHandler | null = null;
    const entitiesList: Cesium.Entity[] = [];
    const lifeguardsEntitiesList: Cesium.Entity[] = [];

    const initCesium = () => {
      if (!cesiumContainer.value) return;

      // 1. Configure Gaode satellite and road overlay templates
      const satelliteProvider = new Cesium.UrlTemplateImageryProvider({
        url: 'https://webst{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
        subdomains: ['01', '02', '03', '04']
      });

      const roadProvider = new Cesium.UrlTemplateImageryProvider({
        url: 'https://webst{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}',
        subdomains: ['01', '02', '03', '04']
      });

      // 2. Initialize viewer with Ellipsoid Terrain to avoid Ion Token authentication errors
      viewer = new Cesium.Viewer(cesiumContainer.value, {
        terrainProvider: new (Cesium as any).EllipsoidTerrainProvider(),
        baseLayerPicker: false,
        geocoder: false,
        homeButton: false,
        infoBox: false,
        selectionIndicator: false,
        navigationHelpButton: false,
        timeline: false,
        animation: false,
        sceneModePicker: false,
        navigationInstructionsInitiallyVisible: false
      });

      // Clear default layers and add Gaode layers
      viewer.imageryLayers.removeAll();
      viewer.imageryLayers.addImageryProvider(satelliteProvider);
      viewer.imageryLayers.addImageryProvider(roadProvider);

      // Hide default credit container logo
      const creditContainer = viewer.cesiumWidget.creditContainer as HTMLElement;
      if (creditContainer) {
        creditContainer.style.display = 'none';
      }

      // 3. Zoom view focused to coordinates [120.0304565, 28.5064125]
      const targetPos = Cesium.Cartesian3.fromDegrees(120.0304565, 28.5064125, 1500); // 1.5km altitude
      viewer.camera.setView({
        destination: targetPos,
        orientation: {
          heading: Cesium.Math.toRadians(0),
          pitch: Cesium.Math.toRadians(-60), // tilted camera
          roll: 0.0
        }
      });

      // Add initial circular core prevention zone marker
      viewer.entities.add({
        position: Cesium.Cartesian3.fromDegrees(120.0304565, 28.5064125, 0),
        ellipse: {
          semiMinorAxis: 400.0,
          semiMajorAxis: 400.0,
          material: Cesium.Color.RED.withAlpha(0.15),
          outline: true,
          outlineColor: Cesium.Color.RED
        }
      });

      // Setup Click Handler for map clicks
      setupMapClicks();

      // Render props devices and lifeguards
      renderDevices(props.devices);
      renderLifeguards(props.lifeguards);
    };

    const setupMapClicks = () => {
      if (!viewer) return;
      clickHandler = new Cesium.ScreenSpaceEventHandler(viewer.scene.canvas);

      // LEFT CLICK ACTION
      clickHandler.setInputAction((click: { position: Cesium.Cartesian2 }) => {
        if (!viewer) return;
        const ray = viewer.camera.getPickRay(click.position);
        if (!ray) return;
        const cartesian = viewer.scene.globe.pick(ray, viewer.scene);

        if (cartesian) {
          if (props.isDrawingLifeguard) {
            const cartographic = Cesium.Cartographic.fromCartesian(cartesian);
            const lng = Cesium.Math.toDegrees(cartographic.longitude);
            const lat = Cesium.Math.toDegrees(cartographic.latitude);
            emit('lifeguard-map-click', { lng, lat });
            return;
          }
          if (isDrawing.value) {
            // Point drawing (register device)
            const cartographic = Cesium.Cartographic.fromCartesian(cartesian);
            const lng = Cesium.Math.toDegrees(cartographic.longitude);
            const lat = Cesium.Math.toDegrees(cartographic.latitude);

            emit('map-click', { lng, lat });
            toggleDrawing(); // Turn off drawing mode after click
          } else if (isDrawingPolygon.value) {
            // Polygon drawing (add warning zone point)
            activePolygonPoints.push(cartesian);
            const pointEnt = viewer.entities.add({
              position: cartesian,
              point: {
                pixelSize: 8,
                color: Cesium.Color.RED,
                outlineColor: Cesium.Color.WHITE,
                outlineWidth: 1
              }
            });
            tempPointsEntities.push(pointEnt);

            // Redraw line
            if (activePolygonPoints.length >= 2) {
              if (activePolylineEntity) {
                viewer.entities.remove(activePolylineEntity);
              }
              activePolylineEntity = viewer.entities.add({
                polyline: {
                  positions: activePolygonPoints,
                  width: 3,
                  material: Cesium.Color.RED
                }
              });
            }
          } else {
            // Regular click on entities
            const pickedObject = viewer.scene.pick(click.position);
            if (Cesium.defined(pickedObject) && pickedObject.id) {
              const entity = pickedObject.id as Cesium.Entity;
              if (entity.name === 'PENDING_ALERT') {
                emit('confirm-alert', entity.properties?.alertId?.getValue());
              } else if (entity.name === 'DEVICE_MARKER') {
                const dev = entity.properties?.deviceData?.getValue();
                emit('select-device', dev);
              }
            }
          }
        }
      }, Cesium.ScreenSpaceEventType.LEFT_CLICK);

      // RIGHT CLICK ACTION (Complete polygon warning zone drawing)
      clickHandler.setInputAction(() => {
        if (!viewer || !isDrawingPolygon.value) return;

        if (activePolygonPoints.length >= 3) {
          // Add warning zone polygon entity
          viewer.entities.add({
            name: 'WARNING_ZONE',
            polygon: {
              hierarchy: new (Cesium as any).PolygonHierarchy(activePolygonPoints),
              material: Cesium.Color.RED.withAlpha(0.2),
              outline: true,
              outlineColor: Cesium.Color.RED,
              outlineWidth: 2
            }
          });

          // Convert points to lat/lng format
          const pointsGeo = activePolygonPoints.map(pos => {
            const carto = Cesium.Cartographic.fromCartesian(pos);
            return {
              lng: Cesium.Math.toDegrees(carto.longitude),
              lat: Cesium.Math.toDegrees(carto.latitude)
            };
          });
          emit('draw-polygon-complete', pointsGeo);
        }

        // Clean up temporary lines & points
        tempPointsEntities.forEach(ent => viewer?.entities.remove(ent));
        tempPointsEntities = [];
        if (activePolylineEntity) {
          viewer.entities.remove(activePolylineEntity);
          activePolylineEntity = null;
        }

        // Reset state
        activePolygonPoints = [];
        isDrawingPolygon.value = false;
        viewer.canvas.style.cursor = '';
      }, Cesium.ScreenSpaceEventType.RIGHT_CLICK);
    };

    const toggleDrawing = () => {
      isDrawing.value = !isDrawing.value;
      if (isDrawing.value) {
        isDrawingPolygon.value = false;
        // Clean up polygon draw if active
        tempPointsEntities.forEach(ent => viewer?.entities.remove(ent));
        tempPointsEntities = [];
        if (activePolylineEntity) {
          viewer?.entities.remove(activePolylineEntity);
          activePolylineEntity = null;
        }
        activePolygonPoints = [];
      }
      if (viewer && viewer.canvas) {
        viewer.canvas.style.cursor = isDrawing.value ? 'crosshair' : '';
      }
    };

    const toggleDrawingPolygon = () => {
      isDrawingPolygon.value = !isDrawingPolygon.value;
      if (isDrawingPolygon.value) {
        isDrawing.value = false;
        activePolygonPoints = [];
        tempPointsEntities.forEach(ent => viewer?.entities.remove(ent));
        tempPointsEntities = [];
        if (activePolylineEntity) {
          viewer?.entities.remove(activePolylineEntity);
          activePolylineEntity = null;
        }
      }
      if (viewer && viewer.canvas) {
        viewer.canvas.style.cursor = isDrawingPolygon.value ? 'crosshair' : '';
      }
    };

    // Render Devices to Cesium 3D View
    const renderDevices = (devices: DeviceData[]) => {
      if (!viewer) return;

      // Clear old devices entities
      entitiesList.forEach(e => {
        if (viewer) viewer.entities.remove(e);
      });
      entitiesList.length = 0;

      devices.forEach(dev => {
        if (!dev.location) return;

        // Parse "POINT(lng lat)"
        const content = dev.location.substring(dev.location.indexOf("(") + 1, dev.location.indexOf(")"));
        const parts = content.split(" ");
        const lng = parseFloat(parts[0]);
        const lat = parseFloat(parts[1]);

        let color = Cesium.Color.SKYBLUE;
        let devLabel = 'CAM';
        if (dev.deviceType === 'SPEAKER') { color = Cesium.Color.LIGHTGREEN; devLabel = 'SPK'; }
        else if (dev.deviceType === 'DRONE_HANGAR') { color = Cesium.Color.VIOLET; devLabel = 'HGR'; }
        else if (dev.deviceType === 'DRONE') { color = Cesium.Color.ORANGE; devLabel = 'UAV'; }

        const entity = viewer.entities.add({
          id: `device-${dev.id}`,
          name: 'DEVICE_MARKER',
          properties: { deviceId: dev.id, deviceData: dev },
          position: Cesium.Cartesian3.fromDegrees(lng, lat, 10),
          label: {
            text: `[${devLabel}] ${dev.deviceName}\n[${dev.status}]`,
            font: '11px sans-serif',
            fillColor: Cesium.Color.WHITE,
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new Cesium.Cartesian2(0, -10),
            backgroundColor: Cesium.Color.fromCssColorString('#1e293b').withAlpha(0.85),
            showBackground: true
          },
          point: {
            pixelSize: 10,
            color: color,
            outlineColor: Cesium.Color.WHITE,
            outlineWidth: 2
          }
        });
        entitiesList.push(entity);

        // Render 1-to-1 bound Patrol Zone Polygon if configured
        if (dev.extraConfig && dev.extraConfig.roi_polygon && Array.isArray(dev.extraConfig.roi_polygon) && dev.extraConfig.roi_polygon.length >= 3) {
          const polyCartesians = dev.extraConfig.roi_polygon.map((pt: any) => Cesium.Cartesian3.fromDegrees(pt.lng, pt.lat, 5));
          const polyEntity = viewer.entities.add({
            id: `patrol-zone-${dev.id}`,
            name: 'PATROL_ZONE_POLYGON',
            polygon: {
              hierarchy: new (Cesium as any).PolygonHierarchy(polyCartesians),
              material: Cesium.Color.CYAN.withAlpha(0.25),
              outline: true,
              outlineColor: Cesium.Color.CYAN,
              outlineWidth: 2
            },
            label: {
              text: `[监控巡逻区域] ${dev.deviceName}`,
              font: '11px sans-serif',
              fillColor: Cesium.Color.CYAN,
              style: Cesium.LabelStyle.FILL_AND_OUTLINE,
              outlineColor: Cesium.Color.BLACK,
              outlineWidth: 2,
              showBackground: true,
              backgroundColor: Cesium.Color.fromCssColorString('#0f172a').withAlpha(0.85),
              verticalOrigin: Cesium.VerticalOrigin.CENTER
            }
          });
          entitiesList.push(polyEntity);
        }
      });
    };

    // Render Lifeguards to Cesium 3D View
    const renderLifeguards = (lifeguards: any[]) => {
      if (!viewer) return;

      // Clear old lifeguards entities
      lifeguardsEntitiesList.forEach(e => {
        if (viewer) viewer.entities.remove(e);
      });
      lifeguardsEntitiesList.length = 0;

      lifeguards.forEach(user => {
        if (!user.lastWktLocation) return;

        // Parse "POINT(lng lat)"
        const content = user.lastWktLocation.substring(user.lastWktLocation.indexOf("(") + 1, user.lastWktLocation.indexOf(")"));
        const parts = content.split(" ");
        const lng = parseFloat(parts[0]);
        const lat = parseFloat(parts[1]);

        const isOnDuty = user.dutyStatus === 1;
        const statusText = isOnDuty ? '在岗' : '下岗';
        const color = isOnDuty ? Cesium.Color.GOLD : Cesium.Color.GRAY;
        const labelFillColor = isOnDuty ? Cesium.Color.WHITE : Cesium.Color.LIGHTGRAY;
        const labelBgColor = isOnDuty
          ? Cesium.Color.fromCssColorString('#0f172a').withAlpha(0.9)
          : Cesium.Color.fromCssColorString('#334155').withAlpha(0.95);

        const entity = viewer.entities.add({
          id: `lifeguard-${user.id}`,
          name: 'LIFEGUARD_MARKER',
          properties: { lifeguardId: user.id, lifeguardData: user },
          position: Cesium.Cartesian3.fromDegrees(lng, lat, 15),
          label: {
            text: `[救生员] ${user.realName}\n[${statusText}] - ${user.phone || '无电话'}`,
            font: '11px sans-serif',
            fillColor: labelFillColor,
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new Cesium.Cartesian2(0, -12),
            backgroundColor: labelBgColor,
            showBackground: true
          },
          point: {
            pixelSize: isOnDuty ? 14 : 12,
            color: color,
            outlineColor: isOnDuty ? Cesium.Color.WHITE : Cesium.Color.DARKGRAY,
            outlineWidth: 2
          }
        });
        lifeguardsEntitiesList.push(entity);
      });
    };

    // Highlight alert coordinates dynamically on Cesium globe
    let activeAlertEntity: Cesium.Entity | null = null;
    const highlightAlert = (alert: any) => {
      if (!viewer) return;

      if (activeAlertEntity) {
        viewer.entities.remove(activeAlertEntity);
        activeAlertEntity = null;
      }

      if (!alert || !alert.alertLocation) return;

      const content = alert.alertLocation.substring(alert.alertLocation.indexOf("(") + 1, alert.alertLocation.indexOf(")"));
      const parts = content.split(" ");
      const lng = parseFloat(parts[0]);
      const lat = parseFloat(parts[1]);

      const locText = `防区: ${alert.waterAreaName || '重点防区'}\n点位: ${alert.sourceDeviceName || '未命名点位'}`;
      // Add blink alarm entity
      activeAlertEntity = viewer.entities.add({
        name: 'PENDING_ALERT',
        position: Cesium.Cartesian3.fromDegrees(lng, lat, 20),
        properties: { alertId: alert.id },
        label: {
          text: `[警告] ${locText}\n事件: ${alert.alertType === 'DROWNING' ? '人员意外落水/疑似溺水' : alert.alertType} (置信度 ${alert.confidence}%)\n[点击确认指派救援]`,
          font: '13px bold sans-serif',
          fillColor: Cesium.Color.RED,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          outlineColor: Cesium.Color.WHITE,
          outlineWidth: 2,
          pixelOffset: new Cesium.Cartesian2(0, -25),
          showBackground: true,
          backgroundColor: Cesium.Color.fromCssColorString('#0f172a').withAlpha(0.9)
        },
        ellipse: {
          semiMinorAxis: 100.0,
          semiMajorAxis: 100.0,
          material: Cesium.Color.RED.withAlpha(0.4),
          outline: true,
          outlineColor: Cesium.Color.WHITE
        }
      });

      // Fly to coordinate
      viewer.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(lng, lat, 500),
        duration: 1.5
      });
    };

    // Watchers for reactive props updates
    watch(() => props.devices, (newVal) => {
      renderDevices(newVal);
    }, { deep: true });

    watch(() => props.lifeguards, (newVal) => {
      renderLifeguards(newVal);
    }, { deep: true });

    watch(() => props.pendingAlert, (newVal) => {
      highlightAlert(newVal);
    });

    watch(() => props.isDrawingLifeguard, (newVal) => {
      if (viewer && viewer.canvas) {
        viewer.canvas.style.cursor = newVal ? 'crosshair' : '';
      }
    });

    onMounted(() => {
      initCesium();
    });

    return {
      cesiumContainer,
      isDrawing,
      toggleDrawing,
      isDrawingPolygon,
      toggleDrawingPolygon
    };
  }
});
</script>

<style scoped>
.map-panel {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
  position: relative;
  box-shadow: inset 0 0 20px rgba(0,0,0,0.3);
}

.cesium-container {
  width: 100%;
  flex: 1;
  min-height: 0;
}

.map-controls {
  position: absolute;
  top: 72px;
  left: 16px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.alarm-banner-overlay {
  position: absolute;
  top: 68px;
  left: 16px;
  right: 16px;
  z-index: 101;
  background: rgba(62, 22, 36, 0.96);
  color: #ffe3e7;
  border-radius: 6px;
  box-shadow: 0 8px 24px #0005;
  padding: 12px 16px;
  border: 1px solid rgba(255, 120, 136, 0.55);
}

.alarm-banner-content {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  flex-wrap: wrap;
}

.alarm-blink-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.alarm-msg-text {
  flex: 1;
  min-width: 160px;
  line-height: 1.8;
}

.alarm-confirm-btn {
  background: #a83d50 !important;
  color: #fff1f3 !important;
  font-weight: bold;
  border: none;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 12px;
}

.alarm-confirm-btn:hover {
  background: #c54d62 !important;
}

@keyframes alarm-blink-anim {
  0%, 100% { box-shadow: 0 4px 20px rgba(239, 68, 68, 0.4); }
  50% { box-shadow: 0 4px 30px rgba(239, 68, 68, 0.8), 0 0 15px rgba(239, 68, 68, 0.6); }
}

@keyframes icon-scale-anim {
  0% { transform: scale(1); }
  100% { transform: scale(1.2); }
}
</style>
