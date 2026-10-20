/* ==========================================================================
   黄村乡 AI 防溺水管理系统 - 后端联调与控制中心逻辑 (app.js)
   ========================================================================== */

// Auth token check
const token = localStorage.getItem('token');
const tokenHead = localStorage.getItem('tokenHead') || 'Bearer ';
if (!token) {
    window.location.href = 'index.html';
}

// Request Helper
async function apiFetch(url, options = {}) {
    options.headers = options.headers || {};
    options.headers['Authorization'] = `${tokenHead}${token}`;
    if (!(options.body instanceof FormData) && typeof options.body === 'object') {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(options.body);
    }

    try {
        const response = await fetch(url, options);
        if (response.status === 401) {
            localStorage.clear();
            window.location.href = 'index.html';
            return null;
        }
        return await response.json();
    } catch (e) {
        console.error(`API Error on ${url}:`, e);
        return { code: 500, message: '网络异常' };
    }
}

// Global Variables
let map;
let deviceMarkers = [];
let drawingMode = false;
let clickCoords = null;
let trendChart, distributionChart;
let waterAreas = [];

// Initialize Page
document.addEventListener('DOMContentLoaded', () => {
    loadUserProfile();
    initMap();
    initVideoSimulation();
    loadKPIs();
    loadWaterAreas();
    loadDeviceList();
    loadAlertEvents();
});

// Load User info
async function loadUserProfile() {
    const res = await apiFetch('/api/v1/auth/info');
    if (res && res.code === 200) {
        document.getElementById('profileName').innerText = res.data.realName || res.data.username;
        document.getElementById('profileRole').innerText = translateRole(res.data.roles[0]);
    }
}

function translateRole(role) {
    const map = {
        'ROLE_ADMIN': '系统管理员',
        'ROLE_OPERATOR': '监控值守员',
        'ROLE_LEADER': '乡镇领导',
        'ROLE_RESCUER': '现场救生员'
    };
    return map[role] || role;
}

function handleLogout() {
    localStorage.clear();
    window.location.href = 'index.html';
}

// Load KPIs
async function loadKPIs() {
    const res = await apiFetch('/api/v1/statistics/overview');
    if (res && res.code === 200) {
        const data = res.data;
        document.getElementById('kpi-devices').innerHTML = `${data.onlineDeviceCount}<span>/${data.totalDeviceCount}</span>`;
        document.getElementById('kpi-alerts').innerText = data.pendingAlertCount;
        document.getElementById('kpi-completed').innerText = data.todayCompletedRescueCount;

        // Simulating some dynamic indicators for counts
        document.getElementById('kpi-people').innerText = Math.floor(Math.random() * 8) + 12; // 模拟涉水人数
        document.getElementById('kpi-lifeguards').innerText = Math.floor(Math.random() * 3) + 4; // 在岗人员

        if (data.pendingAlertCount > 0) {
            document.getElementById('kpi-alerts-trend').innerText = `待处理警报: ${data.pendingAlertCount}起`;
            document.getElementById('kpi-alerts-trend').className = 'kpi-trend up';
        } else {
            document.getElementById('kpi-alerts-trend').innerText = '当前水域整体安全';
            document.getElementById('kpi-alerts-trend').className = 'kpi-trend down';
        }
    }
}

// Fetch Water Areas for Selector
async function loadWaterAreas() {
    const res = await apiFetch('/api/v1/water-areas');
    if (res && res.code === 200) {
        waterAreas = res.data.records;
        const selector = document.getElementById('devWaterArea');
        selector.innerHTML = '';
        waterAreas.forEach(area => {
            const opt = document.createElement('option');
            opt.value = area.id;
            opt.innerText = area.name;
            selector.appendChild(opt);
        });
    }
}

