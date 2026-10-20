<template>
  <div class="dashboard-wrapper">
    <!-- Header bar -->
    <header class="header-bar">
      <div class="brand">
        <div class="brand-logo">
          <svg viewBox="0 0 32 32" fill="none" aria-hidden="true">
            <path d="M16 3 27 7v8c0 7-5 11-11 14C10 26 5 22 5 15V7Z" stroke="currentColor" stroke-width="1.5" fill="none"/>
            <path d="M10 15c3-5 9 5 12 0M10 20c3-5 9 5 12 0" stroke="currentColor" stroke-width="1.5" fill="none"/>
            <path d="M16 8v3" stroke="currentColor" stroke-width="1.5"/>
          </svg>
        </div>
        <div class="brand-title">
          <h1>黄村乡防溺水安全监管系统</h1>
          <p>HUANGCUN · WATER SAFETY COMMAND CENTER</p>
        </div>
      </div>

      <div class="nav-tabs">
        <button class="nav-tab" :class="{ active: currentTab === 'monitor' }" @click="switchTab('monitor')">实时监控</button>
        <button class="nav-tab" :class="{ active: currentTab === 'wall' }" @click="switchTab('wall')">监控分发墙</button>
        <button class="nav-tab" :class="{ active: currentTab === 'alerts' }" @click="switchTab('alerts')">事件中心</button>
        <button class="nav-tab" :class="{ active: currentTab === 'recordings' }" @click="switchTab('recordings')">录像中心</button>
        <button class="nav-tab" :class="{ active: currentTab === 'users' }" @click="switchTab('users')">人物管理</button>
        <button class="nav-tab" :class="{ active: currentTab === 'devices' }" @click="switchTab('devices')">设备管理</button>
        <button class="nav-tab" :class="{ active: currentTab === 'tasks' }" @click="switchTab('tasks')">任务中心</button>
      </div>

      <div class="user-profile">
        <DistributionLink />
        <div class="user-info">
          <div class="user-name">{{ user.realName || user.username }}</div>
          <div class="user-role">{{ translateRole(user.role) }}</div>
        </div>
        <button class="btn-logout" @click="handleLogout">安全退出</button>
      </div>
    </header>

    <!-- Lifeguard Placement Alert Overlay -->
    <div v-if="isDrawingLifeguard" class="alarm-banner-overlay" style="position: relative; top: 0; left: 0; right: 0; z-index: 1000; background: rgba(234, 179, 8, 0.15); border-bottom: 2px solid var(--color-primary); margin-top: 2px;">
      <div class="alarm-banner-content" style="display:flex; justify-content:space-between; align-items:center; width:100%; max-width:1200px; margin:0 auto; padding:12px 24px; box-sizing: border-box;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span class="alarm-blink-icon" style="font-size:16px;"><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/></svg></span>
          <span class="alarm-msg-text" style="font-size:14px; color: var(--color-primary); font-weight: 500;">
            正在为救生员 <strong>[{{ mockLocationUser?.realName }}]</strong> 进行三维坐标定位打点。<strong>请在三维地图上直接点击所需定位的坐标位置...</strong>
          </span>
        </div>
        <button class="alarm-confirm-btn" style="background: var(--bg-card); color: var(--text-color); border: 1px solid var(--border-color); padding: 6px 12px; margin:0; border-radius:4px; cursor: pointer;" @click="cancelMockLocation">
          取消打点
        </button>
      </div>
    </div>

    <!-- Main Container -->
    <div class="dashboard-container" :class="{ 'is-monitor': currentTab === 'monitor' }">

      <!-- 实时监控 PANEL -->
      <div v-show="currentTab === 'monitor'" class="main-content monitor-stage">
        <div class="overview-heading monitor-heading">
          <div class="monitor-heading-main">
            <span class="monitor-signal" aria-hidden="true">
              <svg viewBox="0 0 40 40" fill="none"><circle cx="20" cy="20" r="16"/><circle cx="20" cy="20" r="9"/><path d="M20 4v32M4 20h32"/><circle cx="20" cy="20" r="2" fill="currentColor" stroke="none"/></svg>
            </span>
            <div><span class="eyebrow">SPATIAL COMMAND / HUANGCUN</span><h2>全域安全态势</h2></div>
          </div>
          <span class="overview-meta">水域感知<br>协同守护</span>
        </div>
        <!-- KPI Cards Row -->
        <div class="kpi-row">
          <div class="kpi-card">
            <div>
              <div class="kpi-label">在线监控点</div>
              <div class="kpi-value">{{ kpis.onlineDeviceCount }}<span>/{{ kpis.totalDeviceCount }}</span></div>
              <div class="kpi-trend">运行状态稳定</div>
            </div>
            <div class="kpi-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--primary)"><path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/></svg>
            </div>
          </div>
          <div class="kpi-card">
            <div>
              <div class="kpi-label">实时涉水人数</div>
              <div class="kpi-value">{{ kpis.peopleCount }}</div>
              <div class="kpi-trend up">实时动态监测</div>
            </div>
            <div class="kpi-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--primary)"><path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5s-3 1.34-3 3 1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/></svg>
            </div>
          </div>
          <div class="kpi-card">
            <div>
              <div class="kpi-label">今日告警数</div>
              <div class="kpi-value">{{ kpis.pendingAlertCount }}</div>
              <div class="kpi-trend" :class="kpis.pendingAlertCount > 0 ? 'up' : 'down'">
                {{ kpis.pendingAlertCount > 0 ? `待确认: ${kpis.pendingAlertCount}起` : '暂无待确认' }}
              </div>
            </div>
            <div class="kpi-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--danger)"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
            </div>
          </div>
          <div class="kpi-card">
            <div>
              <div class="kpi-label">今日救援完成</div>
              <div class="kpi-value">{{ kpis.completedRescueCount }}</div>
              <div class="kpi-trend">救援处置闭环</div>
            </div>
            <div class="kpi-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--success)"><path d="M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L14 19v-5.5l7 2.5z"/></svg>
            </div>
          </div>
          <div class="kpi-card">
            <div>
              <div class="kpi-label">在岗救生力量</div>
              <div class="kpi-value">{{ kpis.lifeguardCount }}</div>
              <div class="kpi-trend">实时GPS追踪</div>
            </div>
            <div class="kpi-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="var(--primary)"><path d="M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z"/></svg>
            </div>
          </div>
        </div>

        <!-- 3D Cesium Map Component -->
        <CesiumMap
          ref="cesiumMapRef"
          :devices="deviceList"
          :pendingAlert="activeAlert"
          :lifeguards="lifeguardList"
          :isDrawingLifeguard="isDrawingLifeguard"
          @map-click="handleMapClick"
          @confirm-alert="handleConfirmAlertClick"
          @select-device="handleSelectDevice"
          @draw-polygon-complete="handleDrawPolygonComplete"
          @lifeguard-map-click="handleLifeguardMapClick"
        />
      </div>

      <!-- 监控分发墙 PANEL -->
      <div v-show="currentTab === 'wall'" class="main-content" style="display: flex; flex-direction: column; height: 100%; gap: 16px; box-sizing: border-box; padding: 16px;">
        <!-- Tool Bar -->
        <div class="wall-toolbar">
          <div style="display: flex; align-items: center; gap: 14px;">
            <span style="font-weight: 700; font-size: 14px; color: var(--text-white); display: flex; align-items: center; gap: 6px;">
              分屏模式:
            </span>
            <div style="display: flex; gap: 8px; align-items: center;">
              <button class="btn-grid" :class="{ active: wallGridSize === 1 }" @click="setWallGridSize(1)">单屏</button>
              <button class="btn-grid" :class="{ active: wallGridSize === 4 }" @click="setWallGridSize(4)">四分屏</button>
              <button class="btn-grid" :class="{ active: wallGridSize === 9 }" @click="setWallGridSize(9)">九分屏</button>
              <button class="btn-grid" @click="toggleWallFullscreen" style="background: linear-gradient(135deg, var(--violet), #6d28d9); color: white !important; border: none; font-weight: 700; margin-left: 6px; box-shadow: 0 2px 8px rgba(124,58,237,0.3);">
                <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/></svg> {{ isWallFullscreen ? '退出全屏' : '全屏观看' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Grid Wall -->
        <div id="wall-grid-container" class="wall-grid-container" :class="'grid-' + wallGridSize">
          <div v-for="index in wallGridSize" :key="index - 1" :id="'wall-cell-' + (index - 1)" class="wall-cell" :class="{ 'has-drowning': checkSlotDrowning(wallSlots[index - 1]) }">

            <!-- Video Container -->
            <div class="wall-video-wrapper" style="flex-grow: 1; position: relative; background: #000; display: flex; align-items: center; justify-content: center;">
              <canvas :id="'wall-canvas-' + (index - 1)" style="width: 100%; height: 100%; object-fit: contain; display: block;"></canvas>

              <!-- Empty / Setup Placeholder -->
              <div v-if="!wallSlots[index - 1].deviceId" class="empty-slot-placeholder" style="position: absolute; display: flex; flex-direction: column; align-items: center; gap: 10px; color: var(--text-muted);">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor"><path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4zM14 13h-3v3H9v-3H6v-2h3V8h2v3h3v2z"/></svg>
                <select @change="assignDeviceToSlot(index - 1, $event)" style="background: rgba(15, 23, 42, 0.85); color: #fff; border: 1px solid var(--border-color); padding: 6px 12px; border-radius: 6px; font-size: 12px; cursor: pointer; outline: none;">
                  <option value="">绑定监控通道...</option>
                  <option v-for="cam in availableCameras" :key="cam.id" :value="cam.id">{{ cam.deviceName }}</option>
                </select>
              </div>

              <!-- Warning Overlays -->
              <div v-if="checkSlotDrowning(wallSlots[index - 1])" class="drowning-overlay-alert" style="position: absolute; top: 12px; right: 12px; background: rgba(239, 68, 68, 0.95); color: white; padding: 6px 12px; border-radius: 6px; font-weight: bold; font-size: 11px; z-index: 10; display: flex; align-items: center; gap: 6px; box-shadow: 0 0 10px rgba(239,68,68,0.5);">
                <span style="width: 8px; height: 8px; background: white; border-radius: 50%; display: inline-block;"></span>
                检测到人员溺水行为！
              </div>
            </div>

            <!-- Cell Info / Controls Footer -->
            <div v-if="wallSlots[index - 1].deviceId" class="wall-cell-footer">
              <div style="display: flex; align-items: center; gap: 8px;">
                <select @change="assignDeviceToSlot(index - 1, $event)" style="background: transparent; color: var(--text-white); border: none; font-weight: 600; padding: 2px 4px; cursor: pointer; max-width: 140px; text-overflow: ellipsis; outline: none;">
                  <option v-for="cam in cameraDevices" :key="cam.id" :value="cam.id" :selected="cam.id === wallSlots[index - 1].deviceId">{{ cam.deviceName }}</option>
                </select>
                <span style="color: var(--text-muted); font-size: 10px;">{{ getSlotFpsInfo(wallSlots[index - 1]) }}</span>
              </div>

              <div style="display: flex; align-items: center; gap: 8px;">
                <span style="color: var(--text-muted); font-size: 11px;">监测模型:</span>
                <span style="color: var(--text-white); font-size: 11px; font-family: monospace;">{{ getSlotModel(wallSlots[index - 1]) }}</span>
                <button @click="toggleCellFullscreen(index - 1)" title="当前画面全屏" style="background: transparent; border: none; color: var(--violet); cursor: pointer; padding: 2px; display: flex; align-items: center; font-size: 13px; font-weight: bold; margin-right: 2px;">
                  <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5"/></svg>
                </button>
                <button @click="unbindSlot(index - 1)" title="解绑通道" style="background: transparent; border: none; color: var(--text-muted); cursor: pointer; padding: 2px; display: flex; align-items: center;">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 事件中心 PANEL -->
      <div v-show="currentTab === 'alerts'" class="main-content">
        <div class="panel-tab-content">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <div class="section-title">事件中心 (实时预警与处置控制)</div>
            <button class="btn-action primary" style="padding: 6px 12px; font-size:12px;" @click="exportAlertsCSV">导出事件数据 (CSV)</button>
          </div>
          <table class="custom-table">
            <thead>
              <tr>
                <th>事件编号</th>
                <th>预警类型</th>
                <th>置信度</th>
                <th>所属水域</th>
                <th>处理状态</th>
                <th>触发时间</th>
                <th style="width: 190px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in alertList" :key="item.id">
                <td>{{ item.eventNo }}</td>
                <td><b>{{ item.alertType === 'DROWNING' ? '溺水预警' : item.alertType }}</b></td>
                <td><span style="font-family:monospace; font-weight:600; color:var(--danger)">{{ item.confidence }}%</span></td>
                <td>{{ item.waterAreaName || '未知防区' }}</td>
                <td>
                  <span class="badge-risk" :class="getAlertStatusClass(item.status)">
                    {{ getAlertStatusText(item.status) }}
                  </span>
                </td>
                <td>{{ item.createdAt }}</td>
                <td>
                  <template v-if="item.status === 'PENDING'">
                    <button class="btn-action primary" style="padding: 4px 8px; font-size:11px; background:var(--success); border-color:var(--success);" @click="confirmAlertEvent(item, true)">标记为已处理</button>
                    <button class="btn-action" style="padding: 4px 8px; font-size:11px; margin-left:4px;" @click="confirmAlertEvent(item, false)">误报</button>
                  </template>
                  <template v-else>
                    <span style="color:var(--success); font-weight:600; font-size:12px; margin-right:8px;"><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg> 已处理</span>
                  </template>
                  <button class="btn-action" style="padding: 4px 8px; font-size:11px; margin-left:4px; border-color: var(--danger); color: var(--danger);" @click="deleteAlertEvent(item.id)">删除</button>
                </td>
              </tr>
              <tr v-if="alertList.length === 0">
                <td colspan="7" style="text-align: center; color: var(--text-muted); padding: 20px;">暂无待处理事件记录</td>
              </tr>
            </tbody>
          </table>

          <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 32px; margin-bottom: 12px;">
            <div class="section-title" style="margin:0; border-left: 4px solid var(--primary); padding-left: 8px;">智能分析与目标监测历史记录</div>
            <div>
              <button class="btn-action" style="padding: 6px 12px; font-size:12px; margin-right: 8px;" @click="exportDetectionsCSV">导出监测历史 (CSV)</button>
              <button class="btn-action" style="padding: 6px 12px; font-size:12px; border-color: var(--danger); color: var(--danger);" @click="clearAllDetections">清空历史</button>
            </div>
          </div>
          <table class="custom-table">
            <thead>
              <tr>
                <th>通道编号</th>
                <th>监测设备名称</th>
                <th>当前载入模型</th>
                <th>智能检测分析内容</th>
                <th>计算置信度</th>
                <th>更新时间</th>
                <th style="width: 80px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, idx) in historicalDetections" :key="idx">
                <td style="font-family: monospace;">{{ item.stream_id }}</td>
                <td>{{ getDeviceNameByCode(item.stream_id) }}</td>
                <td><span style="font-family: monospace; font-size: 11px; padding: 2px 6px; background: rgba(59, 130, 246, 0.1); color: var(--primary); border-radius: 4px;">{{ getDeviceModelByCode(item.stream_id) }}</span></td>
                <td><b :style="{ color: (/drown|drowing|溺水|落水/i.test((item.event_name || '') + ' ' + (item.details || '')) || (item.details && item.details.includes('溺水姿态'))) ? 'var(--danger)' : 'var(--text-white)' }">{{ (/drown|drowing|溺水|落水/i.test((item.event_name || '') + ' ' + (item.details || '')) || (item.details && (item.details.includes('溺水姿态') || item.details.includes('警告')))) ? '警告！AI算法识别到溺水姿态' : (item.details || item.event_name || '涉水人员') }}</b></td>
                <td>
                  <span style="font-family: monospace; font-weight: 600; color: var(--success);">
                    {{ (item.confidence <= 1 ? item.confidence : item.confidence / 100).toFixed(2) }}
                  </span>
                </td>
                <td>{{ item.timestamp }}</td>
                <td>
                  <button class="btn-action" style="padding: 2px 6px; font-size:11px; color: var(--danger); border-color: var(--danger);" @click="deleteDetectionItem(item.id)">删除</button>
                </td>
              </tr>
              <tr v-if="historicalDetections.length === 0">
                <td colspan="7" style="text-align: center; color: var(--text-muted); padding: 20px;">暂无智能目标监测历史记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 录像中心 PANEL -->
      <div v-show="currentTab === 'recordings'" class="main-content">
        <div class="panel-tab-content">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
            <div class="section-title">AI 阈值触发抓拍与实时录像归档中心</div>
            <div>
              <button class="btn-action" style="padding: 8px 16px; margin-right: 8px;" @click="loadRecordings">刷新录像</button>
              <button class="btn-action primary" style="padding: 8px 16px; margin-right: 8px;" @click="exportRecordingsCSV">导出录像清单 (CSV)</button>
              <button class="btn-action" style="padding: 8px 16px; border-color: var(--danger); color: var(--danger);" @click="clearAllRecordings">清空全量录像</button>
            </div>
          </div>
          <table class="custom-table">
            <thead>
              <tr>
                <th style="width: 60px;">ID</th>
                <th>监控通道/设备</th>
                <th>设定触发阈值</th>
                <th>最高检测置信度</th>
                <th>录像时长</th>
                <th>文件大小</th>
                <th>触发与归档原因</th>
                <th>生成时间</th>
                <th style="width: 220px;">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="rec in recordingList" :key="rec.id">
                <td style="font-family: monospace;">#{{ rec.id }}</td>
                <td><b>{{ getDeviceNameByCode(rec.stream_id) }}</b> <span style="font-size:11px; color:var(--text-muted);">({{ rec.stream_id }})</span></td>
                <td><span style="color:var(--primary); font-weight:600; font-family: monospace;">{{ (rec.threshold <= 1 ? rec.threshold : rec.threshold / 100).toFixed(2) }}</span></td>
                <td><span style="color:var(--danger); font-weight:600; font-family: monospace;">{{ (rec.max_confidence <= 1 ? rec.max_confidence : rec.max_confidence / 100).toFixed(2) }}</span></td>
                <td>{{ rec.duration }} 秒</td>
                <td>{{ (rec.file_size / (1024 * 1024)).toFixed(2) }} MB</td>
                <td style="font-size:12px; color:var(--text-white);">{{ rec.trigger_reason }}</td>
                <td>{{ rec.created_at }}</td>
                <td>
                  <button class="btn-action primary" style="padding: 4px 8px; font-size:11px; margin-right:4px;" @click="playVideoModal(rec)"><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m8 4 12 8-12 8V4Z"/></svg> 播放</button>
                  <button class="btn-action" style="padding: 4px 8px; font-size:11px; margin-right:4px;" @click="downloadVideo(rec)"><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v12m-4-4 4 4 4-4M4 16v5h16v-5"/></svg> 下载</button>
                  <button class="btn-action" style="padding: 4px 8px; font-size:11px; border-color:var(--danger); color:var(--danger);" @click="deleteRecordingItem(rec.id)">删除</button>
                </td>
              </tr>
              <tr v-if="recordingList.length === 0">
                <td colspan="9" style="text-align: center; color: var(--text-muted); padding: 30px;">暂无触发阈值录像归档记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 人物管理 PANEL -->
      <div v-show="currentTab === 'users'" class="main-content">
        <div class="panel-tab-content">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
            <div class="section-title">系统人物与在岗救生员管理</div>
            <button class="btn-action primary" @click="openAddUserModal" style="margin: 0; padding: 8px 16px;">创建人物</button>
          </div>
          <table class="custom-table">
            <thead>
              <tr>
                <th style="width: 60px;">ID</th>
                <th>用户名</th>
                <th>真实姓名</th>
                <th>联系电话</th>
                <th>人物类型 / 系统角色</th>
                <th>绑定的责任监控点 (支持多对多)</th>
                <th>在岗状态</th>
                <th>最新三维GPS位置</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="usr in userList" :key="usr.id">
                <td>{{ usr.id }}</td>
                <td>{{ usr.username }}</td>
                <td><b>{{ usr.realName }}</b></td>
                <td>{{ usr.phone || '未记录' }}</td>
                <td>
                  <span class="user-role" style="background: rgba(16, 185, 129, 0.1); color: var(--success); border: 1px solid rgba(16, 185, 129, 0.2); padding: 2px 6px; border-radius: 4px;">
                    {{ getRoleLabel(usr.roleCode) }}
                  </span>
                </td>
                <td>
                  <span v-if="usr.deviceIds && usr.deviceIds.length > 0" style="display:flex; flex-wrap:wrap; gap:4px;">
                    <span v-for="devId in usr.deviceIds" :key="devId" style="background: rgba(59, 130, 246, 0.1); color: #3b82f6; border: 1px solid rgba(59, 130, 246, 0.2); padding: 1px 4px; border-radius: 3px; font-size: 10px;">
                      {{ getDeviceNameById(devId) }}
                    </span>
                  </span>
                  <span v-else style="color: var(--text-muted);">暂无绑定</span>
                </td>
                <td>
                  <span v-if="usr.roleCode === 'ROLE_RESCUER'">
                    <span class="badge-risk" :class="usr.dutyStatus === 1 ? 'low' : 'high'" style="padding: 2px 6px;">
                      {{ usr.dutyStatus === 1 ? '在岗在线' : '下班离岗' }}
                    </span>
                  </span>
                  <span v-else style="color: var(--text-muted); font-size:11px;">非外勤岗位</span>
                </td>
                <td>
                  <span v-if="usr.lastWktLocation" class="coord-text" @click="focusOnCoordinates(usr.lastWktLocation)" style="cursor:pointer; color: var(--primary); font-family: monospace; font-size:11px; font-weight:600; text-decoration: underline;" title="点击复制并定位到该坐标">
                    {{ usr.lastWktLocation }}
                  </span>
                  <span v-else style="color: var(--text-muted); font-size:11px;">未标记定位</span>
                </td>
                <td>
                  <div style="display: flex; gap: 6px;">
                    <button class="btn-action small" @click="openEditUserModal(usr)" style="padding: 2px 6px; font-size:11px; margin: 0; min-height:0;">编辑</button>
                    <button v-if="usr.roleCode === 'ROLE_RESCUER'" class="btn-action small" :style="{ padding: '2px 6px', fontSize: '11px', margin: 0, minHeight: 0, borderColor: usr.dutyStatus === 1 ? 'var(--danger)' : 'var(--success)', color: usr.dutyStatus === 1 ? 'var(--danger)' : 'var(--success)' }" @click="toggleUserDutyStatus(usr)">
                      {{ usr.dutyStatus === 1 ? '下岗' : '上岗' }}
                    </button>
                    <button class="btn-logout" @click="handleDeleteUser(usr.id)" style="padding: 2px 6px; font-size:11px; margin: 0; min-height: 0;">删除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="userList.length === 0">
                <td colspan="9" style="text-align: center; color: var(--text-muted); padding: 24px;">暂无系统人物数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 设备管理 PANEL -->
      <div v-show="currentTab === 'devices'" class="main-content">
        <div class="panel-tab-content">
          <div class="section-title">设备注册及工况总览</div>
          <table class="custom-table">
            <thead>
              <tr>
                <th>编码</th>
                <th>设备/监控点名称</th>
                <th>类型</th>
                <th>IP/端口</th>
                <th>地图点位坐标</th>
                <th>1对1监控巡逻区域</th>
                <th>状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dev in deviceList" :key="dev.id">
                <td style="font-family: monospace;">{{ dev.deviceCode }}</td>
                <td><b>{{ dev.deviceName }}</b></td>
                <td><span class="user-role">{{ dev.deviceType }}</span></td>
                <td style="font-family: monospace;">{{ dev.ipAddress }}:{{ dev.port }}</td>
                <td>
                  <span v-if="dev.location" class="coord-text" @click="focusDeviceOnMap(dev)" style="cursor:pointer; color: var(--primary); font-family: monospace; font-size:11px; font-weight:600; text-decoration: underline;" title="点击切换至地图该视角">
                    {{ dev.location }}
                  </span>
                  <span v-else style="color: var(--text-muted); font-size:11px;">未标定打点</span>
                </td>
                <td>
                  <span v-if="dev.extraConfig && dev.extraConfig.roi_polygon && dev.extraConfig.roi_polygon.length >= 3" style="color: var(--success); font-weight:600; font-size:11px;">
                    已绑定 ({{ dev.extraConfig.roi_polygon.length }}拐点)
                  </span>
                  <span v-else style="color: var(--text-muted); font-size:11px;">未绘制区域</span>
                </td>
                <td><span class="badge-risk" :class="dev.status === 'ONLINE' ? 'low' : 'high'">{{ dev.status }}</span></td>
                <td>
                  <div style="display:flex; gap:6px;">
                    <button class="btn-action small" style="padding: 2px 6px; font-size:11px; margin:0;" @click="openEditDeviceModal(dev)">编辑</button>
                    <button class="btn-action small primary" style="padding: 2px 6px; font-size:11px; margin:0;" @click="focusDeviceOnMap(dev)">定位</button>
                    <button class="btn-logout" style="padding: 2px 6px; font-size:11px; margin:0;" @click="handleDeleteDevice(dev)">删除</button>
                  </div>
                </td>
              </tr>
              <tr v-if="deviceList.length === 0">
                <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 24px;">暂无监控点位与设备数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 任务中心 PANEL -->
      <div v-show="currentTab === 'tasks'" class="main-content">
        <div class="panel-tab-content" style="padding: 16px; display: flex; flex-direction: column; gap: 16px; height: 100%; box-sizing: border-box;">

          <!-- Top Toolbar & Status Filter Tabs -->
          <div style="display: flex; justify-content: space-between; align-items: center; background: var(--bg-card); padding: 12px 16px; border-radius: 8px; border: 1px solid var(--border-color); box-shadow: 0 1px 3px rgba(0,0,0,0.04);">
            <div class="section-title" style="margin: 0; display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 15px; font-weight: 700; color: var(--text-white);">AI 风险检测任务中心</span>
              <span style="font-size: 11px; color: var(--text-muted); font-weight: normal;">(风险切片自动产生派发 & 人工标注核验控制台)</span>
            </div>

            <div style="display: flex; gap: 8px; align-items: center;">
              <button class="btn-grid" :class="{ active: taskStatusFilter === 'ALL' }" @click="taskStatusFilter = 'ALL'">全部任务 ({{ taskCounts.total }})</button>
              <button class="btn-grid" :class="{ active: taskStatusFilter === 'PENDING' }" @click="taskStatusFilter = 'PENDING'" style="color: #f59e0b; font-weight: 600;">未处理 ({{ taskCounts.pending }})</button>
              <button class="btn-grid" :class="{ active: taskStatusFilter === 'PROCESSED' }" @click="taskStatusFilter = 'PROCESSED'" style="color: var(--success); font-weight: 600;">已处理 ({{ taskCounts.processed }})</button>
              <button class="btn-grid" :class="{ active: taskStatusFilter === 'RECOGNITION_ERROR' }" @click="taskStatusFilter = 'RECOGNITION_ERROR'" style="color: var(--danger); font-weight: 600;">识别错误 ({{ taskCounts.error }})</button>
            </div>
          </div>

          <!-- Task Cards Grid -->
          <div class="tasks-card-grid" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; overflow-y: auto; flex: 1; padding-right: 4px;">

            <div v-for="task in filteredTasksList" :key="task.id" class="task-card" style="background: var(--bg-card); border-radius: 10px; border: 1px solid var(--border-color); box-shadow: 0 2px 8px rgba(0,0,0,0.05); padding: 16px; display: flex; flex-direction: column; gap: 12px; position: relative;">

              <!-- Warning Banner -->
              <div style="background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: 6px; padding: 8px 10px; display: flex; align-items: center; justify-content: space-between;">
                <span style="color: var(--danger); font-weight: 700; font-size: 12px; display: flex; align-items: center; gap: 6px;">
                  <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 10 18H2L12 3Zm0 6v5m0 3v.1"/></svg> 警告！AI算法高置信度切片识别到溺水姿态
                </span>
                <span style="font-family: monospace; font-size: 12px; color: var(--danger); font-weight: 700;">
                  {{ task.confidence ? (task.confidence > 1 ? task.confidence + '%' : (task.confidence * 100).toFixed(1) + '%') : '92.50%' }}
                </span>
              </div>

              <!-- Task Header -->
              <div style="display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid var(--border-color); padding-bottom: 8px;">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="font-family: monospace; font-weight: 700; font-size: 13px; color: var(--text-white);">#{{ task.taskNo || task.eventNo || ('TASK_' + task.id) }}</span>
                  <span class="user-role" style="background: rgba(109, 40, 217, 0.08); color: var(--violet); border: 1px solid rgba(109, 40, 217, 0.2); font-size: 10px; padding: 1px 6px;">
                    {{ task.alertType === 'DROWNING' ? '溺水求救预警' : (task.alertType || '风险阈值告警') }}
                  </span>
                </div>

                <!-- Status Badge -->
                <span class="badge-risk" :class="getTaskStatusClass(task.status)">
                  {{ getTaskStatusText(task.status) }}
                </span>
              </div>

              <!-- Device & Location Info Section -->
              <div style="background: var(--bg-input); border-radius: 6px; padding: 10px; border: 1px solid var(--border-color); display: flex; flex-direction: column; gap: 6px; font-size: 12px;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span style="color: var(--text-muted); font-weight: 600;">监控点/设备名称:</span>
                  <span style="color: var(--text-white); font-weight: 700;"><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z"/><circle cx="12" cy="10" r="2"/></svg> {{ task.sourceDeviceName || getDeviceNameById(task.sourceDeviceId) || '关联水域监控点' }}</span>
                </div>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span style="color: var(--text-muted); font-weight: 600;">三维地图点位坐标:</span>
                  <div style="display: flex; align-items: center; gap: 6px;">
                    <span style="font-family: monospace; color: var(--primary); font-weight: 600; font-size: 11px;">
                      {{ formatLocationCoords(task.taskLocation || task.alertLocation || getDeviceLocationById(task.sourceDeviceId)) }}
                    </span>
                    <button class="btn-action small primary" style="padding: 2px 6px; font-size: 10px; margin: 0; color: #ffffff !important;" @click="focusOnCoordinates(task.taskLocation || task.alertLocation || getDeviceLocationById(task.sourceDeviceId))" title="在 3D Cesium 地图定位">
                      地图定位
                    </button>
                  </div>
                </div>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <span style="color: var(--text-muted); font-weight: 600;">计算置信度 / 设定阈值:</span>
                  <span style="font-family: monospace; font-weight: 700; color: var(--danger);">
                    {{ task.confidence ? (task.confidence > 1 ? task.confidence + '%' : (task.confidence * 100).toFixed(1) + '%') : '92.50%' }} / {{ getDeviceThresholdById(task.sourceDeviceId) }}
                  </span>
                </div>
              </div>

              <!-- Detection Bounding Box Snapshot Section -->
              <div style="display: flex; flex-direction: column; gap: 4px;">
                <span style="font-size: 11px; font-weight: 600; color: var(--text-muted);">AI算法高置信度切片带框截图:</span>
                <div class="task-snapshot" style="position: relative; width: 100%; height: 165px; background: #0f172a; border-radius: 6px; overflow: hidden; display: flex; align-items: center; justify-content: center; border: 1px solid var(--border-color);">

                  <!-- Snapshot Image -->
                  <img :src="getTaskSnapshotUrl(task)" @error="handleImageError" style="width: 100%; height: 100%; object-fit: cover;" alt="溺水切片带框截图" />

                  <!-- Bounding Box Visual Overlay -->
                  <div style="position: absolute; border: 2.5px solid var(--danger); background: rgba(239, 68, 68, 0.18); top: 22%; left: 28%; width: 44%; height: 55%; border-radius: 3px; pointer-events: none; box-shadow: 0 0 10px rgba(239, 68, 68, 0.7);">
                    <span style="position: absolute; top: -20px; left: 0; background: var(--danger); color: white; font-size: 9px; font-weight: bold; padding: 2px 6px; border-radius: 2px; font-family: monospace; letter-spacing: 0.5px;">
                      Drowning: {{ task.confidence ? (task.confidence > 1 ? (task.confidence / 100).toFixed(2) : Number(task.confidence).toFixed(2)) : '0.93' }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- Footer Actions (人工标注为已处理或识别错误) -->
              <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px; padding-top: 8px; border-top: 1px solid var(--border-color);">
                <span style="font-size: 10px; color: var(--text-muted);">产生时间: {{ task.createdAt || task.dispatchAt }}</span>

                <div style="display: flex; gap: 8px;">
                  <button class="btn-action small" style="padding: 4px 10px; font-size: 11px; background: rgba(16, 185, 129, 0.1); color: var(--success); border: 1px solid rgba(16, 185, 129, 0.3); font-weight: 600;" :disabled="task.status === 'PROCESSED' || task.status === 'COMPLETED' || task.status === 'RESOLVED'" @click="changeTaskStatus(task.id, 'PROCESSED')">
                    <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg> 标注为已处理
                  </button>
                  <button class="btn-action small" style="padding: 4px 10px; font-size: 11px; background: rgba(239, 68, 68, 0.1); color: var(--danger); border: 1px solid rgba(239, 68, 68, 0.3); font-weight: 600;" :disabled="task.status === 'RECOGNITION_ERROR' || task.status === 'FALSE_ALARM'" @click="changeTaskStatus(task.id, 'RECOGNITION_ERROR')">
                    <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18"/></svg> 标注为识别错误
                  </button>
                </div>
              </div>

            </div>

            <!-- Empty State -->
            <div v-if="filteredTasksList.length === 0" style="grid-column: 1 / -1; text-align: center; color: var(--text-muted); padding: 40px; background: var(--bg-card); border-radius: 8px; border: 1px dashed var(--border-color);">
              <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true" style="font-size: 32px; color: var(--text-muted); margin-bottom: 8px; display: block;"><path d="m3 6 2 2 3-4m3 2h10M3 13h4m4 0h10M3 20h4m4 0h10"/></svg>
              <p style="font-size: 13px;">暂无该筛选条件下的风险检测任务记录</p>
            </div>

          </div>
        </div>
      </div>

      <!-- Right Sidebar (Monitor panel details) -->
      <aside v-show="currentTab === 'monitor'" class="right-sidebar monitor-rail" aria-label="视频与联动监测">
        <section class="monitor-card monitor-video-card">
          <div class="section-title"><span class="panel-index">01</span>监控视频画面 <small>VIDEO</small></div>
          <div class="video-container">
            <canvas ref="liveCanvas" style="width: 100%; height: 100%; display: block;"></canvas>
            <div class="video-status">{{ activeCameraName }}</div>
          </div>

          <!-- Model Selection for Active Camera (Read-only) -->
          <div class="model-selection-bar" style="background: rgba(30, 41, 59, 0.2); padding: 8px 12px; border-radius: 8px; margin-bottom: 12px; display: flex; align-items: center; justify-content: space-between; border: 1px solid var(--border-color);">
            <span style="font-size: 11px; font-weight: 600; color: var(--text-white);">AI 算法模型:</span>
            <span style="font-size: 11px; color: var(--text-muted); font-family: monospace;">{{ activeCameraModel }}</span>
          </div>
        </section>

        <!-- <div class="video-actions">
          <button class="btn-action primary" @click="simulateBroadcast">语音广播</button>
          <button class="btn-action" @click="simulateManualTrigger">模拟上报告警</button>
          <button class="btn-action" @click="simulateDroneTakeoff">指令无人机</button>
        </div> -->

        <section class="monitor-card monitor-detection-card">
          <div class="section-title"><span class="panel-index">02</span>安全动态监测 <small>DETECTION</small></div>
          <div class="detection-list monitor-detection-feed">
            <div v-if="detectionHistory.length === 0" class="monitor-empty-state">
              <span class="empty-signal" aria-hidden="true"><i></i><i></i><i></i></span>
              <span>等待监测事件接入</span>
              <small>水域安全动态将在此呈现</small>
            </div>
            <div v-else class="timeline-wrapper">
              <div v-for="(log, idx) in [...detectionHistory].reverse()" :key="idx" class="timeline-item" style="display: flex; gap: 8px; margin-bottom: 10px; border-left: 2px solid rgba(0,0,0,0.08); padding-left: 10px; position: relative;">
                <!-- Timeline bullet: Red glowing warning bullet -->
                <span :style="{
                  position: 'absolute',
                  left: '-6px',
                  top: '5px',
                  width: '10px',
                  height: '10px',
                  borderRadius: '50%',
                  background: log.isAudioAlert ? 'var(--violet)' : log.isAlert ? 'var(--danger)' : 'var(--success)',
                  boxShadow: log.isAudioAlert ? '0 0 10px var(--violet)' : log.isAlert ? '0 0 10px var(--danger)' : 'none'
                }"></span>

                <div style="flex: 1; font-size: 11px;">
                  <div style="display: flex; justify-content: space-between; align-items: center; color: var(--text-muted); margin-bottom: 2px;">
                    <span>{{ log.time }} - {{ log.deviceName }}</span>
                    <span v-if="log.isAudioAlert" style="background: rgba(124, 58, 237, 0.15); color: var(--violet); border: 1px solid rgba(124, 58, 237, 0.3); padding: 1px 6px; border-radius: 4px; font-weight: 700; font-size: 10px;">
                      触发音频告警
                    </span>
                    <span v-else-if="log.isAlert" style="background: rgba(239, 68, 68, 0.15); color: var(--danger); border: 1px solid rgba(239, 68, 68, 0.3); padding: 1px 6px; border-radius: 4px; font-weight: 700; font-size: 10px;">
                      触发红标告警
                    </span>
                    <span v-else style="color: var(--success); font-weight: 600; font-size: 10px;">
                      <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg> 安全监测
                    </span>
                  </div>
                  <div :style="{ color: log.isAudioAlert ? 'var(--violet)' : log.isAlert ? 'var(--danger)' : 'var(--text-white)', fontWeight: log.isAlert || log.isAudioAlert ? '700' : '500' }">
                    {{ log.displayText || log.details || '涉水人员' }}
                    <span v-if="log.confidence !== undefined" style="font-size: 10px; font-family: monospace; opacity: 0.9;">
                      (YOLO置信度: {{ log.confidenceFormatted || (log.confidence <= 1 ? log.confidence : log.confidence / 100).toFixed(2) }})
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section class="monitor-card monitor-linkage-card">
          <div class="section-title"><span class="panel-index">03</span>决策联动日志 <small>LINKAGE</small></div>
          <div class="detection-list monitor-linkage-feed">
            <div v-for="(log, idx) in linkageLogs" :key="idx">{{ log.replace(/[\p{Extended_Pictographic}\uFE0F]/gu, '') }}</div>
          </div>
        </section>
      </aside>

    </div>

    <!-- ADD/EDIT DEVICE MODAL OVERLAY -->
    <div class="modal-overlay" :class="{ show: showAddModal }">
      <div class="modal-card" style="max-width: 920px; width: 92%;">
        <div class="modal-header">
          <h3 class="modal-title">{{ deviceForm.id ? '编辑设备打点与绑定监控巡逻区域' : '新增设备打点与 1对1 监控巡逻区域' }}</h3>
          <button class="btn-close-modal" @click="closeAddModal">&times;</button>
        </div>
        <form @submit.prevent="submitDevice">
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; align-items: start;">

            <!-- LEFT COLUMN: Basic Device & Patrol Area Settings -->
            <div style="display: flex; flex-direction: column; gap: 12px;">
              <div class="form-group">
                <label>三维地图打点坐标 (经度 / 纬度)</label>
                <div style="display:flex; gap:8px;">
                  <input type="text" v-model="deviceForm.lng" class="form-control" readonly style="background: var(--bg-dark); font-size:12px;">
                  <input type="text" v-model="deviceForm.lat" class="form-control" readonly style="background: var(--bg-dark); font-size:12px;">
                </div>
              </div>

              <div class="form-group">
                <label>选择关联监控设备</label>
                <select v-model="selectedDeviceId" class="form-control" style="width: 100%; font-size:12px;">
                  <option :value="null">请选择摄像头设备...</option>
                  <option v-for="cam in cameraDevices" :key="cam.id" :value="cam.id">
                    [{{ cam.deviceCode }}] {{ cam.deviceName }}
                  </option>
                </select>
              </div>

              <div class="form-group">
                <label>监控点及巡逻区域自定义名称 (自动1对1绑定)</label>
                <input type="text" v-model="deviceForm.deviceName" class="form-control" required placeholder="例如: 西湖1号监控巡逻点" style="font-size:12px;">
              </div>

              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                  <label>监控点编号</label>
                  <input type="text" v-model="deviceForm.deviceCode" class="form-control" required placeholder="如 CAM_1001" style="font-size:12px;">
                </div>

                <div class="form-group">
                  <label>所属防护水域</label>
                  <select v-model="deviceForm.waterAreaId" class="form-control" required style="width: 100%; font-size:12px;">
                    <option v-for="area in waterAreas" :key="area.id" :value="area.id">
                      {{ area.name }}
                    </option>
                  </select>
                </div>
              </div>



              <!-- 1-to-1 Bound Patrol Zone Polygon Section -->
              <div class="form-group" style="background: var(--bg-card); padding: 12px; border-radius: 8px; border: 1px solid var(--border-color); box-shadow: 0 1px 3px rgba(0,0,0,0.05);">
                <label style="font-weight: 600; display:flex; justify-content:space-between; align-items:center; margin-bottom: 6px;">
                  <span style="color: var(--primary); font-weight: 700; font-size: 13px;">绑定 1 对 1 监控巡逻区域</span>
                  <span style="font-size:11px; color: var(--text-muted);">
                    区域名称: <strong style="color: var(--text-white);">[{{ deviceForm.deviceName || '与点位同名' }}]</strong>
                  </span>
                </label>
                <div style="display:flex; justify-content:space-between; align-items:center; margin-top:4px;">
                  <span style="font-size:11px;">
                    <span v-if="deviceForm.roiPolygon && deviceForm.roiPolygon.length >= 3" style="color: var(--success); font-weight:600;">
                      <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12 4 4L19 6"/></svg> 已成功绑定防区 ({{ deviceForm.roiPolygon.length }} 个坐标拐点)
                    </span>
                    <span v-else style="color: var(--warning); font-weight:600;">
                      <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m12 3 10 18H2L12 3Zm0 6v5m0 3v.1"/></svg> 尚未绘制巡逻多边形防区
                    </span>
                  </span>
                  <button type="button" class="btn-action small primary" style="padding: 5px 10px; font-size:11px; margin:0; color: #ffffff !important; font-weight: 600;" @click="startDrawPatrolPolygonForDevice">
                    {{ (deviceForm.roiPolygon && deviceForm.roiPolygon.length >= 3) ? '重新绘制区域' : '绘制巡逻区域' }}
                  </button>
                </div>
              </div>
            </div>

            <!-- RIGHT COLUMN: Audio AI ONNX Analysis Section -->
            <div class="form-group" style="background: var(--bg-card); padding: 14px; border-radius: 8px; border: 1px solid var(--border-color); box-shadow: 0 1px 3px rgba(0,0,0,0.05); margin: 0; height: 100%; box-sizing: border-box;">
              <label style="font-weight: 600; display:flex; justify-content:space-between; align-items:center; margin-bottom: 10px;">
                <span style="color: var(--violet); font-weight: 700; font-size: 13px;">音频 AI 识别与双重预警配置</span>
                <label style="font-size:12px; cursor:pointer; display:flex; align-items:center; gap:6px; color: var(--text-muted); font-weight:600;">
                  <input type="checkbox" v-model="deviceForm.audioConfig.enabled" style="accent-color:var(--violet); width:16px; height:16px;"> 开启音频 AI 分析
                </label>
              </label>

              <div v-if="deviceForm.audioConfig.enabled" style="display: flex; flex-direction: column; gap: 10px;">
                <!-- Processing Platform Model Selector -->
                <div>
                  <label style="font-size: 11px; font-weight: 600; color: var(--text-muted); margin-bottom: 4px; display:block;">选择视频处理平台上的 ONNX 音频模型:</label>
                  <select v-model="deviceForm.audioConfig.model_name" class="form-control" style="width: 100%; font-size: 12px; padding: 6px;">
                    <option value="">-- 系统默认音频模型 (YAMNet_Audio.onnx) --</option>
                    <option v-for="m in audioModelList" :key="m.model_name" :value="m.model_name">
                      {{ m.model_name }} {{ m.has_csv ? `(包含 ${m.display_names.length} 项 CSV 标签)` : '' }}
                    </option>
                  </select>
                </div>

                <!-- Multi-select Checklist for display_name -->
                <div>
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                    <span style="font-size: 11px; font-weight: 600; color: var(--text-white);">勾选预警目标声音类别 (display_name):</span>
                    <div style="display:flex; gap:6px;">
                      <button type="button" class="btn-action small" style="padding:2px 6px; font-size:10px;" @click="selectAllAudioClasses">全选</button>
                      <button type="button" class="btn-action small" style="padding:2px 6px; font-size:10px;" @click="clearAudioClasses">清空</button>
                    </div>
                  </div>
                  <div style="max-height: 140px; overflow-y: auto; display: flex; flex-wrap: wrap; gap: 6px; background: var(--bg-input); padding: 8px; border-radius: 6px; border: 1px solid var(--border-color);">
                    <label v-for="clsName in availableAudioClasses" :key="clsName" style="font-size: 11px; background: var(--bg-card); padding: 3px 8px; border-radius: 4px; border: 1px solid var(--text-muted); cursor: pointer; display: flex; align-items: center; gap: 4px;">
                      <input type="checkbox" :value="clsName" v-model="deviceForm.audioConfig.selected_classes">
                      <span>{{ clsName }}</span>
                    </label>
                  </div>
                </div>

                <!-- Audio Threshold Setting -->
                <div style="display:flex; align-items:center; justify-content:space-between;">
                  <span style="font-size: 11px; color: var(--text-muted); font-weight: 600;">音频独立触发阈值 (0.10 ~ 0.99):</span>
                  <input type="number" step="0.01" min="0.10" max="0.99" v-model.number="deviceForm.audioConfig.audio_threshold" class="form-control" style="width: 110px; padding: 4px 8px; font-size: 12px;" placeholder="0.70">
                </div>

                <!-- Dual Condition Info Banner -->
                <div style="font-size: 11px; background: rgba(124, 58, 237, 0.08); color: var(--violet); padding: 8px 10px; border-radius: 6px; border: 1px solid rgba(124, 58, 237, 0.2); font-weight: 600; line-height: 1.4;">
                  备注：开启音频分析后，系统仅当【视频视觉置信度 ≥ {{deviceForm.alertThreshold || 0.70}}】且【音频分析置信度 ≥ {{deviceForm.audioConfig.audio_threshold || 0.70}}】<b>同步满足</b>时才会上报红标告警！
                </div>
              </div>
              <div v-else style="font-size:12px; color:#94a3b8; padding:30px 0; text-align:center;">
                <svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true" style="font-size:24px; margin-bottom:8px; display:block;"><path d="m11 4-6 5H2v6h3l6 5V4Zm5 5 6 6m0-6-6 6"/></svg>
                音频 AI 分析未开启（勾选上方“开启音频 AI 分析”启用）
              </div>
            </div>

          </div>

          <div class="modal-footer" style="margin-top: 20px;">
            <button type="button" class="btn-action" @click="closeAddModal">取消</button>
            <button type="submit" class="btn-action primary" style="color: #ffffff !important; font-weight: 600;">保存打点与巡逻区域</button>
          </div>
        </form>
      </div>
    </div>

    <!-- USER ADD/EDIT MODAL OVERLAY -->
    <div class="modal-overlay" :class="{ show: showUserModal }">
      <div class="modal-card" style="max-width: 500px;">
        <div class="modal-header">
          <h3 class="modal-title">{{ isEditingUser ? '修改系统人物信息' : '创建新系统人物' }}</h3>
          <button class="btn-close-modal" @click="closeUserModal">&times;</button>
        </div>
        <form @submit.prevent="submitUser">
          <div class="form-grid" style="grid-template-columns: 1fr; gap: 16px;">
            <div class="form-group">
              <label>登录用户名</label>
              <input type="text" v-model="userForm.username" class="form-control" required :readonly="isEditingUser" placeholder="请输入唯一登录账号">
            </div>
            <div class="form-group" v-if="!isEditingUser">
              <label>登录密码</label>
              <input type="password" v-model="userForm.password" class="form-control" required placeholder="请输入登录密码">
            </div>
            <div class="form-group" v-else>
              <label>修改密码 (留空表示不修改)</label>
              <input type="password" v-model="userForm.password" class="form-control" placeholder="输入新密码">
            </div>
            <div class="form-group">
              <label>真实姓名</label>
              <input type="text" v-model="userForm.realName" class="form-control" required placeholder="请输入真实姓名">
            </div>
            <div class="form-group">
              <label>联系电话</label>
              <input type="text" v-model="userForm.phone" class="form-control" placeholder="请输入手机号">
            </div>
            <div class="form-group">
              <label>人物类型 / 对应角色</label>
              <select v-model="userForm.roleCode" class="form-control" required style="width:100%;">
                <option value="ROLE_RESCUER">现场救生员 (可指派任务与位置打点)</option>
                <option value="ROLE_OPERATOR">监控值守员 (处置大屏告警事件)</option>
                <option value="ROLE_LEADER">乡镇级领导 (查看系统数据报表)</option>
                <option value="ROLE_ADMIN">系统管理员 (管理设备与所有用户)</option>
              </select>
            </div>
            <div class="form-group">
              <label>绑定的安全责任监控点 (一对多 / 多对多关联)</label>
              <div class="device-checkbox-group" style="max-height: 120px; overflow-y: auto; padding: 12px; background: var(--bg-dark); border-radius: 4px; border: 1px solid var(--border-color); box-sizing: border-box;">
                <div v-for="cam in cameraDevices" :key="cam.id" style="display:flex; align-items:center; gap:8px; margin-bottom:6px;">
                  <input type="checkbox" :id="'user-cam-' + cam.id" :value="cam.id" v-model="userForm.deviceIds" style="cursor: pointer; width: 14px; height: 14px; margin: 0;">
                  <label :for="'user-cam-' + cam.id" style="margin:0; font-size:12px; color: var(--text-color); cursor:pointer;">
                    [{{ cam.deviceCode }}] {{ cam.deviceName }}
                  </label>
                </div>
                <div v-if="cameraDevices.length === 0" style="color:var(--text-muted); font-size:12px; text-align: center; padding: 12px 0;">系统暂无摄像头设备</div>
              </div>
            </div>
          </div>
          <div class="modal-footer" style="margin-top: 24px;">
            <button type="button" class="btn-action" @click="closeUserModal">取消</button>
            <button type="submit" class="btn-action primary">确认保存</button>
          </div>
        </form>
      </div>
    </div>

    <!-- VIDEO PLAYER MODAL OVERLAY -->
    <div class="modal-overlay" :class="{ show: showVideoModal }" @click.self="showVideoModal = false">
      <div class="modal-card" style="max-width: 720px; width: 90%;">
        <div class="modal-header">
          <h3 class="modal-title"><svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="5" width="13" height="14" rx="2"/><path d="m16 10 5-3v10l-5-3"/></svg> 抓拍录像在线回放 - {{ activeVideo?.file_name }}</h3>
          <button class="btn-close-modal" @click="showVideoModal = false">&times;</button>
        </div>
        <div style="background: #000; padding: 12px; border-radius: 8px; text-align: center; margin-top: 12px;">
          <video v-if="showVideoModal" :src="activeVideoUrl" controls autoplay style="width: 100%; max-height: 420px; border-radius: 4px;"></video>
        </div>
        <div class="modal-footer" style="margin-top: 16px; display: flex; justify-content: space-between; align-items: center;">
          <span style="font-size: 12px; color: var(--text-muted);">最高置信度: {{ activeVideo ? (activeVideo.max_confidence * 100).toFixed(1) : 0 }}% | 设定阈值: {{ activeVideo ? (activeVideo.threshold * 100).toFixed(0) : 70 }}%</span>
          <button type="button" class="btn-action" @click="showVideoModal = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- Toast Notification -->
    <div class="toast" :class="{ show: toast.visible, error: toast.type === 'error', success: toast.type === 'success' }">
      {{ toast.message.replace(/[\p{Extended_Pictographic}\uFE0F]/gu, '') }}
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, onMounted, onUnmounted, reactive, ref, nextTick, watch, computed } from 'vue';
import { useRouter } from 'vue-router';
import * as echarts from 'echarts';
import request from '../utils/request';
import CesiumMap, { DeviceData } from '../components/CesiumMap.vue';
import DistributionLink from '../components/DistributionLink.vue';

export default defineComponent({
  name: 'Dashboard',
  components: {
    CesiumMap,
    DistributionLink
  },
  setup() {
    const router = useRouter();
    const currentTab = ref('monitor');
    const deviceList = ref<DeviceData[]>([]);
    const alertList = ref<any[]>([]);
    const waterAreas = ref<any[]>([]);
    const activeAlert = ref<any>(null);
    const showAddModal = ref(false);
    const linkageLogs = ref<string[]>([]);
    const detectionHistory = ref<any[]>([]);

    // 任务中心相关变量与计算属性
    const taskStatusFilter = ref('ALL');
    const tasksList = ref<any[]>([]);

    const loadTasks = async () => {
      try {
        const res: any = await request.get('/api/v1/tasks?pageSize=50');
        if (res && res.code === 200 && res.data && res.data.records) {
          tasksList.value = res.data.records;
        }
      } catch (e) {
        console.error("加载救援任务列表失败:", e);
      }
    };

    const filteredTasksList = computed(() => {
      const list = (tasksList.value && tasksList.value.length > 0) ? tasksList.value : (alertList.value || []);
      if (taskStatusFilter.value === 'ALL') {
        return list;
      } else if (taskStatusFilter.value === 'PENDING') {
        return list.filter(t => t.status === 'PENDING' || t.status === 'DISPATCHED');
      } else if (taskStatusFilter.value === 'PROCESSED') {
        return list.filter(t => t.status === 'PROCESSED' || t.status === 'COMPLETED' || t.status === 'CONFIRMED' || t.status === 'RESOLVED');
      } else if (taskStatusFilter.value === 'RECOGNITION_ERROR') {
        return list.filter(t => t.status === 'RECOGNITION_ERROR' || t.status === 'FALSE_ALARM');
      }
      return list;
    });

    const taskCounts = computed(() => {
      const list = (tasksList.value && tasksList.value.length > 0) ? tasksList.value : (alertList.value || []);
      const total = list.length;
      const pending = list.filter(t => t.status === 'PENDING' || t.status === 'DISPATCHED').length;
      const processed = list.filter(t => t.status === 'PROCESSED' || t.status === 'COMPLETED' || t.status === 'CONFIRMED' || t.status === 'RESOLVED').length;
      const error = list.filter(t => t.status === 'RECOGNITION_ERROR' || t.status === 'FALSE_ALARM').length;
      return { total, pending, processed, error };
    });

    const getTaskStatusClass = (status: string) => {
      if (status === 'PENDING' || status === 'DISPATCHED') return 'high';
      if (status === 'PROCESSED' || status === 'COMPLETED' || status === 'CONFIRMED' || status === 'RESOLVED') return 'low';
      if (status === 'RECOGNITION_ERROR' || status === 'FALSE_ALARM') return 'medium';
      return 'medium';
    };

    const getTaskStatusText = (status: string) => {
      if (status === 'PENDING' || status === 'DISPATCHED') return '未处理';
      if (status === 'PROCESSED' || status === 'COMPLETED' || status === 'CONFIRMED' || status === 'RESOLVED') return '已处理';
      if (status === 'RECOGNITION_ERROR' || status === 'FALSE_ALARM') return '识别错误';
      return status || '未处理';
    };

    const getDeviceLocationById = (id: number) => {
      if (!id) return '';
      const dev = deviceList.value.find(d => d.id === id);
      return dev ? dev.location : '';
    };

    const getDeviceThresholdById = (id: number) => {
      if (!id) return '70%';
      const dev = deviceList.value.find(d => d.id === id);
      if (dev && dev.extraConfig && dev.extraConfig.alert_threshold !== undefined) {
        const val = Number(dev.extraConfig.alert_threshold);
        return `${(val <= 1 ? val * 100 : val).toFixed(0)}%`;
      }
      return '70%';
    };

    const formatLocationCoords = (locationWkt: string) => {
      if (!locationWkt) return 'POINT(120.0318156 28.5093682)';
      try {
        if (locationWkt.includes('(') && locationWkt.includes(')')) {
          const content = locationWkt.substring(locationWkt.indexOf('(') + 1, locationWkt.indexOf(')'));
          const parts = content.split(' ');
          const lng = parseFloat(parts[0]);
          const lat = parseFloat(parts[1]);
          return `E: ${lng.toFixed(6)}, N: ${lat.toFixed(6)}`;
        }
      } catch (e) {}
      return locationWkt;
    };

    const getTaskSnapshotUrl = (task: any) => {
      if (task.snapshotUrl) return task.snapshotUrl;
      const host = window.location.hostname || '127.0.0.1';
      return `http://${host}:8000/static/recordings/snapshot_${task.id || 'default'}.jpg`;
    };

    const handleImageError = (e: Event) => {
      const target = e.target as HTMLImageElement;
      if (target) {
        target.src = 'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="360" height="165" viewBox="0 0 360 165"><rect width="360" height="165" fill="%230f172a"/><text x="180" y="30" fill="%23ef4444" font-size="11" font-weight="bold" text-anchor="middle" font-family="sans-serif">【⚠️ 警告！AI算法高置信度切片识别到溺水姿态】</text><rect x="90" y="45" width="180" height="90" stroke="%23ef4444" stroke-width="2.5" fill="rgba(239,68,68,0.2)" rx="3"/><rect x="90" y="27" width="105" height="18" fill="%23ef4444" rx="2"/><text x="95" y="40" fill="%23ffffff" font-size="10" font-weight="bold" font-family="monospace">Drowning: 0.93</text><text x="180" y="150" fill="%2394a3b8" font-size="10" text-anchor="middle" font-family="sans-serif">监控切片快照预览</text></svg>';
      }
    };

    const changeTaskStatus = async (taskId: number, newStatus: string) => {
      try {
        let res: any;
        res = await request.post(`/api/v1/tasks/${taskId}/status?status=${newStatus}`);
        if (!res || res.code !== 200) {
          res = await request.post(`/api/v1/alerts/${taskId}/status?status=${newStatus}`);
        }
        if (res && res.code === 200) {
          showToast(`任务 #${taskId} 状态已变更为 [${getTaskStatusText(newStatus)}]`, 'success');
          addLog(`[任务中心] 人工标注任务 #${taskId} 状态为 ${getTaskStatusText(newStatus)}`);
          loadTasks();
          loadAlerts();
          loadKPIs();
        } else {
          showToast(res.message || '更新状态失败', 'error');
        }
      } catch (e) {
        showToast('更新任务状态网络请求异常', 'error');
      }
    };

    // 录像中心与阈值设定相关变量
    const recordingList = ref<any[]>([]);
    const showVideoModal = ref(false);
    const activeVideo = ref<any>(null);
    const activeVideoUrl = computed(() => {
      if (!activeVideo.value || !activeVideo.value.file_name) return '';
      const host = window.location.hostname || '127.0.0.1';
      return `http://${host}:8000/static/recordings/${activeVideo.value.file_name}`;
    });

    // 人物管理与救生员定位打点相关
    const userList = ref<any[]>([]);
    const lifeguardList = ref<any[]>([]);
    const showUserModal = ref(false);
    const isEditingUser = ref(false);
    const isDrawingLifeguard = ref(false);
    const mockLocationUser = ref<any>(null);
    const userForm = reactive({
      id: null as number | null,
      username: '',
      password: '',
      realName: '',
      phone: '',
      roleCode: 'ROLE_RESCUER',
      deviceIds: [] as number[]
    });

    // 监控分发系统 & AI热切换相关变量
    const wallGridSize = ref(4); // 默认 4分屏 (2x2)
    const activeCameraDevice = ref<DeviceData | null>(null);
    const activeCameraModel = ref('best.pt');
    const activeCameraThreshold = ref<number>(0.25);
    const activeCameraDetections = ref<any[]>([]);

    const getThresholdModeBadgeClass = (conf: number | undefined) => {
      const c = conf !== undefined ? Number(conf) : 0.25;
      if (c <= 0.20) return 'badge-fine';
      if (c <= 0.45) return 'badge-balanced';
      return 'badge-strict';
    };

    const getThresholdModeText = (conf: number | undefined) => {
      const c = conf !== undefined ? Number(conf) : 0.25;
      if (c <= 0.20) return '🎯 精细全面模式';
      if (c <= 0.45) return '⚖️ 标准平衡模式';
      return '🛡️ 严格防误报模式';
    };

    const getThresholdModeDesc = (conf: number | undefined) => {
      const c = conf !== undefined ? Number(conf) : 0.25;
      if (c <= 0.20) return '【小切片 320x320 + 低门槛 0.12】极高灵敏度，专用于捕捉水面远端微小目标与隐蔽落水姿态。';
      if (c <= 0.45) return '【标准切片 480x480 + 平衡门槛 0.25】兼顾近景与远景识别覆盖面，适用于大多数常规监控水域。';
      return '【大切片 640x640 + 高门槛 0.60 + 严格NMS】极高抗干扰能力，适用于强日光折射、水面波浪杂波场景，防误报。';
    };

    const changeActiveCameraThreshold = async () => {
      const conf = activeCameraThreshold.value;
      if (!activeCameraDevice.value) {
        showToast(`🎚️ AI识别精细度设为 ${Math.round(conf * 100)}% (${getThresholdModeText(conf)})`, 'success');
        return;
      }
      const camId = activeCameraDevice.value.id;
      try {
        const res: any = await request.post(`/api/v1/devices/${camId}/confidence`, { confidence: conf });
        if (res && res.code === 200) {
          showToast(`⚡ 监控点 [${activeCameraDevice.value.deviceName}] 识别粒度已热刷新为 ${Math.round(conf * 100)}% (${getThresholdModeText(conf)})!`, 'success');
          addLog(`[AI灵敏度控制] 监控点 [${activeCameraDevice.value.deviceName}] 热重载识别阈值为 ${conf} (${getThresholdModeText(conf)})`);
          loadDevices();
        } else {
          showToast(res.message || '热重载识别灵敏度失败', 'error');
        }
      } catch (e) {
        showToast('热重载识别灵敏度网络错误', 'error');
      }
    };

    const wallSlots = ref<Array<{ slotIndex: number, deviceId: number | null, player: any, detections: any[], statusInfo: any }>>([
      { slotIndex: 0, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 1, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 2, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 3, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 4, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 5, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 6, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 7, deviceId: null, player: null, detections: [], statusInfo: null },
      { slotIndex: 8, deviceId: null, player: null, detections: [], statusInfo: null }
    ]);

    const user = reactive({
      username: '',
      realName: '',
      role: ''
    });

    const theme = ref('light');

    const toggleTheme = () => {
      theme.value = 'light';
      document.documentElement.classList.remove('dark-theme');
      localStorage.setItem('theme', 'light');
      nextTick(() => {
        renderCharts();
      });
    };

    const kpis = reactive({
      onlineDeviceCount: 0,
      totalDeviceCount: 0,
      peopleCount: 0,
      pendingAlertCount: 0,
      completedRescueCount: 0,
      lifeguardCount: 0
    });

    const cesiumMapRef = ref<any>(null);

    const deviceForm = reactive({
      id: null as number | null,
      deviceCode: '',
      deviceName: '',
      deviceType: 'CAMERA',
      ipAddress: '127.0.0.1',
      port: 554,
      waterAreaId: 1,
      locationDesc: '',
      lng: '',
      lat: '',
      streamUrl: 'rtsp://127.0.0.1:554/live/c1_yolo',
      alertThreshold: 0.70,
      roiPolygon: [] as Array<{lng: number, lat: number}>,
      audioConfig: {
        enabled: true,
        model_name: '',
        selected_classes: ['Scream', 'Shout', 'Crying', 'Splash'] as string[],
        audio_threshold: 0.70
      }
    });

    const selectedDeviceId = ref<number | null>(null);

    const activeCameraName = ref('C02 · 中央南河段 (演示流)');
    let jsmpegPlayer: any = null;
    const useSimulatedFeed = ref(true);

    // Watcher to auto update port on device type change
    watch(() => deviceForm.deviceType, (newType) => {
      if (newType === 'CAMERA') {
        deviceForm.port = 554;
      } else {
        deviceForm.port = 80;
      }
    });

    // Watcher to auto fill device details when selectedDeviceId changes in mock association mode
    watch(selectedDeviceId, (newId) => {
      if (newId) {
        const dev = deviceList.value.find(d => d.id === newId);
        if (dev) {
          deviceForm.deviceName = dev.deviceName;
          deviceForm.deviceCode = dev.deviceCode;
          deviceForm.waterAreaId = dev.waterAreaId || 1;
        }
      } else {
        deviceForm.deviceName = '';
        deviceForm.deviceCode = '';
        deviceForm.waterAreaId = 1;
      }
    });

    const toast = reactive({
      visible: false,
      message: '',
      type: 'success'
    });

    // Refs for Charts and Canvas
    const trendChartDom = ref<HTMLDivElement | null>(null);
    const distChartDom = ref<HTMLDivElement | null>(null);
    const liveCanvas = ref<HTMLCanvasElement | null>(null);

    const showToast = (msg: string, type: 'success' | 'error' = 'success') => {
      toast.message = msg;
      toast.type = type;
      toast.visible = true;
      setTimeout(() => {
        toast.visible = false;
      }, 3000);
    };

    const addLog = (text: string) => {
      const time = new Date().toLocaleTimeString();
      linkageLogs.value.push(`[${time}] ${text}`);
    };

    const translateRole = (role: string) => {
      const map: any = {
        'ROLE_ADMIN': '系统管理员',
        'ROLE_OPERATOR': '监控值守员',
        'ROLE_LEADER': '乡镇领导',
        'ROLE_RESCUER': '现场救生员'
      };
      return map[role] || role;
    };

    const loadProfile = async () => {
      try {
        const res: any = await request.get('/api/v1/auth/info');
        if (res && res.code === 200) {
          user.username = res.data.username;
          user.realName = res.data.realName;
          user.role = res.data.roles[0];
        }
      } catch (e) {
        console.error("加载用户信息失败:", e);
      }
    };

    const loadKPIs = async () => {
      try {
        const res: any = await request.get('/api/v1/statistics/overview');
        if (res && res.code === 200) {
          kpis.onlineDeviceCount = res.data.onlineDeviceCount;
          kpis.totalDeviceCount = res.data.totalDeviceCount;
          kpis.pendingAlertCount = res.data.pendingAlertCount;
          kpis.completedRescueCount = res.data.todayCompletedRescueCount;
          kpis.peopleCount = res.data.peopleCount || 0;
          kpis.lifeguardCount = res.data.lifeguardCount || 0;
        }
      } catch (e) {
        console.error("加载KPI指标失败:", e);
      }
    };

    const loadWaterAreas = async () => {
      try {
        const res: any = await request.get('/api/v1/water-areas');
        if (res && res.code === 200) {
          waterAreas.value = res.data.records;
        }
      } catch (e) {
        console.error("加载水域列表失败:", e);
      }
    };

    const updateWallSlotsData = () => {
      for (let slot of wallSlots.value) {
        if (slot.deviceId) {
          const dev = deviceList.value.find(d => d.id === slot.deviceId);
          if (dev) {
            slot.detections = dev.extraConfig?.detections || [];
            slot.statusInfo = {
              selectedModel: dev.extraConfig?.selectedModel || 'best.pt',
              inferenceFps: dev.extraConfig?.inferenceFps ?? 25,
              decodeFps: dev.extraConfig?.decodeFps ?? 25,
              device: dev.extraConfig?.device || 'cuda',
              status: dev.status
            };
          }
        }
      }
    };

    const autoAssignWallSlots = () => {
      const cameras = deviceList.value.filter(d => d.deviceType === 'CAMERA');
      for (let i = 0; i < Math.min(cameras.length, 9); i++) {
        if (wallSlots.value[i].deviceId === null) {
          wallSlots.value[i].deviceId = cameras[i].id;
        }
      }
      updateWallSlotsData();
    };

    const loadDevices = async () => {
      try {
        const res: any = await request.get('/api/v1/devices?pageSize=100');
        if (res && res.code === 200) {
          deviceList.value = res.data.records;

          // Sync active camera details
          if (activeCameraDevice.value) {
            const updated = deviceList.value.find(d => d.id === activeCameraDevice.value?.id);
            if (updated) {
              activeCameraDevice.value = updated;
              activeCameraDetections.value = updated.extraConfig?.detections || [];
              activeCameraModel.value = updated.extraConfig?.selectedModel || 'best.pt';
              activeCameraThreshold.value = updated.extraConfig?.alert_threshold ?? updated.extraConfig?.confidence ?? 0.25;
            }
          }

          // Sync wall slots details
          updateWallSlotsData();
          autoAssignWallSlots();

          // Auto-play first camera if no stream active yet
          if (!jsmpegPlayer && currentTab.value === 'monitor') {
            const firstCam = deviceList.value.find(d => d.deviceType === 'CAMERA');
            if (firstCam) {
              nextTick(() => {
                playCameraStream(firstCam);
              });
            } else {
              runVideoSimulation();
            }
          }
        }
      } catch (e) {
        console.error("加载设备列表失败:", e);
      }
    };

    let lastAlertedId: any = null;

    const loadAlerts = async () => {
      try {
        const res: any = await request.get('/api/v1/alerts?pageSize=50');
        if (res && res.code === 200) {
          alertList.value = res.data.records;
          // Check for pending alert to highlight
          const pending = alertList.value.find(a => a.status === 'PENDING');
          if (pending) {
            activeAlert.value = pending;
            if (lastAlertedId !== pending.id) {
              lastAlertedId = pending.id;
              const locationName = pending.sourceDeviceName || '未知点位';
              const alarmContent = pending.alertType === 'DROWNING' ? '人员落水/疑似溺水' : '异常安全警告';
              // Location name + alarm type notification linkage
              showToast(`警告！检测到 [${locationName}] 发生 [${alarmContent}]，已联动触发机库无人机应急起飞！`, 'error');

              // Announcement via Web Speech API
              try {
                const utterance = new SpeechSynthesisUtterance(`警告，检测到 ${locationName} 发生 ${alarmContent}`);
                utterance.lang = 'zh-CN';
                window.speechSynthesis.speak(utterance);
              } catch (e) {}

              addLog(`[防区联动警告] 点位: ${locationName} 触发 [${alarmContent}]，已指派附近救援音柱进行警告广播！`);
            }
          } else {
            activeAlert.value = null;
          }
        }
      } catch (e) {
        console.error("加载报警记录失败:", e);
      }
    };

    const historicalDetections = ref<any[]>([]);

    const loadHistoricalDetections = async () => {
      try {
        const res: any = await request.get('/api/v1/alerts/detection-history?limit=100');
        if (res && res.code === 200 && res.data) {
          historicalDetections.value = res.data;

          // Map to sidebar rolling detection history
          const topEvents = res.data.slice(0, 15);
          detectionHistory.value = topEvents.map((item: any) => {
            const cam = deviceList.value.find(d => d.deviceCode?.toLowerCase() === item.stream_id?.toLowerCase());
            const deviceName = cam ? cam.deviceName : item.stream_id;

            let timeStr = item.timestamp;
            if (timeStr && timeStr.includes(" ")) {
              timeStr = timeStr.split(" ")[1];
            }

            const confDec = item.confidence > 1 ? Number((item.confidence / 100).toFixed(2)) : Number(item.confidence.toFixed(2));
            const threshDec = cam?.extraConfig?.alert_threshold !== undefined ? Number(cam.extraConfig.alert_threshold) : 0.70;

            const evtCombined = (item.event_name || '') + ' ' + (item.details || '') + ' ' + (item.stream_id || '');
            const isDrowning = /drown|drowing|溺水|落水/i.test(evtCombined) || (item.details && (item.details.includes('溺水姿态') || item.details.includes('警告')));

            // 只要显示“警告！AI算法识别到溺水姿态”的话才会显示安全动态监测日志中的红标警告，不然不会发生红标警告
            const displayText = isDrowning ? "警告！AI算法识别到溺水姿态" : (item.details || (item.event_name === 'Drowning' ? '警告！AI算法识别到溺水姿态' : '涉水人员'));
            const isAlert = displayText === "警告！AI算法识别到溺水姿态";
            const isAudioAlert = (item.rule_type === 'audio_alert' || (item.event_name && item.event_name.startsWith('Audio'))) && !isAlert;

            return {
              id: item.id,
              time: timeStr || "未知",
              deviceName: deviceName,
              eventName: item.event_name,
              details: item.details,
              displayText: displayText,
              confidence: confDec,
              confidenceFormatted: confDec.toFixed(2),
              threshold: threshDec,
              thresholdFormatted: threshDec.toFixed(2),
              isAlert: isAlert,
              isAudioAlert: isAudioAlert
            };
          });
        }
      } catch (e) {
        console.error("加载监测历史事件失败:", e);
      }
    };

    const loadRecordings = async () => {
      try {
        const res: any = await request.get('/api/v1/alerts/recordings?limit=100');
        if (res && res.code === 200) {
          recordingList.value = res.data || [];
        }
      } catch (e) {
        console.error("加载录像记录失败:", e);
      }
    };

    const playVideoModal = (rec: any) => {
      activeVideo.value = rec;
      showVideoModal.value = true;
    };

    const downloadVideo = (rec: any) => {
      const host = window.location.hostname || '127.0.0.1';
      const url = `http://${host}:8000/static/recordings/${rec.file_name}`;
      const link = document.createElement("a");
      link.href = url;
      link.download = rec.file_name;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    };

    const deleteRecordingItem = async (id: number) => {
      if (!confirm("确定要删除该条抓拍录像及物理文件吗？")) return;
      try {
        const res: any = await request.delete(`/api/v1/alerts/recordings/${id}`);
        if (res && res.code === 200) {
          showToast("录像文件已被彻底删除");
          loadRecordings();
        } else {
          showToast("删除失败", "error");
        }
      } catch (e) {
        showToast("删除失败", "error");
      }
    };

    const clearAllRecordings = async () => {
      if (!confirm("确定要清空全部触发阈值抓拍录像记录及物理视频文件吗？操作不可撤销。")) return;
      try {
        const res: any = await request.delete('/api/v1/alerts/recordings');
        if (res && res.code === 200) {
          showToast("全量抓拍录像文件及记录已清空", "success");
          loadRecordings();
        } else {
          showToast(res.message || "清空录像失败", "error");
        }
      } catch (e) {
        showToast("清空录像请求网络错误", "error");
      }
    };

    const deleteAlertEvent = async (id: number) => {
      if (!confirm("确定要删除该条告警记录吗？")) return;
      try {
        const res: any = await request.delete(`/api/v1/alerts/${id}`);
        if (res && res.code === 200) {
          showToast("告警记录已删除");
          loadAlerts();
        } else {
          showToast("删除失败", "error");
        }
      } catch (e) {
        showToast("删除失败", "error");
      }
    };

    const deleteDetectionItem = async (id: number) => {
      try {
        const res: any = await request.delete(`/api/v1/alerts/detection-history/${id}`);
        if (res && res.code === 200) {
          showToast("单条监测历史已删除");
          loadHistoricalDetections();
        }
      } catch (e) {
        showToast("删除失败", "error");
      }
    };

    const clearAllDetections = async () => {
      if (!confirm("确定要清空全部智能分析与目标监测历史记录吗？")) return;
      try {
        const res: any = await request.delete('/api/v1/alerts/detection-history');
        if (res && res.code === 200) {
          showToast("历史监测记录已全部清空");
          loadHistoricalDetections();
        }
      } catch (e) {
        showToast("清空失败", "error");
      }
    };

    const exportToCSV = (filename: string, headers: string[], rows: (string | number)[][]) => {
      let csvContent = "\uFEFF";
      csvContent += headers.join(",") + "\n";
      rows.forEach(row => {
        const rowContent = row.map(val => {
          let str = val === null || val === undefined ? '' : String(val);
          str = str.replace(/"/g, '""');
          if (str.includes(',') || str.includes('\n') || str.includes('"')) {
            str = `"${str}"`;
          }
          return str;
        }).join(",");
        csvContent += rowContent + "\n";
      });

      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement("a");
      const url = URL.createObjectURL(blob);
      link.setAttribute("href", url);
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      showToast(`已成功导出 ${filename}`);
    };

    const exportAlertsCSV = () => {
      const headers = ["告警编号", "告警类型", "置信度(%)", "所属水域", "状态", "时间"];
      const rows = alertList.value.map(a => [
        a.eventNo,
        a.alertType === 'DROWNING' ? '溺水求救行为' : a.alertType,
        `${a.confidence}%`,
        a.waterAreaName || '未知防区',
        getAlertStatusText(a.status),
        a.createdAt
      ]);
      exportToCSV(`系统告警记录导出_${new Date().toISOString().slice(0,10)}.csv`, headers, rows);
    };

    const exportDetectionsCSV = () => {
      const headers = ["通道编号", "监测设备名称", "载入模型", "智能检测分析内容", "计算置信度(%)", "更新时间"];
      const rows = historicalDetections.value.map(d => [
        d.stream_id,
        getDeviceNameByCode(d.stream_id),
        getDeviceModelByCode(d.stream_id),
        d.details || d.event_name,
        `${(d.confidence <= 1 ? d.confidence * 100 : d.confidence).toFixed(1)}%`,
        d.timestamp
      ]);
      exportToCSV(`目标监测历史导出_${new Date().toISOString().slice(0,10)}.csv`, headers, rows);
    };

    const exportRecordingsCSV = () => {
      const headers = ["ID", "通道/设备编号", "设备名称", "设定阈值(%)", "最高置信度(%)", "录像时长(秒)", "文件大小(MB)", "触发与归档原因", "生成时间"];
      const rows = recordingList.value.map(r => [
        r.id,
        r.stream_id,
        getDeviceNameByCode(r.stream_id),
        `${(r.threshold * 100).toFixed(0)}%`,
        `${(r.max_confidence * 100).toFixed(1)}%`,
        r.duration,
        (r.file_size / (1024 * 1024)).toFixed(2),
        r.trigger_reason,
        r.created_at
      ]);
      exportToCSV(`触发阈值录像清单导出_${new Date().toISOString().slice(0,10)}.csv`, headers, rows);
    };

    const getDeviceNameByCode = (code: string) => {
      const cam = deviceList.value.find(d => d.deviceCode?.toLowerCase() === code?.toLowerCase());
      return cam ? cam.deviceName : code;
    };

    const getDeviceModelByCode = (code: string) => {
      const cam = deviceList.value.find(d => d.deviceCode?.toLowerCase() === code?.toLowerCase());
      return cam ? (cam.extraConfig?.selectedModel || 'best.pt') : 'yolov8n.pt';
    };

    const audioModelList = ref<any[]>([]);
    const audioOnnxFileInput = ref<any>(null);
    const audioCsvFileInput = ref<any>(null);

    const defaultPresetAudioClasses = [
      "Speech", "Scream", "Shout", "Yell", "Crying", "Groan",
      "Splash", "Water", "Swimming", "Whistle", "Alarm"
    ];

    const loadAudioModels = async () => {
      try {
        const res: any = await request.get('/api/audio/models');
        if (res && Array.isArray(res)) {
          audioModelList.value = res;
        }
      } catch (e) {
        console.error("加载音频 ONNX 模型列表失败:", e);
      }
    };

    const availableAudioClasses = computed(() => {
      const current = audioModelList.value.find(m => m.model_name === deviceForm.audioConfig.model_name);
      if (current && current.display_names && current.display_names.length > 0) {
        return current.display_names;
      }
      return defaultPresetAudioClasses;
    });

    const selectAllAudioClasses = () => {
      deviceForm.audioConfig.selected_classes = [...availableAudioClasses.value];
    };

    const clearAudioClasses = () => {
      deviceForm.audioConfig.selected_classes = [];
    };

    const handleUploadAudioModelFiles = async () => {
      const onnxEl = audioOnnxFileInput.value;
      const csvEl = audioCsvFileInput.value;
      if (!onnxEl || !onnxEl.files[0]) {
        showToast("请先选择 .onnx 格式的音频模型文件", "error");
        return;
      }
      if (!csvEl || !csvEl.files[0]) {
        showToast("请先选择包含 (index,mid,display_name) 列的 .csv 标签文件", "error");
        return;
      }

      const formData = new FormData();
      formData.append("onnx_file", onnxEl.files[0]);
      formData.append("csv_file", csvEl.files[0]);

      try {
        const res: any = await request.post('/api/audio/upload-model', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        });
        if (res && res.success) {
          showToast(`音频模型 [${res.model_name}] 与 CSV 标签 (${res.total_classes} 项) 上传成功！`, "success");
          await loadAudioModels();
          deviceForm.audioConfig.model_name = res.model_name;
          deviceForm.audioConfig.selected_classes = res.display_names || defaultPresetAudioClasses;
        }
      } catch (e: any) {
        showToast(`上传音频模型与 CSV 标签失败: ${e.message || e}`, "error");
      }
    };

    const openEditDeviceModal = (dev: any) => {
      deviceForm.id = dev.id;
      deviceForm.deviceCode = dev.deviceCode;
      deviceForm.deviceName = dev.deviceName;
      deviceForm.deviceType = dev.deviceType || 'CAMERA';
      deviceForm.waterAreaId = dev.waterAreaId || 1;
      const rawThresh = dev.extraConfig?.alert_threshold !== undefined ? Number(dev.extraConfig.alert_threshold) : 0.70;
      deviceForm.alertThreshold = Number(Number(rawThresh <= 1 ? rawThresh : rawThresh / 100).toFixed(2));
      deviceForm.roiPolygon = dev.extraConfig?.roi_polygon || [];

      // Audio Config parse
      const audioConf = dev.extraConfig?.audio_config || {};
      deviceForm.audioConfig.enabled = audioConf.enabled !== false;
      deviceForm.audioConfig.model_name = audioConf.model_name || '';
      deviceForm.audioConfig.selected_classes = audioConf.selected_classes || ['Scream', 'Shout', 'Crying', 'Splash'];
      deviceForm.audioConfig.audio_threshold = audioConf.audio_threshold || 0.70;

      selectedDeviceId.value = dev.id;

      if (dev.location && dev.location.includes("(")) {
        const content = dev.location.substring(dev.location.indexOf("(") + 1, dev.location.indexOf(")"));
        const parts = content.split(" ");
        deviceForm.lng = parseFloat(parts[0]).toFixed(7);
        deviceForm.lat = parseFloat(parts[1]).toFixed(7);
      } else {
        deviceForm.lng = '120.0304565';
        deviceForm.lat = '28.5064125';
      }

      showAddModal.value = true;
    };

    const handleMapClick = (coords: { lng: number, lat: number }) => {
      deviceForm.id = null;
      deviceForm.lng = coords.lng.toFixed(7);
      deviceForm.lat = coords.lat.toFixed(7);
      deviceForm.deviceName = `监控点_${Math.floor(100 + Math.random() * 900)}`;
      deviceForm.deviceCode = `CAM_${Math.floor(1000 + Math.random() * 9000)}`;
      deviceForm.waterAreaId = 1;
      deviceForm.alertThreshold = 0.70;
      deviceForm.roiPolygon = [];
      deviceForm.audioConfig.enabled = true;
      deviceForm.audioConfig.selected_classes = ['Scream', 'Shout', 'Crying', 'Splash'];
      deviceForm.audioConfig.audio_threshold = 0.70;
      selectedDeviceId.value = null;
      showAddModal.value = true;
    };

    const startDrawPatrolPolygonForDevice = () => {
      showAddModal.value = false;
      currentTab.value = 'monitor';
      if (cesiumMapRef.value) {
        cesiumMapRef.value.toggleDrawingPolygon();
        showToast(`请在三维地图上使用【左键】连续打点标记 [${deviceForm.deviceName || '该点位'}] 的巡逻区域，【右键】结束绘制`, 'success');
      }
    };

    const closeAddModal = () => {
      showAddModal.value = false;
      selectedDeviceId.value = null;
    };

    const submitDevice = async () => {
      let targetDevice = deviceList.value.find(d => d.id === selectedDeviceId.value || d.id === deviceForm.id);

      const location = (deviceForm.lng && deviceForm.lat) ? `POINT(${deviceForm.lng} ${deviceForm.lat})` : null;

      let threshVal = Number(deviceForm.alertThreshold || 0.70);
      if (threshVal > 1) {
        threshVal = threshVal / 100;
      }

      const extraConfig = {
        ...(targetDevice?.extraConfig || {}),
        alert_threshold: Number(threshVal.toFixed(2)),
        roi_polygon: deviceForm.roiPolygon || [],
        audio_config: {
          enabled: deviceForm.audioConfig.enabled,
          model_name: deviceForm.audioConfig.model_name,
          selected_classes: deviceForm.audioConfig.selected_classes,
          audio_threshold: deviceForm.audioConfig.audio_threshold
        }
      };

      const payload = {
        id: deviceForm.id || targetDevice?.id,
        deviceCode: deviceForm.deviceCode || targetDevice?.deviceCode || `CAM_${Date.now()}`,
        deviceName: deviceForm.deviceName || targetDevice?.deviceName || '新建监控点',
        deviceType: targetDevice?.deviceType || 'CAMERA',
        manufacturer: (targetDevice as any)?.manufacturer || 'HIKVISION',
        model: (targetDevice as any)?.model || 'DS-2CD2T87WD',
        ipAddress: targetDevice?.ipAddress || '10.150.63.103',
        port: targetDevice?.port || 554,
        waterAreaId: deviceForm.waterAreaId || 1,
        location: location,
        locationDesc: deviceForm.deviceName,
        extraConfig: extraConfig,
        status: targetDevice?.status || 'ONLINE'
      };

      try {
        let res: any;
        if (payload.id) {
          res = await request.put(`/api/v1/devices/${payload.id}`, payload);
        } else {
          res = await request.post('/api/v1/devices', payload);
        }

        if (res && res.code === 200) {
          showToast(`监控点位 [${payload.deviceName}] 及 1对1 监控巡逻区域保存成功！`, 'success');
          addLog(`[设备管理] 成功修改/创建监控点位 ${payload.deviceName} (包含 ${deviceForm.roiPolygon?.length || 0} 个巡逻拐点)`);
          closeAddModal();
          loadDevices();
          loadKPIs();
        } else {
          showToast(res.message || '打点关联失败', 'error');
        }
      } catch (e) {
        showToast('打点关联网络错误', 'error');
      }
    };

    const handleDrawPolygonComplete = (points: Array<{lng: number, lat: number}>) => {
      deviceForm.roiPolygon = points;
      showAddModal.value = true;
      showToast(`监控巡逻区域绘制成功！共包含 ${points.length} 个拐点，已与监控点位 [${deviceForm.deviceName || '该点位'}] 完成 1 对 1 绑定！`, 'success');
      addLog(`[防区绘制] 完成监控点 [${deviceForm.deviceName || '点位'}] 1对1巡逻区域绑定，共 ${points.length} 个拐点`);
    };

    const focusDeviceOnMap = (dev: any) => {
      currentTab.value = 'monitor';
      if (dev.location && dev.location.includes("(")) {
        focusOnCoordinates(dev.location);
        showToast(`已切换至地图视图，定位到监控点位 [${dev.deviceName}]`, 'success');
      } else {
        showToast('该设备尚未标定三维地图点位', 'error');
      }
    };

    const handleDeleteDevice = async (dev: any) => {
      if (!confirm(`确定要彻底删除监控点 [${dev.deviceName}] 及其绑定的 1对1 监控巡逻区域吗？三维地图上的点位和对应多边形区域也将同步移除。`)) return;
      try {
        const res: any = await request.delete(`/api/v1/devices/${dev.id}`);
        if (res && res.code === 200) {
          showToast(`监控点位 [${dev.deviceName}] 及关联巡逻区域已同步清退`, 'success');
          addLog(`[设备管理] 成功删除监控点 [${dev.deviceName}] (ID: ${dev.id})`);
          loadDevices();
          loadKPIs();
        } else {
          showToast(res.message || '删除设备失败', 'error');
        }
      } catch (e) {
        showToast('删除设备请求出错', 'error');
      }
    };

    const handleConfirmAlertClick = (alertId: number) => {
      const alertItem = alertList.value.find(a => a.id === alertId);
      if (alertItem) {
        confirmAlertEvent(alertItem, true);
      }
    };

    // User management and lifeguard mock locations implementation
    const getRoleLabel = (roleCode: string) => {
      const map: any = {
        'ROLE_ADMIN': '系统管理员',
        'ROLE_OPERATOR': '监控值守员',
        'ROLE_LEADER': '乡镇级领导',
        'ROLE_RESCUER': '现场救生员'
      };
      return map[roleCode] || roleCode || '未指定';
    };

    const getDeviceNameById = (id: number) => {
      const dev = deviceList.value.find(d => d.id === id);
      return dev ? dev.deviceName : `监控点(${id})`;
    };

    const loadUsers = async () => {
      try {
        const res: any = await request.get('/api/v1/users?pageNum=1&pageSize=100');
        if (res && res.code === 200) {
          userList.value = res.data.records;
        }
      } catch (e) {
        console.error("加载系统用户列表失败:", e);
      }
    };

    const loadActiveLifeguards = async () => {
      try {
        const res: any = await request.get('/api/v1/users/active-locations');
        if (res && res.code === 200 && res.data) {
          lifeguardList.value = res.data;
          // 实时将最新三维GPS位置与在岗状态映射更新至人物管理 (userList)
          if (userList.value && userList.value.length > 0) {
            const locMap = new Map();
            const dutyMap = new Map();
            res.data.forEach((u: any) => {
              if (u.id) {
                if (u.lastWktLocation) locMap.set(u.id, u.lastWktLocation);
                if (u.dutyStatus !== undefined && u.dutyStatus !== null) dutyMap.set(u.id, u.dutyStatus);
              }
            });
            userList.value.forEach((usr: any) => {
              if (locMap.has(usr.id)) {
                usr.lastWktLocation = locMap.get(usr.id);
              }
              if (dutyMap.has(usr.id)) {
                usr.dutyStatus = dutyMap.get(usr.id);
              }
            });
          }
        }
      } catch (e) {
        console.error("加载在岗救生员定位失败:", e);
      }
    };

    const openAddUserModal = () => {
      userForm.id = null;
      userForm.username = '';
      userForm.password = '';
      userForm.realName = '';
      userForm.phone = '';
      userForm.roleCode = 'ROLE_RESCUER';
      userForm.deviceIds = [];
      isEditingUser.value = false;
      showUserModal.value = true;
    };

    const openEditUserModal = (userData: any) => {
      userForm.id = userData.id;
      userForm.username = userData.username;
      userForm.password = ''; // 密码留空
      userForm.realName = userData.realName;
      userForm.phone = userData.phone || '';
      userForm.roleCode = userData.roleCode || 'ROLE_RESCUER';
      userForm.deviceIds = userData.deviceIds ? [...userData.deviceIds] : [];
      isEditingUser.value = true;
      showUserModal.value = true;
    };

    const closeUserModal = () => {
      showUserModal.value = false;
    };

    const submitUser = async () => {
      const payload = {
        username: userForm.username,
        realName: userForm.realName,
        phone: userForm.phone,
        roleCode: userForm.roleCode,
        deviceIds: userForm.deviceIds
      } as any;

      if (userForm.password) {
        payload.password = userForm.password;
      }

      try {
        let res: any;
        if (isEditingUser.value && userForm.id) {
          res = await request.put(`/api/v1/users/${userForm.id}`, payload);
        } else {
          if (!userForm.password) {
            showToast('创建用户密码不能为空', 'error');
            return;
          }
          res = await request.post('/api/v1/users', payload);
        }

        if (res && res.code === 200) {
          showToast('保存系统人物成功', 'success');
          closeUserModal();
          loadUsers();
          loadActiveLifeguards();
        } else {
          showToast(res.message || '保存失败', 'error');
        }
      } catch (e) {
        showToast('保存用户请求出错', 'error');
      }
    };

    const handleDeleteUser = async (userId: number) => {
      if (!confirm('确认删除该系统人物？')) return;
      try {
        const res: any = await request.delete(`/api/v1/users/${userId}`);
        if (res && res.code === 200) {
          showToast('人物删除成功', 'success');
          loadUsers();
          loadActiveLifeguards();
        } else {
          showToast(res.message || '删除失败', 'error');
        }
      } catch (e) {
        showToast('删除用户请求出错', 'error');
      }
    };

    const startMockLocation = (userData: any) => {
      mockLocationUser.value = userData;
      isDrawingLifeguard.value = true;
      currentTab.value = 'monitor'; // Switch to map tab
      showToast(`已开启 [${userData.realName}] 位置标记模式，请在三维地图上直接点击打点定位。`, 'success');
    };

    const cancelMockLocation = () => {
      isDrawingLifeguard.value = false;
      mockLocationUser.value = null;
      showToast('已取消救生员位置标记', 'error');
    };

    const handleLifeguardMapClick = async (coords: { lng: number, lat: number }) => {
      if (!mockLocationUser.value) return;
      try {
        const res: any = await request.post(`/api/v1/users/${mockLocationUser.value.id}/location?lng=${coords.lng}&lat=${coords.lat}`);
        if (res && res.code === 200) {
          showToast(`救生员 [${mockLocationUser.value.realName}] 最新定位标记成功！`, 'success');
          addLog(`[救生员打点] 标记 [${mockLocationUser.value.realName}] 定位点: (${coords.lng.toFixed(7)}, ${coords.lat.toFixed(7)})`);
          isDrawingLifeguard.value = false;
          mockLocationUser.value = null;
          loadUsers();
          loadActiveLifeguards();
        } else {
          showToast(res.message || '定位标记失败', 'error');
        }
      } catch (e) {
        showToast('上报定位打点请求出错', 'error');
      }
    };

    const toggleUserDutyStatus = async (usr: any) => {
      const newStatus = usr.dutyStatus === 1 ? 0 : 1;
      const actionName = newStatus === 1 ? '上岗' : '下岗';
      try {
        const payload = {
          id: usr.id,
          username: usr.username,
          realName: usr.realName,
          phone: usr.phone,
          roleCode: usr.roleCode || 'ROLE_RESCUER',
          deviceIds: usr.deviceIds || [],
          dutyStatus: newStatus
        };
        let res: any = await request.put(`/api/v1/users/duty-status?dutyStatus=${newStatus}`);
        if (!res || res.code !== 200) {
          res = await request.put(`/api/v1/users/${usr.id}`, payload);
        }
        if (res && res.code === 200) {
          showToast(`救生员 [${usr.realName}] 已成功切换为 [${actionName}] 状态`, 'success');
          addLog(`[救生员管理] 救生员 ${usr.realName} 在岗状态切换为 ${actionName}`);
          loadUsers();
          loadActiveLifeguards();
        } else {
          showToast(res.message || '切换在岗状态失败', 'error');
        }
      } catch (e) {
        showToast('网络通信异常', 'error');
      }
    };

    const focusOnCoordinates = (locationWkt: string) => {
      if (!locationWkt) return;
      try {
        const content = locationWkt.substring(locationWkt.indexOf("(") + 1, locationWkt.indexOf(")"));
        const parts = content.split(" ");
        const lng = parseFloat(parts[0]);
        const lat = parseFloat(parts[1]);
        showToast(`救生员坐标: 经度 ${lng.toFixed(7)}, 纬度 ${lat.toFixed(7)}`, 'success');
      } catch (e) {
        console.error("解析 WKT POINT 失败:", e);
      }
    };

    const confirmAlertEvent = async (item: any, isReal: boolean) => {
      let reason = '';
      if (!isReal) {
        reason = prompt("请输入误报关闭的原因:", "飞鸟反光干扰") || '误报';
      }

      try {
        const res: any = await request.post(`/api/v1/alerts/${item.id}/confirm`, {
          isReal,
          reason
        });

        if (res && res.code === 200) {
          addLog(`判定事件 #${item.eventNo} 为 ${isReal ? '真实溺水' : '误报'}，已启动紧急调度`);
          loadAlerts();
          loadKPIs();
          activeAlert.value = null;
        }
      } catch (e) {
        showToast('确认预警操作网络异常', 'error');
      }
    };

    // Monitoring wall specific helper methods
    const isWallFullscreen = ref(false);

    const toggleWallFullscreen = () => {
      const elem = document.getElementById('wall-grid-container');
      if (!elem) return;
      if (!document.fullscreenElement) {
        if (elem.requestFullscreen) {
          elem.requestFullscreen();
        } else if ((elem as any).webkitRequestFullscreen) {
          (elem as any).webkitRequestFullscreen();
        } else if ((elem as any).msRequestFullscreen) {
          (elem as any).msRequestFullscreen();
        }
        isWallFullscreen.value = true;
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen();
        }
        isWallFullscreen.value = false;
      }
    };

    const toggleCellFullscreen = (slotIndex: number) => {
      const cellElem = document.getElementById(`wall-cell-${slotIndex}`) || document.getElementById(`wall-canvas-${slotIndex}`);
      if (!cellElem) return;
      if (!document.fullscreenElement) {
        if (cellElem.requestFullscreen) {
          cellElem.requestFullscreen();
        } else if ((cellElem as any).webkitRequestFullscreen) {
          (cellElem as any).webkitRequestFullscreen();
        } else if ((cellElem as any).msRequestFullscreen) {
          (cellElem as any).msRequestFullscreen();
        }
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen();
        }
      }
    };

    if (typeof window !== 'undefined') {
      document.addEventListener('fullscreenchange', () => {
        isWallFullscreen.value = !!document.fullscreenElement;
      });
    }

    const setWallGridSize = (size: number) => {
      wallGridSize.value = size;
      // Stop and restart players for active size slots
      destroyAllWallSlots();
      nextTick(() => {
        startAllWallSlots();
      });
    };

    const playWallSlotStream = (slotIndex: number, deviceId: number) => {
      const device = deviceList.value.find(d => d.id === deviceId);
      if (!device || device.deviceType !== 'CAMERA') return;

      if (wallSlots.value[slotIndex].player) {
        try {
          wallSlots.value[slotIndex].player.destroy();
        } catch (e) {}
        wallSlots.value[slotIndex].player = null;
      }

      nextTick(() => {
        const canvas = document.getElementById(`wall-canvas-${slotIndex}`) as HTMLCanvasElement;
        if (!canvas) return;

        const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${wsProto}//${window.location.host}/api/ws/stream/${device.id}`;

        if (typeof (window as any).JSMpeg === 'undefined') {
          setTimeout(() => playWallSlotStream(slotIndex, deviceId), 300);
          return;
        }

        try {
          const player = new (window as any).JSMpeg.Player(wsUrl, {
            canvas: canvas,
            autoplay: true,
            audio: false
          });
          wallSlots.value[slotIndex].player = player;
        } catch (e) {
          console.error(`Failed to play slot ${slotIndex} stream`, e);
        }
      });
    };

    const assignDeviceToSlot = (slotIndex: number, event: any) => {
      const val = event.target.value;
      if (!val) {
        unbindSlot(slotIndex);
        return;
      }
      const deviceId = parseInt(val);
      wallSlots.value[slotIndex].deviceId = deviceId;
      playWallSlotStream(slotIndex, deviceId);

      const dev = deviceList.value.find(d => d.id === deviceId);
      if (dev) {
        wallSlots.value[slotIndex].detections = dev.extraConfig?.detections || [];
        wallSlots.value[slotIndex].statusInfo = {
          selectedModel: dev.extraConfig?.selectedModel || 'best.pt',
          inferenceFps: dev.extraConfig?.inferenceFps ?? 25,
          decodeFps: dev.extraConfig?.decodeFps ?? 25,
          device: dev.extraConfig?.device || 'cuda',
          status: dev.status
        };
      }
    };

    const unbindSlot = (slotIndex: number) => {
      if (wallSlots.value[slotIndex].player) {
        try {
          wallSlots.value[slotIndex].player.destroy();
        } catch (e) {}
        wallSlots.value[slotIndex].player = null;
      }
      wallSlots.value[slotIndex].deviceId = null;
      wallSlots.value[slotIndex].detections = [];
      wallSlots.value[slotIndex].statusInfo = null;
    };

    const changeSlotModel = async (slot: any, event: any) => {
      const modelName = event.target.value;
      if (!slot.deviceId) return;
      try {
        const res: any = await request.post(`/api/v1/devices/${slot.deviceId}/model`, {
          modelName: modelName
        });
        if (res && res.code === 200) {
          showToast(`该通道模型成功切换为 ${modelName}`, 'success');
          if (slot.statusInfo) {
            slot.statusInfo.selectedModel = modelName;
          }
          addLog(`[监控墙] 通道模型动态切换为 ${modelName}`);
        } else {
          showToast(res.message || '切换失败', 'error');
        }
      } catch (err) {
        showToast('通信异常', 'error');
      }
    };

    const checkSlotDrowning = (slot: any) => {
      if (!slot.detections) return false;
      return slot.detections.some((d: any) => /drowing|drowning/i.test(d.className || d.class_name || ''));
    };

    const getSlotFpsInfo = (slot: any) => {
      if (!slot.statusInfo) return '';
      const info = slot.statusInfo;
      return `${info.device.toUpperCase()} | FPS: ${info.inferenceFps} | D: ${info.decodeFps}`;
    };

    const getSlotModel = (slot: any) => {
      return slot.statusInfo?.selectedModel || 'best.pt';
    };

    const availableCameras = computed(() => {
      const boundIds = wallSlots.value.slice(0, wallGridSize.value).map(s => s.deviceId).filter(id => id !== null);
      return deviceList.value.filter(d => d.deviceType === 'CAMERA' && !boundIds.includes(d.id));
    });

    const cameraDevices = computed(() => {
      return deviceList.value.filter(d => d.deviceType === 'CAMERA');
    });

    const startAllWallSlots = () => {
      for (let i = 0; i < wallGridSize.value; i++) {
        const slot = wallSlots.value[i];
        if (slot.deviceId) {
          playWallSlotStream(i, slot.deviceId);
        }
      }
    };

    const destroyAllWallSlots = () => {
      for (let i = 0; i < wallSlots.value.length; i++) {
        unbindSlot(i);
      }
    };

    const switchTab = (tab: string) => {
      currentTab.value = tab;

      if (tab === 'wall') {
        // Stop active camera stream in monitor tab to release thread/FFmpeg source
        if (jsmpegPlayer) {
          try {
            jsmpegPlayer.destroy();
          } catch (e) {}
          jsmpegPlayer = null;
        }
        useSimulatedFeed.value = false;

        nextTick(() => {
          startAllWallSlots();
        });
      } else {
        // Destroy wall slot connections when switching away
        destroyAllWallSlots();

        if (tab === 'monitor') {
          const activeCam = activeCameraDevice.value || deviceList.value.find(d => d.deviceType === 'CAMERA');
          if (activeCam) {
            nextTick(() => {
              playCameraStream(activeCam);
            });
          } else {
            useSimulatedFeed.value = true;
            runVideoSimulation();
          }
        }
      }

      if (tab === 'analysis') {
        nextTick(() => {
          renderCharts();
        });
      }
      if (tab === 'recordings') {
        loadRecordings();
      }
    };

    // Render ECharts
    let trendChart: echarts.ECharts | null = null;
    let distChart: echarts.ECharts | null = null;

    const renderCharts = async () => {
      if (trendChartDom.value) {
        if (trendChart) {
          trendChart.dispose();
        }
        trendChart = echarts.init(trendChartDom.value, theme.value === 'dark' ? 'dark' : undefined);
        const res: any = await request.get('/api/v1/statistics/trends?days=7');
        if (res && res.code === 200) {
          trendChart.setOption({
            backgroundColor: 'transparent',
            tooltip: { trigger: 'axis' },
            legend: { data: ['溺水告警起数', '救援指派完成'], textStyle: { color: theme.value === 'dark' ? '#94a3b8' : '#475569' } },
            xAxis: { type: 'category', data: res.data.dates, axisLabel: { color: theme.value === 'dark' ? '#94a3b8' : '#475569' } },
            yAxis: { type: 'value', axisLabel: { color: theme.value === 'dark' ? '#94a3b8' : '#475569' } },
            series: [
              { name: '溺水告警起数', type: 'line', data: res.data.alertCounts, smooth: true, color: '#ef4444' },
              { name: '救援指派完成', type: 'bar', data: res.data.rescueCounts, color: '#10b981' }
            ]
          });
        }
      }

      if (distChartDom.value) {
        if (distChart) {
          distChart.dispose();
        }
        distChart = echarts.init(distChartDom.value, theme.value === 'dark' ? 'dark' : undefined);
        const res: any = await request.get('/api/v1/statistics/water-areas');
        if (res && res.code === 200) {
          const distribution = res.data.distribution;
          const chartData: any[] = [];
          for (const [key, value] of Object.entries(distribution)) {
            const name = waterAreas.value.find(w => w.id == key)?.name || `水域 #${key}`;
            chartData.push({ name, value });
          }

          distChart.setOption({
            backgroundColor: 'transparent',
            tooltip: { trigger: 'item' },
            legend: { textStyle: { color: theme.value === 'dark' ? '#94a3b8' : '#475569' } },
            series: [{
              name: '报警占比',
              type: 'pie',
              radius: '55%',
              data: chartData.length > 0 ? chartData : [{ name: '黄村水库', value: 3 }, { name: '南河段', value: 5 }],
              emphasis: {
                itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
              }
            }]
          });
        }
      }
    };

    // Active camera model change API call
    const changeActiveCameraModel = async () => {
      if (!activeCameraDevice.value) {
        showToast('当前未选择活动摄像头', 'error');
        return;
      }
      try {
        const res: any = await request.post(`/api/v1/devices/${activeCameraDevice.value.id}/model`, {
          modelName: activeCameraModel.value
        });
        if (res && res.code === 200) {
          showToast(`当前摄像头AI算法成功切换为 ${activeCameraModel.value}`, 'success');
          addLog(`[AI端点] 摄像头 ${activeCameraDevice.value.deviceName} AI模型切换为 ${activeCameraModel.value}`);
        } else {
          showToast(res.message || '切换失败', 'error');
        }
      } catch (e) {
        showToast('模型热加载通信异常', 'error');
      }
    };

    // Real RTSP Stream Player via WebSocket & JSMpeg
    const playCameraStream = (device: DeviceData) => {
      if (!device || device.deviceType !== 'CAMERA') return;

      activeCameraDevice.value = device;
      activeCameraName.value = `${device.deviceName} (实时画面已载入)`;
      useSimulatedFeed.value = false; // Stop canvas simulation
      activeCameraModel.value = device.extraConfig?.selectedModel || 'best.pt';
      activeCameraDetections.value = device.extraConfig?.detections || [];

      if (jsmpegPlayer) {
        try {
          jsmpegPlayer.destroy();
        } catch (e) {}
        jsmpegPlayer = null;
      }

      const canvas = liveCanvas.value;
      if (!canvas) return;

      const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
      const wsUrl = `${wsProto}//${window.location.host}/api/ws/stream/${device.id}`;

      if (typeof (window as any).JSMpeg === 'undefined') {
        // If script not loaded yet, retry shortly
        setTimeout(() => playCameraStream(device), 300);
        return;
      }

      addLog(`[监控中心] 正在连接摄像头 ${device.deviceName} 实时流...`);

      try {
        jsmpegPlayer = new (window as any).JSMpeg.Player(wsUrl, {
          canvas: canvas,
          autoplay: true,
          audio: false,
          onPlay: () => {
            addLog(`[监控中心] 摄像头 ${device.deviceName} 视频流解码播放成功`);
          }
        });
      } catch (err: any) {
        showToast('视频解码播放失败: ' + err.message, 'error');
      }
    };

    const handleSelectDevice = (dev: DeviceData) => {
      if (dev.deviceType === 'CAMERA') {
        playCameraStream(dev);
      } else {
        showToast(`选中的设备 ${dev.deviceName} 不是摄像机`, 'success');
      }
    };

    const handleDeviceRowClick = (dev: DeviceData) => {
      handleSelectDevice(dev);
      switchTab('monitor'); // Jump to live monitor tab
    };

    // Simulated Canvas Drawing (fallback when no cameras)
    const runVideoSimulation = () => {
      const canvas = liveCanvas.value;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      canvas.width = 640;
      canvas.height = 360;

      let count = 0;
      const draw = () => {
        if (!useSimulatedFeed.value || !liveCanvas.value) return; // Terminate recursion
        ctx.fillStyle = '#0f172a';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // draw water
        ctx.fillStyle = '#1e3a8a';
        ctx.beginPath();
        ctx.moveTo(0, 200);
        for (let i = 0; i <= canvas.width; i += 20) {
          ctx.lineTo(i, 200 + Math.sin((i + count) * 0.05) * 15);
        }
        ctx.lineTo(canvas.width, canvas.height);
        ctx.lineTo(0, canvas.height);
        ctx.closePath();
        ctx.fill();

        // draw person
        const px = 280 + Math.sin(count * 0.02) * 50;
        const py = 180;
        ctx.fillStyle = '#e2e8f0';
        ctx.beginPath();
        ctx.arc(px, py - 20, 6, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillRect(px - 4, py - 14, 8, 20);

        // bounding box
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 2;
        ctx.strokeRect(px - 15, py - 30, 30, 45);
        ctx.fillStyle = '#ef4444';
        ctx.font = '10px sans-serif';
        ctx.fillText('人员-靠近危险水域: 96.8%', px - 15, py - 35);

        count++;
        requestAnimationFrame(draw);
      };
      draw();
    };

    const simulateBroadcast = () => {
      addLog("[广播联动] 音柱广播已开启 -> '请远离河道，安全重于泰山'！");
    };

    const simulateManualTrigger = async () => {
      addLog("[AI端点] 上报新疑似溺水警报并产生救援任务单...");
      const dev = deviceList.value && deviceList.value.length > 0 ? deviceList.value[0] : null;
      const body = {
        sourceDeviceId: dev ? dev.id : 1,
        waterAreaId: dev ? dev.waterAreaId : 1,
        longitude: 120.0304565 + (Math.random() - 0.5) * 0.004,
        latitude: 28.5064125 + (Math.random() - 0.5) * 0.004,
        confidence: 92.50,
        alertType: 'DROWNING',
        status: 'PENDING',
        snapshotUrl: '/static/recordings/rec_drowning_snapshot.jpg'
      };

      try {
        const res: any = await request.post('/api/v1/alerts/trigger', body);
        if (res && res.code === 200) {
          showToast(`⚡ 成功触发并产生全新溺水救援任务单 #${res.data?.eventNo || res.data?.id}！`, 'success');
          addLog(`[任务中心] 产生新溺水救援任务单 #${res.data?.eventNo}`);
          loadTasks();
          loadAlerts();
          loadKPIs();
        }
      } catch (e) {
        showToast('模拟上报网络异常', 'error');
      }
    };

    const simulateDroneTakeoff = () => {
      addLog("[无人机] 指导 H01 机库自主起飞，正在投送定位坐标及回传实时空中侦察画面。");
    };

    const handleLogout = () => {
      localStorage.clear();
      router.push('/login');
    };

    // Helper formatting methods
    const getAlertStatusClass = (status: string) => {
      if (status === 'PENDING') return 'high';
      if (status === 'PROCESSED' || status === 'CONFIRMED' || status === 'RESOLVED') return 'low';
      return 'medium';
    };

    const getAlertStatusText = (status: string) => {
      if (status === 'PENDING') return '待处理';
      if (status === 'PROCESSED' || status === 'CONFIRMED' || status === 'RESOLVED') return '已处理';
      return '误报已关闭';
    };

    let pollingTimer: any = null;
    let locationTimer: any = null;

    onMounted(() => {
      theme.value = 'light';
      document.documentElement.classList.remove('dark-theme');
      localStorage.setItem('theme', 'light');

      loadProfile();
      loadKPIs();
      loadWaterAreas();
      loadDevices();
      loadAudioModels();
      loadTasks();
      loadAlerts();
      loadHistoricalDetections();
      loadUsers();
      loadActiveLifeguards();

      // 每 1 秒独立实时获取人物最新三维 GPS 定位 (1000ms 高频刷新)
      locationTimer = setInterval(() => {
        loadActiveLifeguards();
      }, 1000);

      // 每 3 秒轮询其他业务数据与人物全量列表
      pollingTimer = setInterval(() => {
        loadKPIs();
        loadDevices();
        loadTasks();
        loadAlerts();
        loadHistoricalDetections();
        loadUsers();
      }, 3000);

      addLog("三维防溺水态势管理决策平台初始化就绪");
    });

    onUnmounted(() => {
      if (jsmpegPlayer) {
        try {
          jsmpegPlayer.destroy();
        } catch (e) {}
      }
      destroyAllWallSlots();
      useSimulatedFeed.value = false;
      if (pollingTimer) {
        clearInterval(pollingTimer);
      }
      if (locationTimer) {
        clearInterval(locationTimer);
      }
    });

    return {
      currentTab,
      user,
      kpis,
      deviceList,
      alertList,
      waterAreas,
      activeAlert,
      showAddModal,
      deviceForm,
      toast,
      linkageLogs,
      trendChartDom,
      distChartDom,
      liveCanvas,
      theme,
      toggleTheme,
      activeCameraName,
      playCameraStream,
      handleSelectDevice,
      handleDeviceRowClick,
      cesiumMapRef,
      openEditDeviceModal,
      startDrawPatrolPolygonForDevice,
      focusDeviceOnMap,
      handleDeleteDevice,
      translateRole,
      handleLogout,
      handleMapClick,
      handleConfirmAlertClick,
      closeAddModal,
      submitDevice,
      confirmAlertEvent,
      switchTab,
      simulateBroadcast,
      simulateManualTrigger,
      simulateDroneTakeoff,
      getAlertStatusClass,
      getAlertStatusText,
      // Wall and model control exports
      wallGridSize,
      isWallFullscreen,
      toggleWallFullscreen,
      toggleCellFullscreen,
      activeCameraModel,
      activeCameraThreshold,
      activeCameraDetections,
      getThresholdModeBadgeClass,
      getThresholdModeText,
      getThresholdModeDesc,
      changeActiveCameraThreshold,
      wallSlots,
      changeActiveCameraModel,
      setWallGridSize,
      assignDeviceToSlot,
      unbindSlot,
      changeSlotModel,
      checkSlotDrowning,
      getSlotFpsInfo,
      getSlotModel,
      availableCameras,
      cameraDevices,
      detectionHistory,
      selectedDeviceId,
      handleDrawPolygonComplete,
      historicalDetections,
      loadHistoricalDetections,
      getDeviceNameByCode,
      getDeviceModelByCode,

      // Audio ONNX model exports
      audioModelList,
      audioOnnxFileInput,
      audioCsvFileInput,
      availableAudioClasses,
      selectAllAudioClasses,
      clearAudioClasses,
      handleUploadAudioModelFiles,

      // User management and lifeguard mock locations
      userList,
      lifeguardList,
      showUserModal,
      isEditingUser,
      isDrawingLifeguard,
      mockLocationUser,
      userForm,
      getRoleLabel,
      getDeviceNameById,
      openAddUserModal,
      openEditUserModal,
      closeUserModal,
      submitUser,
      handleDeleteUser,
      startMockLocation,
      cancelMockLocation,
      handleLifeguardMapClick,
      toggleUserDutyStatus,
      focusOnCoordinates,

      // Task Center exports
      taskStatusFilter,
      filteredTasksList,
      taskCounts,
      getTaskStatusClass,
      getTaskStatusText,
      getDeviceLocationById,
      getDeviceThresholdById,
      formatLocationCoords,
      getTaskSnapshotUrl,
      handleImageError,
      changeTaskStatus,

      // Recording center & threshold recording exports
      recordingList,
      showVideoModal,
      activeVideo,
      activeVideoUrl,
      loadRecordings,
      playVideoModal,
      downloadVideo,
      deleteRecordingItem,
      clearAllRecordings,
      deleteAlertEvent,
      deleteDetectionItem,
      clearAllDetections,
      exportAlertsCSV,
      exportDetectionsCSV,
      exportRecordingsCSV
    };
  }
});
</script>