// Initialize Leaflet with High-Res Gaode Satellite Map and Road Network Overlay
function initMap() {
    // Center point coordinates focused to [28.5064125, 120.0304565]
    const center = [28.5064125, 120.0304565];
    map = L.map('map', {
        center: center,
        zoom: 16,
        zoomControl: true,
        attributionControl: false
    });

    // Gaode Satellite layer
    const satLayer = L.tileLayer('https://webst0{s}.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}', {
        subdomains: '1234',
        maxZoom: 18,
        minZoom: 3
    });

    // Gaode Road overlay
    const roadLayer = L.tileLayer('https://webst0{s}.is.autonavi.com/appmaptile?style=8&x={x}&y={y}&z={z}', {
        subdomains: '1234',
        maxZoom: 18,
        minZoom: 3
    });

    // Group them
    const baseMap = L.layerGroup([satLayer, roadLayer]);
    baseMap.addTo(map);

    // Initial center marker
    L.circleMarker(center, {
        color: '#ef4444',
        fillColor: '#ef4444',
        fillOpacity: 0.2,
        radius: 120
    }).addTo(map).bindPopup("<b>黄村乡核心防溺水控制区</b><br>AI监测及无人机巡海中心");

    // Load registered devices on Map
    loadDevicesOnMap();

    // Map Click Listener (for device registration placement)
    map.on('click', (e) => {
        if (drawingMode) {
            clickCoords = e.latlng;
            openDeviceModal(clickCoords.lng, clickCoords.lat);
        }
    });
}

// Toggle Drawing Mode
function toggleDeviceDrawing() {
    drawingMode = !drawingMode;
    const btn = document.getElementById('btn-draw-device');
    btn.classList.toggle('active', drawingMode);

    if (drawingMode) {
        map.getContainer().style.cursor = 'crosshair';
        addLinkageLog("[系统] 已开启地图打点注册模式，请在卫星地图上点击以安装设备");
    } else {
        map.getContainer().style.cursor = '';
    }
}

// Open Device Registration Dialog
function openDeviceModal(lng, lat) {
    document.getElementById('devLng').value = lng.toFixed(7);
    document.getElementById('devLat').value = lat.toFixed(7);
    document.getElementById('addDeviceModal').classList.add('show');
}

function closeDeviceModal() {
    document.getElementById('addDeviceModal').classList.remove('show');
    // Disable drawing mode
    drawingMode = false;
    document.getElementById('btn-draw-device').classList.remove('active');
    map.getContainer().style.cursor = '';
}

// Submit Registered Device at coordinate points
async function submitNewDevice(e) {
    e.preventDefault();
    const devCode = document.getElementById('devCode').value.trim();
    const devName = document.getElementById('devName').value.trim();
    const devType = document.getElementById('devType').value;
    const ipAddress = document.getElementById('devIp').value.trim();
    const port = parseInt(document.getElementById('devPort').value);
    const waterAreaId = document.getElementById('devWaterArea').value;
    const locationDesc = document.getElementById('devDesc').value.trim();
    const lng = parseFloat(document.getElementById('devLng').value);
    const lat = parseFloat(document.getElementById('devLat').value);

    // Build PostGIS WKT Point string
    const location = `POINT(${lng} ${lat})`;

    const body = {
        deviceCode: devCode,
        deviceName: devName,
        deviceType: devType,
        ipAddress: ipAddress,
        port: port,
        location: location,
        locationDesc: locationDesc,
        waterAreaId: waterAreaId,
        status: 'ONLINE' // Default online for debug simplicity
    };

    const res = await apiFetch('/api/v1/devices', {
        method: 'POST',
        body: body
    });

    if (res && res.code === 200) {
        addLinkageLog(`[设备管理] 成功注册设备: ${devName} [${devType}]`);
        closeDeviceModal();
        loadDevicesOnMap();
        loadDeviceList();
        loadKPIs();
    } else {
        alert(res ? res.message : '注册失败');
    }
}