<style scoped>
.dashboard-wrapper {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-dark);
  color: var(--text-white);
  overflow: hidden;
}

.wall-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-card);
  padding: 10px 16px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.wall-grid-container {
  display: grid;
  flex-grow: 1;
  gap: 12px;
  height: calc(100vh - 200px);
  overflow-y: auto;
}

.wall-grid-container.grid-1 {
  grid-template-columns: 1fr;
  grid-template-rows: 1fr;
}

.wall-grid-container.grid-4 {
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
}

.wall-grid-container.grid-9 {
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
}

.wall-cell {
  background: var(--bg-card);
  border-radius: 6px;
  border: 1px solid var(--border-color);
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  transition: all 0.3s ease;
}

.wall-cell.has-drowning {
  border-color: #ef4444 !important;
  box-shadow: 0 0 15px rgba(239, 68, 68, 0.4);
  animation: pulse-border 1.5s infinite alternate;
}

@keyframes pulse-border {
  from {
    border-color: #ef4444;
    box-shadow: 0 0 5px rgba(239, 68, 68, 0.2);
  }
  to {
    border-color: #f87171;
    box-shadow: 0 0 20px rgba(239, 68, 68, 0.6);
  }
}

.btn-grid {
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 11px;
  font-weight: 600;
  transition: all 0.2s ease;
  margin-right: 6px;
}

.btn-grid.active {
  background: #1c6386;
  color: #f0faff;
  border-color: #3486a5;
}

.btn-grid:hover {
  border-color: var(--primary);
  color: var(--text-white);
}

.wall-cell-footer {
  background: rgba(15, 23, 42, 0.95);
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--border-color);
  font-size: 12px;
  z-index: 5;
}

.badge-fine {
  background: rgba(6, 182, 212, 0.15);
  color: var(--primary);
  border: 1px solid rgba(6, 182, 212, 0.35);
}

.badge-balanced {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.35);
}

.badge-strict {
  background: rgba(245, 158, 11, 0.15);
  color: var(--warning);
  border: 1px solid rgba(245, 158, 11, 0.35);
}
</style>