// Fetch devices and display markers
async function loadDevicesOnMap() {
    // Clear old markers
    deviceMarkers.forEach(m => map.removeLayer(m));
    deviceMarkers = [];

    const res = await apiFetch('/api/v1/devices?pageSize=100');
    if (res && res.code === 200) {
        const devices = res.data.records;
        devices.forEach(dev => {
            if (!dev.location) return;
            // Parse point coordinates
            const content = dev.location.substring(dev.location.indexOf("(") + 1, dev.location.indexOf(")"));
            const parts = content.split(" ");
            const lng = parseFloat(parts[0]);
            const lat = parseFloat(parts[1]);

            // Custom icon based on type
            let iconText = '🎥';
            let color = '#0ea5e9';
            if (dev.deviceType === 'SPEAKER') { iconText = '📢'; color = '#10b981'; }
            else if (dev.deviceType === 'DRONE_HANGAR') { iconText = '🏗️'; color = '#8b5cf6'; }
            else if (dev.deviceType === 'DRONE') { iconText = '🚁'; color = '#f59e0b'; }

            const customIcon = L.divIcon({
                html: `<div style="background: ${color}; width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center; border: 2px solid white; box-shadow: 0 2px 10px rgba(0,0,0,0.4); font-size: 14px;">${iconText}</div>`,
                className: 'custom-div-icon',
                iconSize: [30, 30],
                iconAnchor: [15, 15]
            });

            const marker = L.marker([lat, lng], { icon: customIcon }).addTo(map);
            marker.bindPopup(`
                <b>${dev.deviceName}</b><br>
                编码: ${dev.deviceCode}<br>
                类型: ${dev.deviceType}<br>
                状态: <span style="color: ${dev.status === 'ONLINE' ? '#10b981' : '#ef4444'}">${dev.status}</span><br>
                位置: ${dev.locationDesc || '无'}
            `);
            deviceMarkers.push(marker);
        });
    }
}

// Fetch and load Device List (Management Panel)
async function loadDeviceList() {
    const res = await apiFetch('/api/v1/devices?pageSize=100');
    if (res && res.code === 200) {
        const list = res.data.records;
        const tbody = document.getElementById('devicesTableBody');
        tbody.innerHTML = '';
        list.forEach(dev => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${dev.deviceCode}</td>
                <td><b>${dev.deviceName}</b></td>
                <td><span class="user-role">${dev.deviceType}</span></td>
                <td>${dev.ipAddress}:${dev.port}</td>
                <td>${dev.locationDesc || '未填写'}</td>
                <td><span class="badge-risk ${dev.status === 'ONLINE' ? 'low' : 'high'}">${dev.status}</span></td>
                <td>${dev.lastHeartbeat || '暂无心跳'}</td>
            `;
            tbody.appendChild(tr);
        });
    }
}

// Fetch and load Alerts List
async function loadAlertEvents() {
    const res = await apiFetch('/api/v1/alerts?pageSize=50');
    if (res && res.code === 200) {
        const list = res.data.records;
        const tbody = document.getElementById('alertsTableBody');
        tbody.innerHTML = '';
        list.forEach(item => {
            let statusColor = 'medium';
            let statusText = item.status;
            if (item.status === 'PENDING') { statusColor = 'high'; statusText = '🚨 待确认'; }
            else if (item.status === 'CONFIRMED') { statusColor = 'medium'; statusText = '📢 已确认派发'; }
            else if (item.status === 'RESOLVED') { statusColor = 'low'; statusText = '✔️ 已处置归档'; }
            else if (item.status === 'FALSE_ALARM') { statusColor = 'low'; statusText = '关闭 (误报)'; }

            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${item.eventNo}</td>
                <td><b>${item.alertType === 'DROWNING' ? '溺水求救行为' : item.alertType}</b></td>
                <td><span style="font-family:monospace; font-weight:600; color:var(--danger)">${item.confidence}%</span></td>
                <td>${item.waterAreaName || '未知防范区'}</td>
                <td><span class="badge-risk ${statusColor}">${statusText}</span></td>
                <td>${item.createdAt}</td>
                <td>
                    ${item.status === 'PENDING' ?
                        `<button class="btn-action primary" style="padding: 4px 8px; font-size:11px;" onclick="confirmAlertEvent(${item.id}, true)">确认并派单</button>
                         <button class="btn-action" style="padding: 4px 8px; font-size:11px; margin-left:4px;" onclick="confirmAlertEvent(${item.id}, false)">误报</button>`
                        : '无操作'
                    }
                </td>
            `;
            tbody.appendChild(tr);

            // If pending, also trigger map display
            if (item.status === 'PENDING' && item.alertLocation) {
                highlightPendingAlertOnMap(item);
            }
        });
    }
}

// Highlighting pending alerts on map
function highlightPendingAlertOnMap(event) {
    const content = event.alertLocation.substring(event.alertLocation.indexOf("(") + 1, event.alertLocation.indexOf(")"));
    const parts = content.split(" ");
    const lng = parseFloat(parts[0]);
    const lat = parseFloat(parts[1]);

    const marker = L.circleMarker([lat, lng], {
        color: '#ef4444',
        fillColor: '#ef4444',
        fillOpacity: 0.6,
        radius: 15
    }).addTo(map);

    marker.bindPopup(`
        <div class="alert-popup-card">
            <div class="alert-popup-title">🚨 疑似防溺水告警! (置信度 ${event.confidence}%)</div>
            <div class="alert-popup-body">
                时间: ${event.createdAt}<br>
                所属防范区: ${event.waterAreaName || '核心防区'}
            </div>
            <div class="alert-popup-actions">
                <button class="btn-action primary" style="padding:4px 8px; font-size:10px;" onclick="confirmAlertEvent(${event.id}, true)">确认派单</button>
                <button class="btn-action" style="padding:4px 8px; font-size:10px;" onclick="confirmAlertEvent(${event.id}, false)">关闭</button>
            </div>
        </div>
    `).openPopup();
}

// Confirming Alert Events via Backend Call
async function confirmAlertEvent(id, isReal) {
    let reason = '';
    if (!isReal) {
        reason = prompt("请输入误报关闭的原因:", "飞鸟反光干扰") || '误报';
    }

    const res = await apiFetch(`/api/v1/alerts/${id}/confirm`, {
        method: 'POST',
        body: { isReal, reason }
    });

    if (res && res.code === 200) {
        addLinkageLog(`[值守决策] 判定事件 #${id} 为 ${isReal ? '真实溺水' : '误报'}，系统已自动触发应急广播联动与无人机指令`);
        loadAlertEvents();
        loadKPIs();
        map.closePopup();
    } else {
        alert(res ? res.message : '操作失败');
    }
}

// Tab Panels Switches
function switchMainTab(tab) {
    document.querySelectorAll('.nav-tab').forEach(btn => btn.classList.remove('active'));
    document.querySelectorAll('.main-content').forEach(panel => panel.classList.add('hidden'));
    document.getElementById('right-sidebar').classList.add('hidden');

    if (tab === 'monitor') {
        event.target.classList.add('active');
        document.getElementById('panel-monitor').classList.remove('hidden');
        document.getElementById('right-sidebar').classList.remove('hidden');
        // Redraw map
        setTimeout(() => map.invalidateSize(), 100);
    } else if (tab === 'alerts') {
        event.target.classList.add('active');
        document.getElementById('panel-alerts').classList.remove('hidden');
        loadAlertEvents();
    } else if (tab === 'devices') {
        event.target.classList.add('active');
        document.getElementById('panel-devices').classList.remove('hidden');
        loadDeviceList();
    } else if (tab === 'analysis') {
        event.target.classList.add('active');
        document.getElementById('panel-analysis').classList.remove('hidden');
        setTimeout(initEcharts, 100);
    }
}

// Initialize Charts
async function initEcharts() {
    const trendDom = document.getElementById('trendChart');
    trendChart = echarts.init(trendDom, 'dark', { backgroundColor: 'transparent' });

    const distDom = document.getElementById('distributionChart');
    distributionChart = echarts.init(distDom, 'dark', { backgroundColor: 'transparent' });

    // Fetch analytical data from APIs
    const trendsRes = await apiFetch('/api/v1/statistics/trends?days=7');
    if (trendsRes && trendsRes.code === 200) {
        const optionTrend = {
            tooltip: { trigger: 'axis' },
            legend: { data: ['溺水告警起数', '救援指派完成'], textStyle: { color: '#94a3b8' } },
            xAxis: { type: 'category', data: trendsRes.data.dates, axisLabel: { color: '#94a3b8' } },
            yAxis: { type: 'value', axisLabel: { color: '#94a3b8' } },
            series: [
                { name: '溺水告警起数', type: 'line', data: trendsRes.data.alertCounts, smooth: true, color: '#ef4444' },
                { name: '救援指派完成', type: 'bar', data: trendsRes.data.rescueCounts, color: '#10b981' }
            ]
        };
        trendChart.setOption(optionTrend);
    }

    const distRes = await apiFetch('/api/v1/statistics/water-areas');
    if (distRes && distRes.code === 200) {
        const distribution = distRes.data.distribution;
        const chartData = [];
        for (const [key, value] of Object.entries(distribution)) {
            // Find water area name
            const name = waterAreas.find(w => w.id == key)?.name || `水域 #${key}`;
            chartData.push({ name, value });
        }

        const optionDist = {
            tooltip: { trigger: 'item' },
            series: [{
                name: '报警占比',
                type: 'pie',
                radius: '55%',
                data: chartData.length > 0 ? chartData : [{name: '黄村水库', value: 3}, {name: '南河段', value: 5}],
                emphasis: {
                    itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' }
                }
            }]
        };
        distributionChart.setOption(optionDist);
    }
}

// Bounding Box Draw simulation
function initVideoSimulation() {
    const canvas = document.getElementById('liveCanvas');
    const ctx = canvas.getContext('2d');
    canvas.width = 640;
    canvas.height = 360;

    let count = 0;
    function animate() {
        ctx.fillStyle = '#0f172a';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Draw animated blue river stream
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

        // Draw bridge structure
        ctx.strokeStyle = '#475569';
        ctx.lineWidth = 12;
        ctx.beginPath();
        ctx.moveTo(100, 160);
        ctx.lineTo(540, 160);
        ctx.stroke();

        // Draw a simulated person near the river
        let personX = 260 + Math.sin(count * 0.02) * 40;
        let personY = 170;

        ctx.fillStyle = '#e2e8f0';
        ctx.beginPath();
        ctx.arc(personX, personY - 20, 6, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillRect(personX - 4, personY - 14, 8, 20);

        // Bounding Box AI Tracking simulation
        ctx.strokeStyle = '#ef4444';
        ctx.lineWidth = 2;
        ctx.strokeRect(personX - 15, personY - 30, 30, 45);

        ctx.fillStyle = '#ef4444';
        ctx.font = '10px sans-serif';
        ctx.fillText('人员-靠近危险水域: 96.8%', personX - 15, personY - 35);

        count++;
        requestAnimationFrame(animate);
    }
    animate();
}

// Log logs outputs
function addLinkageLog(text) {
    const container = document.getElementById('linkage-logs-container');
    const log = document.createElement('div');
    const time = new Date().toLocaleTimeString();
    log.innerText = `[${time}] ${text}`;
    container.appendChild(log);
    container.scrollTop = container.scrollHeight;
}

// Simulating interactions
function simulateBroadcast() {
    addLinkageLog("[广播联动] 正在尝试链接附近的音柱系统，下发: '警报，此处水域水深危险，请迅速上岸，切勿游泳捕鱼！'...");
}

async function simulateManualTrigger() {
    addLinkageLog("[AI推理设备] 捕获到了一个可能溺水的行为，正在上报服务器...");

    // Simulate POSTing alert event
    const body = {
        longitude: 120.0304565 + (Math.random() - 0.5) * 0.005,
        latitude: 28.5064125 + (Math.random() - 0.5) * 0.005,
        confidence: 88.50,
        alertType: 'DROWNING',
        status: 'PENDING'
    };

    const res = await apiFetch('/api/v1/alerts/trigger', {
        method: 'POST',
        body: body
    });

    if (res && res.code === 200) {
        addLinkageLog(`[服务器同步] 已成功创建警报事件 ${res.data.eventNo}，大屏弹窗高亮就绪`);
        loadAlertEvents();
        loadKPIs();
    }
}

function simulateDroneTakeoff() {
    addLinkageLog("[系统调度] 已向最近机库 [H01] 发送无人机紧急起飞侦察指令，正在建立视频画面图传回传通道...");
}
