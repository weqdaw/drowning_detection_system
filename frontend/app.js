// Global State
let streams = [];
let models = [];
let activePlayers = {}; // streamId_type -> flvjs.Player
let activeLayout = 'auto'; // auto, 1, 4
let currentTab = 'monitor';
const API_BASE = '/api';
let zlmPorts = { rtsp: 554, rtmp: 1935, http: 88 };
let pollingInterval = null;

// Blueprint Engine State
let blueprintNodes = []; // [{ id, type, x, y, name }]
let blueprintConnections = []; // [{ source, target }]
let panX = 0;
let panY = 0;
let zoom = 1.0;
let isPanning = false;
let startPanX = 0, startPanY = 0;
let startMouseX = 0, startMouseY = 0;

let draggingNode = null;
let offsetNodeX = 0, offsetNodeY = 0;

let drawingConnection = null; // { sourceNodeId, startX, startY }

// DOM Elements
const zlmStatus = document.getElementById('zlm-status');
const gpuStatus = document.getElementById('gpu-status');
const cpuCircle = document.getElementById('cpu-circle');
const ramCircle = document.getElementById('ram-circle');
const gpuCircle = document.getElementById('gpu-circle');
const cpuText = document.getElementById('cpu-text');
const ramText = document.getElementById('ram-text');
const gpuText = document.getElementById('gpu-text');
const gpuName = document.getElementById('gpu-name');
const streamModelSelect = document.getElementById('stream-model');
const confSlider = document.getElementById('stream-conf');
const confVal = document.getElementById('conf-val');
const addStreamForm = document.getElementById('add-stream-form');
const streamList = document.getElementById('stream-list');
const videoGrid = document.getElementById('video-grid');
const dropArea = document.getElementById('drop-area');
const fileInput = document.getElementById('model-file-input');
const uploadStatus = document.getElementById('upload-status');
const uploadFilename = document.getElementById('upload-filename');
const uploadProgress = document.getElementById('upload-progress');
const uploadPercent = document.getElementById('upload-percent');

// The distribution console starts directly; its APIs support anonymous access.
document.addEventListener('DOMContentLoaded', () => {
    if (typeof flvjs === 'undefined') {
        alert("⚠️ 检测到播放器核心库(flv.js)加载失败！\n\n原因：您可能正在使用 HBuilderX 等 IDE 的内置预览浏览器。这些内置浏览器内核过旧，不支持 MediaSource (HTML5 播放器)。\n\n解决办法：请使用 Google Chrome 或 Microsoft Edge 等现代浏览器，直接访问 http://127.0.0.1:8000/ 即可正常观看监控画面。");
    }
    initTabs();
    initClock();
    initEventListeners();
    initEventsSseStream();
    fetchModels();
    fetchAudioModels();
    pollData();
    pollingInterval = setInterval(pollData, 2000);
});

// Tab Routing
function initTabs() {
    const menuItems = document.querySelectorAll('.menu-item');
    const tabContents = document.querySelectorAll('.tab-content');
    const activeTabTitle = document.getElementById('active-tab-title');

    menuItems.forEach(item => {
        item.addEventListener('click', () => {
            const tabName = item.dataset.tab;

            // Switch tabs active classes
            menuItems.forEach(mi => mi.classList.remove('active'));
            tabContents.forEach(tc => tc.classList.remove('active'));

            item.classList.add('active');
            const targetContent = document.getElementById(`tab-${tabName}`);
            if (targetContent) targetContent.classList.add('active');

            currentTab = tabName;

            // Update Title
            let titleText = "监控大屏";
            if (tabName === 'streams') titleText = "直播流管理中心";
            else if (tabName === 'models') {
                titleText = "算法模型管理库";
                fetchAudioModels();
            }
            else if (tabName === 'blueprint') {
                titleText = "可视化工作流蓝图";
                loadBlueprint();
            } else if (tabName === 'events') {
                titleText = "事件与智能分析告警中心";
                fetchEventLogs();
            } else if (tabName === 'logs') {
                titleText = "系统日志审计";
                fetchAuditLogs();
            } else if (tabName === 'api') {
                titleText = "系统接口与开发中心";
                updateSandboxStreamsDropdown();
            }
            activeTabTitle.textContent = titleText;
        });
    });
}

// Clock Display
function initClock() {
    const clockDisplay = document.getElementById('clock-display');
    if (!clockDisplay) return;
    function updateClock() {
        const now = new Date();
        const hrs = String(now.getHours()).padStart(2, '0');
        const mins = String(now.getMinutes()).padStart(2, '0');
        const secs = String(now.getSeconds()).padStart(2, '0');
        clockDisplay.textContent = `${hrs}:${mins}:${secs}`;
    }
    updateClock();
    setInterval(updateClock, 1000);
}

// Event Listeners Setup
function initEventListeners() {
    // Slider Change
    if (confSlider) {
        confSlider.addEventListener('input', (e) => {
            confVal.textContent = e.target.value;
        });
    }

    // Form Submit
    if (addStreamForm) {
        addStreamForm.addEventListener('submit', handleAddStream);
    }

    // Audio ONNX Model Upload Button Listener
    const btnAudioUpload = document.getElementById('btn-upload-audio-model');
    if (btnAudioUpload) {
        btnAudioUpload.addEventListener('click', (e) => {
            e.preventDefault();
            uploadAudioModelFiles();
        });
    }

    // Bind Sandbox Select Change
    const sandboxSelect = document.getElementById('api-sandbox-select');
    if (sandboxSelect) {
        sandboxSelect.addEventListener('change', () => {
            updateSandboxStreamsDropdown();
        });
    }

    // Layout Toggle Buttons
    document.querySelectorAll('.grid-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            document.querySelectorAll('.grid-btn').forEach(b => b.classList.remove('active'));
            const targetBtn = e.target.closest('.grid-btn');
            targetBtn.classList.add('active');
            activeLayout = targetBtn.dataset.grid;
            renderVideoGrid();
        });
    });

    // Drag and Drop Upload Setup
    if (dropArea) {
        ['dragenter', 'dragover'].forEach(eventName => {
            dropArea.addEventListener(eventName, (e) => {
                e.preventDefault();
                dropArea.classList.add('dragover');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            dropArea.addEventListener(eventName, (e) => {
                e.preventDefault();
                dropArea.classList.remove('dragover');
            }, false);
        });

        dropArea.addEventListener('drop', (e) => {
            const dt = e.dataTransfer;
            const files = dt.files;
            if (files.length > 0) {
                handleModelUpload(files[0]);
            }
        });

        dropArea.addEventListener('click', () => {
            fileInput.click();
        });
    }

    if (fileInput) {
        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                handleModelUpload(e.target.files[0]);
            }
        });
    }
}

// Fetch and Poll Data
async function pollData() {
    try {
        await Promise.all([
            fetchSystemStatus(),
            fetchStreams()
        ]);

        // Update statuses on blueprint nodes if in blueprint tab
        if (currentTab === 'blueprint') {
            updateBlueprintNodesStatus();
        }
    } catch (err) {
        console.error("Polling error:", err);
    }
}

// System Status API
async function fetchSystemStatus() {
    try {
        const res = await fetch(`${API_BASE}/system/status`);
        if (!res.ok) throw new Error("HTTP error");
        const status = await res.json();

        // Save ports configuration
        if (status.zlm_rtsp_port) zlmPorts.rtsp = status.zlm_rtsp_port;
        if (status.zlm_rtmp_port) zlmPorts.rtmp = status.zlm_rtmp_port;
        if (status.zlm_http_port) zlmPorts.http = status.zlm_http_port;

        // Update Status indicators
        updateStatusBadge(zlmStatus, status.zlm_online, status.zlm_online ? '在线' : '离线');
        updateStatusBadge(gpuStatus, status.gpu_available, status.gpu_available ? '可用' : '无GPU');

        // Update Progress Rings
        setProgress(cpuCircle, status.cpu_usage);
        cpuText.textContent = `${Math.round(status.cpu_usage)}%`;

        setProgress(ramCircle, status.memory_usage);
        ramText.textContent = `${Math.round(status.memory_usage)}%`;

        if (status.gpu_available) {
            setProgress(gpuCircle, status.gpu_utilization);
            gpuText.textContent = `${Math.round(status.gpu_utilization)}%`;
            gpuName.innerHTML = `<i class="fa-solid fa-microchip"></i> ${status.gpu_name} (${status.gpu_mem_used_gb} / ${status.gpu_mem_total_gb} GB)`;
        } else {
            setProgress(gpuCircle, 0);
            gpuText.textContent = '0%';
            gpuName.textContent = '无可用 NVIDIA GPU 硬件';
        }
    } catch (err) {
        console.error("Failed to fetch system status:", err);
        updateStatusBadge(zlmStatus, false, '连接断开');
        updateStatusBadge(gpuStatus, false, '未知');
    }
}

// Set Progress Ring Offset
function setProgress(circle, percent) {
    if (!circle) return;
    const radius = circle.r.baseVal.value;
    const circumference = radius * 2 * Math.PI;
    const offset = circumference - (percent / 100) * circumference;
    circle.style.strokeDasharray = `${circumference} ${circumference}`;
    circle.style.strokeDashoffset = offset;
}

function updateStatusBadge(element, isOnline, text) {
    if (!element) return;
    if (isOnline) {
        element.className = 'status-badge online';
        element.querySelector('.text').textContent = text;
    } else {
        element.className = 'status-badge offline';
        element.querySelector('.text').textContent = text;
    }
}

// Model Management
async function fetchModels() {
    try {
        const res = await fetch(`${API_BASE}/models`);
        if (!res.ok) throw new Error();
        models = await res.json();

        // Populate select box
        if (streamModelSelect) {
            const currentSelected = streamModelSelect.value;
            streamModelSelect.innerHTML = '';
            models.forEach(m => {
                const opt = document.createElement('option');
                opt.value = m;
                opt.textContent = m;
                streamModelSelect.appendChild(opt);
            });

            if (models.includes(currentSelected)) {
                streamModelSelect.value = currentSelected;
            } else if (models.length > 0) {
                streamModelSelect.value = models[0];
            }
        }

        // Render model cards
        renderModelsListGrid();
    } catch (err) {
        console.error("Failed to fetch models list:", err);
    }
}

// Render Model Center library cards grid
function renderModelsListGrid() {
    const grid = document.getElementById('models-list-grid');
    if (!grid) return;

    if (models.length === 0) {
        grid.innerHTML = `
            <div class="list-empty span-2">
                <i class="fa-solid fa-brain"></i>
                <p>模型库为空，请在上方上传模型</p>
            </div>
        `;
        return;
    }

    grid.innerHTML = '';
    models.forEach(m => {
        const card = document.createElement('div');
        card.className = 'model-card';

        const isDefaultModel = m === 'yolov8n.pt';

        card.innerHTML = `
            <div class="model-card-icon"><i class="fa-solid fa-brain"></i></div>
            <div class="model-card-info" style="flex: 1;">
                <span class="model-card-name" title="${m}">${m}</span>
                <div class="model-card-meta">
                    <span>类型: YOLOv8 目标检测</span>
                    <span>状态: ${isDefaultModel ? '系统内置' : '自定义'}</span>
                </div>
                <div style="margin-top:6px; font-size:10px; font-weight:bold; color:var(--text-light);">可识别类别:</div>
                <div class="model-classes-tags" id="model-classes-${m.replace('.', '_')}" style="display:flex; flex-wrap:wrap; gap:4px; margin-top:4px; font-size:9px;">
                    <span style="color:var(--text-muted); font-style:italic;">加载类别中...</span>
                </div>
            </div>
            <div class="model-card-actions">
                <button class="model-delete-btn"
                        onclick="deleteModel('${m}')"
                        ${isDefaultModel ? 'disabled title="系统默认模型不可删除"' : ''}>
                    <i class="fa-solid fa-trash-can"></i> 删除
                </button>
            </div>
        `;
        grid.appendChild(card);
        // Fetch classes async
        fetchAndRenderModelClasses(m);
    });
}

async function fetchAndRenderModelClasses(modelName) {
    const container = document.getElementById(`model-classes-${modelName.replace('.', '_')}`);
    if (!container) return;
    try {
        const res = await fetch(`${API_BASE}/models/${modelName}/classes`);
        if (!res.ok) throw new Error();
        const data = await res.json();
        const classes = data.classes || [];
        if (classes.length === 0) {
            container.innerHTML = `<span style="color:var(--text-muted); font-size:9px;">无类别信息</span>`;
            return;
        }
        // Limit to first 12 classes to prevent layout overflow, show ... if more
        const displayed = classes.slice(0, 15);
        let html = displayed.map(c => `
            <span style="background:rgba(109, 40, 217, 0.05); color:var(--primary); padding:1px 4px; border-radius:4px; border:1px solid rgba(109, 40, 217, 0.1); font-weight:bold;">${c}</span>
        `).join('');
        if (classes.length > 15) {
            html += `<span style="color:var(--text-muted); font-weight:bold; padding:1px 4px;">+${classes.length - 15}...</span>`;
        }
        container.innerHTML = html;
    } catch (e) {
        container.innerHTML = `<span style="color:var(--text-muted); font-size:9px;">类别加载失败</span>`;
    }
}

// Delete model
async function deleteModel(modelName) {
    if (!confirm(`确定要彻底删除模型文件 ${modelName} 吗？`)) return;
    try {
        const res = await fetch(`${API_BASE}/models/${modelName}`, { method: 'DELETE' });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.detail || "删除失败");
        }
        await fetchModels();
        alert(`模型 ${modelName} 已成功删除。`);
    } catch (e) {
        alert(`删除失败: ${e.message}`);
    }
}

/******************************************************************************
 * AUDIO ONNX MODEL & CSV CLASS MAPPING MANAGEMENT
 ******************************************************************************/
let audioModelsList = [];
const defaultPresetAudioClasses = [
    "Speech", "Scream", "Shout", "Yell", "Crying", "Groan",
    "Splash", "Water", "Swimming", "Whistle", "Alarm"
];

async function fetchAudioModels() {
    try {
        const res = await fetch(`${API_BASE}/audio/models`);
        if (!res.ok) throw new Error();
        audioModelsList = await res.json();
        renderAudioModelsGrid();
        populateRuleAudioModelSelect();
    } catch (e) {
        console.error("Failed to fetch audio ONNX models list:", e);
    }
}

function renderAudioModelsGrid() {
    const grid = document.getElementById('audio-models-list-grid');
    if (!grid) return;

    if (audioModelsList.length === 0) {
        grid.innerHTML = `
            <div class="list-empty span-2" style="padding: 16px; font-size: 11px;">
                <i class="fa-solid fa-volume-xmark" style="font-size: 20px;"></i>
                <p>暂无自定义音频 ONNX 模型，目前使用系统默认模型 (YAMNet_Audio.onnx)</p>
            </div>
        `;
        return;
    }

    grid.innerHTML = '';
    audioModelsList.forEach(m => {
        const card = document.createElement('div');
        card.className = 'model-card';
        card.style.borderColor = 'rgba(124, 58, 237, 0.3)';

        const tagBadges = (m.display_names || []).map(name =>
            `<span style="background: rgba(124, 58, 237, 0.1); color: #7c3aed; padding: 2px 6px; border-radius: 4px; font-size: 9px; font-weight: 600;">${name}</span>`
        ).join(' ');

        card.innerHTML = `
            <div class="model-card-icon" style="background: rgba(124, 58, 237, 0.1); color: #7c3aed;"><i class="fa-solid fa-file-audio"></i></div>
            <div class="model-card-info" style="flex: 1;">
                <span class="model-card-name" title="${m.model_name}">${m.model_name}</span>
                <div class="model-card-meta">
                    <span>关联 CSV: ${m.csv_name || '未找到配套 CSV'}</span>
                    <span>解析标签数: ${m.display_names ? m.display_names.length : 0} 项</span>
                </div>
                <div style="margin-top:6px; font-size:10px; font-weight:bold; color:var(--text-light);">已加载 display_name 类别:</div>
                <div style="display:flex; flex-wrap:wrap; gap:4px; margin-top:4px; max-height:60px; overflow-y:auto;">
                    ${tagBadges || '<span style="color:var(--text-muted); font-size:9px;">暂无解析出的 display_name</span>'}
                </div>
            </div>
            <div class="model-card-actions">
                <button class="model-delete-btn" onclick="deleteAudioModel('${m.model_name}')" title="删除音频模型">
                    <i class="fa-solid fa-trash-can"></i>
                </button>
            </div>
        `;
        grid.appendChild(card);
    });
}

function updateFileName(input, labelId) {
    const label = document.getElementById(labelId);
    if (label && input.files && input.files[0]) {
        label.textContent = input.files[0].name;
        label.style.color = '#7c3aed';
        label.style.fontWeight = 'bold';
    }
}
window.updateFileName = updateFileName;

async function uploadAudioModelFiles() {
    const onnxInput = document.getElementById('audio-onnx-file-input');
    const csvInput = document.getElementById('audio-csv-file-input');

    if (!onnxInput || !onnxInput.files[0]) {
        alert("请先选择 .onnx 格式的音频模型文件");
        return;
    }
    if (!csvInput || !csvInput.files[0]) {
        alert("请先选择包含 (index,mid,display_name) 列的 .csv 标签文件");
        return;
    }

    const formData = new FormData();
    formData.append("onnx_file", onnxInput.files[0]);
    formData.append("csv_file", csvInput.files[0]);

    try {
        const res = await fetch(`${API_BASE}/audio/upload-model`, {
            method: 'POST',
            body: formData
        });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.detail || "上传失败");
        }
        const data = await res.json();
        alert(`音频模型 [${data.model_name}] 与 CSV 标签 (${data.total_classes} 项) 上传并解析成功！`);
        onnxInput.value = '';
        csvInput.value = '';
        await fetchAudioModels();
    } catch (e) {
        alert(`上传音频模型失败: ${e.message}`);
    }
}

async function deleteAudioModel(modelName) {
    if (!confirm(`确定要删除音频模型 ${modelName} 及其 CSV 标签文件吗？`)) return;
    try {
        const res = await fetch(`${API_BASE}/audio/models/${modelName}`, { method: 'DELETE' });
        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.detail || "删除失败");
        }
        await fetchAudioModels();
        alert(`音频模型 ${modelName} 已删除。`);
    } catch (e) {
        alert(`删除失败: ${e.message}`);
    }
}

function populateRuleAudioModelSelect() {
    const select = document.getElementById('rule-audio-model');
    if (!select) return;

    const currentVal = select.value;
    select.innerHTML = `<option value="">-- 系统默认音频模型 (YAMNet_Audio.onnx) --</option>`;

    audioModelsList.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.model_name;
        opt.textContent = `${m.model_name} ${m.has_csv ? `(已包含 ${m.display_names.length} 项 CSV 标签)` : ''}`;
        select.appendChild(opt);
    });

    if (currentVal && audioModelsList.some(m => m.model_name === currentVal)) {
        select.value = currentVal;
    }
    onRuleAudioModelSelectChange(select.value);
}

function onRuleAudioModelSelectChange(modelName) {
    const box = document.getElementById('rule-audio-classes-box');
    if (!box) return;

    let displayNames = defaultPresetAudioClasses;
    const found = audioModelsList.find(m => m.model_name === modelName);
    if (found && found.display_names && found.display_names.length > 0) {
        displayNames = found.display_names;
    }

    box.innerHTML = '';
    displayNames.forEach(clsName => {
        const label = document.createElement('label');
        label.style.cssText = "font-size: 11px; background: #ffffff; padding: 2px 6px; border-radius: 4px; border: 1px solid var(--border-light); cursor: pointer; display: inline-flex; align-items: center; gap: 4px;";
        label.innerHTML = `
            <input type="checkbox" value="${clsName}">
            <span>${clsName}</span>
        `;
        box.appendChild(label);
    });
}

function selectAllRuleAudioClasses() {
    const box = document.getElementById('rule-audio-classes-box');
    if (!box) return;
    box.querySelectorAll('input[type="checkbox"]').forEach(cb => cb.checked = true);
}

function clearRuleAudioClasses() {
    const box = document.getElementById('rule-audio-classes-box');
    if (!box) return;
    box.querySelectorAll('input[type="checkbox"]').forEach(cb => cb.checked = false);
}

// Model Upload with Progress Bar
function handleModelUpload(file) {
    if (!file.name.endsWith('.pt')) {
        alert("只能上传 PyTorch 权重文件 (.pt)");
        return;
    }

    uploadFilename.textContent = file.name;
    uploadStatus.style.display = 'flex';
    uploadProgress.style.width = '0%';
    uploadPercent.textContent = '0%';

    const xhr = new XMLHttpRequest();
    const formData = new FormData();
    formData.append("file", file);

    // Track upload progress
    xhr.upload.addEventListener("progress", (e) => {
        if (e.lengthComputable) {
            const percent = Math.round((e.loaded / e.total) * 100);
            uploadProgress.style.width = `${percent}%`;
            uploadPercent.textContent = `${percent}%`;
        }
    });

    xhr.addEventListener("load", () => {
        if (xhr.status === 200) {
            setTimeout(() => {
                uploadStatus.style.display = 'none';
                fetchModels();
                alert(`模型 ${file.name} 上传成功！`);
            }, 500);
        } else {
            const err = JSON.parse(xhr.responseText || '{}');
            alert(`上传失败: ${err.detail || '未知错误'}`);
            uploadStatus.style.display = 'none';
        }
    });

    xhr.addEventListener("error", () => {
        alert("上传网络错误");
        uploadStatus.style.display = 'none';
    });

    xhr.open("POST", `${API_BASE}/models/upload`);

    xhr.send(formData);
}

// Streams API Actions
async function fetchStreams() {
    try {
        const res = await fetch(`${API_BASE}/streams`);
        if (!res.ok) throw new Error();
        const data = await res.json();

        // Detect differences to trigger layout updates
        const activeKeys = [];
        data.filter(s => s.status === 'Running').forEach(s => {
            activeKeys.push(`${s.id}_original`);
            activeKeys.push(`${s.id}_yolo`);
        });
        const activeKeysStr = activeKeys.sort().join(',');
        const currentActiveKeysStr = Object.keys(activePlayers).sort().join(',');

        streams = data;

        // Render Stream list
        renderStreamList();

        if (activeKeysStr !== currentActiveKeysStr) {
            renderVideoGrid();
        } else {
            // Update FPS stats dynamically
            streams.forEach(s => {
                const originalStatusEl = document.getElementById(`overlay-status-${s.id}_original`);
                const yoloStatusEl = document.getElementById(`overlay-status-${s.id}_yolo`);
                const yoloFpsEl = document.getElementById(`overlay-fps-${s.id}_yolo`);

                if (yoloFpsEl) yoloFpsEl.textContent = `${s.inference_fps} FPS`;

                const statusText = s.status === 'Running' ? '运行中' : s.status === 'Reconnecting' ? '重连中' : '错误';

                if (originalStatusEl) {
                    originalStatusEl.textContent = statusText;
                    originalStatusEl.previousElementSibling.className = `video-card-status-dot ${s.status}`;
                }
                if (yoloStatusEl) {
                    yoloStatusEl.textContent = statusText;
                    yoloStatusEl.previousElementSibling.className = `video-card-status-dot ${s.status}`;
                }
            });
        }
        renderRealtimeAnalysisGrid();
    } catch (err) {
        console.error("Failed to fetch streams list:", err);
    }
}

async function handleAddStream(e) {
    e.preventDefault();
    const id = document.getElementById('stream-id').value.trim();
    const name = document.getElementById('stream-name').value.trim();
    const inputUrl = document.getElementById('stream-url').value.trim();
    const model = streamModelSelect.value;
    const confidence = parseFloat(confSlider.value);
    const device = document.getElementById('stream-device').value;

    try {
        // Create Stream
        let res = await fetch(`${API_BASE}/streams`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id, name, input_url: inputUrl, selected_model: model, confidence, device })
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.detail || "创建失败");
        }

        // Auto Start Stream
        res = await fetch(`${API_BASE}/streams/${id}/start`, { method: 'POST' });
        if (!res.ok) throw new Error("启动分析失败");

        addStreamForm.reset();
        confVal.textContent = '0.25';
        confSlider.value = 0.25;

        await fetchStreams();
        alert(`监控通道 [${name}] 已成功接入并启动分析。`);
    } catch (err) {
        alert(err.message);
    }
}

async function startStream(id) {
    try {
        const res = await fetch(`${API_BASE}/streams/${id}/start`, { method: 'POST' });
        if (!res.ok) throw new Error("启动分析失败");
        await fetchStreams();
    } catch (err) {
        alert(err.message);
    }
}

async function stopStream(id) {
    try {
        const res = await fetch(`${API_BASE}/streams/${id}/stop`, { method: 'POST' });
        if (!res.ok) throw new Error("停止失败");
        await fetchStreams();
    } catch (err) {
        alert(err.message);
    }
}

async function deleteStream(id) {
    if (!confirm(`确定要删除通道 ${id} 吗？`)) return;
    try {
        const res = await fetch(`${API_BASE}/streams/${id}`, { method: 'DELETE' });
        if (!res.ok) throw new Error("删除失败");
        await fetchStreams();
    } catch (err) {
        alert(err.message);
    }
}

// Render Streams Controls UI
function renderStreamList() {
    if (!streamList) return;
    if (streams.length === 0) {
        streamList.innerHTML = `
            <div class="list-empty">
                <i class="fa-solid fa-video-slash"></i>
                <p>暂无监控流，请在左侧添加</p>
            </div>
        `;
        return;
    }

    streamList.innerHTML = '';
    streams.forEach(s => {
        const item = document.createElement('div');
        item.className = 'stream-item';

        const isRunning = s.status === 'Running' || s.status === 'Reconnecting';

        item.innerHTML = `
            <div class="stream-item-info">
                <div class="stream-item-header">
                    <span class="stream-item-status-dot ${s.status}"></span>
                    <span class="stream-item-name" title="${s.name}">${s.name}</span>
                    <span class="stream-item-id">${s.id}</span>
                </div>
                <div class="stream-item-url" title="${s.input_url}">${s.input_url}</div>
                <div class="stream-item-meta">
                    <span><i class="fa-solid fa-brain"></i> ${s.selected_model}</span>
                    <span><i class="fa-solid fa-crosshairs"></i> Conf: ${s.confidence}</span>
                    <span class="device-badge" style="background: ${s.device === 'cuda' ? 'rgba(109, 40, 217, 0.1)' : 'rgba(71, 85, 105, 0.1)'}; color: ${s.device === 'cuda' ? 'var(--primary)' : 'var(--text-muted)'}; padding: 1px 4px; border-radius: 4px; font-weight: bold; font-size: 9px;"><i class="fa-solid fa-microchip"></i> ${s.device === 'cuda' ? 'GPU' : 'CPU'}</span>
                </div>
            </div>
            <div class="stream-item-actions">
                <button class="action-btn rules-btn" onclick="openRulesModal('${s.id}', '${s.name}')" title="规则防区配置" style="color: var(--primary); border-color: rgba(109, 40, 217, 0.2); background: rgba(109, 40, 217, 0.05);"><i class="fa-solid fa-gears"></i></button>
                ${isRunning ?
                `<button class="action-btn share-btn" onclick="openShareModal('${s.id}', '${s.name}')" title="查看分享流地址" style="color: var(--accent); border-color: rgba(0, 245, 255, 0.2); background: rgba(0, 245, 255, 0.05);"><i class="fa-solid fa-share-nodes"></i></button>
                     <button class="action-btn stop-btn" onclick="stopStream('${s.id}')" title="停止分析"><i class="fa-solid fa-stop"></i></button>` :
                `<button class="action-btn start-btn" onclick="startStream('${s.id}')" title="启动分析"><i class="fa-solid fa-play"></i></button>`
            }
                <button class="action-btn delete-btn" onclick="deleteStream('${s.id}')" title="删除通道"><i class="fa-solid fa-trash-can"></i></button>
            </div>
        `;
        streamList.appendChild(item);
    });
}

// Render Video Monitors Grid
function renderVideoGrid() {
    if (!videoGrid) return;
    const runningStreams = streams.filter(s => s.status === 'Running' || s.status === 'Reconnecting' || s.status === 'Error');

    const activeKeys = [];
    runningStreams.forEach(s => {
        if (s.status === 'Running') {
            activeKeys.push(`${s.id}_original`);
            activeKeys.push(`${s.id}_yolo`);
        }
    });

    Object.keys(activePlayers).forEach(key => {
        if (!activeKeys.includes(key)) {
            destroyPlayer(key);
        }
    });

    if (runningStreams.length === 0) {
        videoGrid.className = "video-grid auto-layout";
        videoGrid.dataset.count = "0";
        videoGrid.innerHTML = `
            <div class="grid-empty-state">
                <div class="empty-glow"></div>
                <i class="fa-solid fa-circle-nodes empty-icon"></i>
                <h3>大屏空闲中</h3>
                <p>请在左侧列表启动通道进行视觉模型检测</p>
            </div>
        `;
        return;
    }

    videoGrid.innerHTML = '';
    const totalCardsCount = runningStreams.length * 2;
    if (activeLayout === '1') {
        videoGrid.className = 'video-grid layout-1';
    } else if (activeLayout === '4') {
        videoGrid.className = 'video-grid layout-4';
    } else {
        videoGrid.className = 'video-grid auto-layout';
        videoGrid.dataset.count = totalCardsCount.toString();
    }

    runningStreams.forEach(s => {
        // Original Stream Card
        const originalCard = document.createElement('div');
        originalCard.className = 'video-card';
        originalCard.id = `video-card-${s.id}_original`;

        originalCard.innerHTML = `
            <div class="video-container" id="container-${s.id}_original">
                <video id="player-${s.id}_original" muted autoplay playsinline></video>
                <div class="video-overlay-top">
                    <div class="video-title-badge">
                        <span class="video-card-status-dot ${s.status}"></span>
                        <span class="stream-title">${s.name} (原始画面)</span>
                    </div>
                    <div class="video-meta-badge">
                        <span>Original Stream</span>
                        <span id="overlay-status-${s.id}_original">${s.status === 'Running' ? '运行中' : s.status === 'Reconnecting' ? '重连中' : '错误'}</span>
                    </div>
                </div>

                ${s.status === 'Reconnecting' ? `
                    <div class="video-overlay-center loading" id="center-status-${s.id}_original">
                        <i class="fa-solid fa-spinner"></i>
                        <p>视频重连中...</p>
                    </div>
                ` : s.status === 'Error' ? `
                    <div class="video-overlay-center error" id="center-status-${s.id}_original">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <p>流读取错误</p>
                        <small style="font-size:10px; opacity:0.7; max-width: 80%;">${s.error_msg}</small>
                    </div>
                ` : `
                    <div class="video-overlay-center loading" id="center-status-${s.id}_original">
                        <i class="fa-solid fa-circle-play"></i>
                        <p>加载原始画面中...</p>
                    </div>
                `}
            </div>
        `;

        // Processed Stream Card
        const yoloCard = document.createElement('div');
        yoloCard.className = 'video-card';
        yoloCard.id = `video-card-${s.id}_yolo`;

        yoloCard.innerHTML = `
            <div class="video-container" id="container-${s.id}_yolo">
                <video id="player-${s.id}_yolo" muted autoplay playsinline></video>
                <div class="video-overlay-top">
                    <div class="video-title-badge">
                        <span class="video-card-status-dot ${s.status}"></span>
                        <span class="stream-title">${s.name} (智能分析)</span>
                    </div>
                    <div class="video-meta-badge">
                        <span>Model: <span class="badge-highlight">${s.selected_model}</span></span>
                        <span id="overlay-fps-${s.id}_yolo">${s.inference_fps} FPS</span>
                        <span id="overlay-status-${s.id}_yolo">${s.status === 'Running' ? '运行中' : s.status === 'Reconnecting' ? '重连中' : '错误'}</span>
                    </div>
                </div>

                ${s.status === 'Reconnecting' ? `
                    <div class="video-overlay-center loading" id="center-status-${s.id}_yolo">
                        <i class="fa-solid fa-spinner"></i>
                        <p>视频重连中...</p>
                    </div>
                ` : s.status === 'Error' ? `
                    <div class="video-overlay-center error" id="center-status-${s.id}_yolo">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <p>流读取错误</p>
                        <small style="font-size:10px; opacity:0.7; max-width: 80%;">${s.error_msg}</small>
                    </div>
                ` : `
                    <div class="video-overlay-center loading" id="center-status-${s.id}_yolo">
                        <i class="fa-solid fa-circle-play"></i>
                        <p>加载智能画面中...</p>
                    </div>
                `}
            </div>
        `;

        videoGrid.appendChild(originalCard);
        videoGrid.appendChild(yoloCard);

        if (s.status === 'Running') {
            const originalPlayUrl = s.play_url.replace('_yolo.live.flv', '.live.flv');
            const yoloPlayUrl = s.play_url;

            setTimeout(() => {
                initPlayer(`${s.id}_original`, originalPlayUrl);
                initPlayer(`${s.id}_yolo`, yoloPlayUrl);
            }, 100);
        }
    });
}

// Bilibili FLV.js Integration
function initPlayer(id, playUrl) {
    if (activePlayers[id]) return;

    const videoElement = document.getElementById(`player-${id}`);
    const statusOverlay = document.getElementById(`center-status-${id}`);

    if (!videoElement) return;

    if (!flvjs.isSupported()) {
        if (statusOverlay) {
            statusOverlay.className = "video-overlay-center error";
            statusOverlay.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i><p>浏览器不支持 FLV.js 播放器</p>`;
        }
        return;
    }

    let mappedUrl = playUrl;
    try {
        const u = new URL(playUrl);
        u.hostname = window.location.hostname;
        mappedUrl = u.toString();
    } catch (e) {
        console.error("Failed to parse play url", playUrl);
    }

    try {
        const player = flvjs.createPlayer({
            type: 'flv',
            url: mappedUrl,
            isLive: true,
            hasAudio: false
        }, {
            enableWorker: false,
            enableStashBuffer: false,
            stashInitialSize: 0,
            lazyLoad: false,
            seekType: 'range'
        });

        player.attachMediaElement(videoElement);
        player.load();

        videoElement.addEventListener('timeupdate', () => {
            if (player.buffered && player.buffered.length > 0) {
                const end = player.buffered.end(player.buffered.length - 1);
                const diff = end - videoElement.currentTime;
                // Keep live stream buffer under 0.6s for real-time smooth playback
                if (diff > 0.8) {
                    videoElement.currentTime = end - 0.1;
                }
            }
        });

        player.on(flvjs.Events.ERROR, (errorType, errorDetail, errorInfo) => {
            console.error(`[Player-${id}] FLV error:`, errorType, errorDetail, errorInfo);
            if (statusOverlay) {
                statusOverlay.style.display = 'flex';
                statusOverlay.className = "video-overlay-center error";
                statusOverlay.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i><p>流加载失败，等待服务器推流...</p>`;
            }
            destroyPlayer(id);
        });

        player.on('script_parsed', () => {
            if (statusOverlay) statusOverlay.style.display = 'none';
        });

        videoElement.onplay = () => {
            if (statusOverlay) statusOverlay.style.display = 'none';
        };

        player.play();
        activePlayers[id] = player;
    } catch (e) {
        console.error(`[Player-${id}] Setup failed:`, e);
        if (statusOverlay) {
            statusOverlay.className = "video-overlay-center error";
            statusOverlay.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i><p>播放器加载失败</p>`;
        }
    }
}

function destroyPlayer(id) {
    if (activePlayers[id]) {
        try {
            activePlayers[id].pause();
            activePlayers[id].unload();
            activePlayers[id].detachMediaElement();
            activePlayers[id].destroy();
        } catch (e) {
            console.error("Player cleanup error:", e);
        }
        delete activePlayers[id];
    }
}


/******************************************************************************
 * BLUEPRINT NODE WRITER ENGINE (DIFY STYLED)
 ******************************************************************************/

const canvasContainer = document.getElementById('blueprint-canvas-container');
const canvas = document.getElementById('blueprint-canvas');
const svgOverlay = document.getElementById('blueprint-svg');

function initBlueprintCanvas() {
    if (!canvasContainer || !canvas || !svgOverlay) return;

    // Mouse Panning
    canvasContainer.addEventListener('mousedown', (e) => {
        if (e.target === canvasContainer || e.target === canvas || e.target.tagName.toLowerCase() === 'svg') {
            isPanning = true;
            canvasContainer.style.cursor = 'grabbing';
            startMouseX = e.clientX;
            startMouseY = e.clientY;
            startPanX = panX;
            startPanY = panY;
            e.preventDefault();
        }
    });

    // Action Buttons bindings
    const btnSave = document.getElementById('btn-blueprint-save');
    const btnClear = document.getElementById('btn-blueprint-clear');
    const btnAuto = document.getElementById('btn-blueprint-auto');

    if (btnSave && !btnSave.dataset.bound) {
        btnSave.addEventListener('click', saveBlueprint);
        btnSave.dataset.bound = "true";
    }
    if (btnClear && !btnClear.dataset.bound) {
        btnClear.addEventListener('click', clearBlueprint);
        btnClear.dataset.bound = "true";
    }
    if (btnAuto && !btnAuto.dataset.bound) {
        btnAuto.addEventListener('click', runBlueprintAutoLayout);
        btnAuto.dataset.bound = "true";
    }
}

// Window mousemove / mouseup handlers
window.addEventListener('mousemove', (e) => {
    if (isPanning) {
        const dx = e.clientX - startMouseX;
        const dy = e.clientY - startMouseY;
        panX = startPanX + dx;
        panY = startPanY + dy;
        updateCanvasTransform();
    } else if (draggingNode) {
        const rect = canvas.getBoundingClientRect();
        const x = (e.clientX - rect.left - offsetNodeX) / zoom;
        const y = (e.clientY - rect.top - offsetNodeY) / zoom;

        const nodeEl = document.getElementById(draggingNode);
        if (nodeEl) {
            const boundedX = Math.max(0, Math.min(2800, x));
            const boundedY = Math.max(0, Math.min(2800, y));

            nodeEl.style.left = `${boundedX}px`;
            nodeEl.style.top = `${boundedY}px`;
            nodeEl.dataset.x = boundedX;
            nodeEl.dataset.y = boundedY;

            const nodeState = blueprintNodes.find(n => n.id === draggingNode);
            if (nodeState) {
                nodeState.x = boundedX;
                nodeState.y = boundedY;
            }

            drawConnections();
        }
    } else if (drawingConnection) {
        updateTempWire(e.clientX, e.clientY);
    }
});

window.addEventListener('mouseup', (e) => {
    if (isPanning) {
        isPanning = false;
        if (canvasContainer) canvasContainer.style.cursor = 'grab';
    }

    if (draggingNode) {
        draggingNode = null;
    }

    if (drawingConnection) {
        const targetPort = document.elementFromPoint(e.clientX, e.clientY);
        if (targetPort && targetPort.classList.contains('port-input')) {
            const targetNodeId = targetPort.dataset.nodeId;
            const sourceNodeId = drawingConnection.sourceNodeId;

            const sourceNode = blueprintNodes.find(n => n.id === sourceNodeId);
            const targetNode = blueprintNodes.find(n => n.id === targetNodeId);

            if (sourceNode && targetNode && sourceNode.type === 'stream' && targetNode.type === 'model') {
                blueprintConnections = blueprintConnections.filter(c => c.source !== sourceNodeId);
                blueprintConnections.push({
                    source: sourceNodeId,
                    target: targetNodeId
                });
            }
        }

        drawingConnection = null;
        const tempWire = document.getElementById('temp-wire');
        if (tempWire) tempWire.remove();

        drawConnections();
    }
});

function updateCanvasTransform() {
    if (canvas) {
        canvas.style.transform = `translate(${panX}px, ${panY}px) scale(${zoom})`;
    }
}

function renderBlueprintSidebarStreams() {
    const list = document.getElementById('blueprint-streams-list');
    if (!list) return;

    list.innerHTML = '';
    streams.forEach(s => {
        const item = document.createElement('div');
        item.className = 'blueprint-node-tpl stream-node-tpl';
        item.innerHTML = `<i class="fa-solid fa-video"></i> <span>${s.name} (${s.id})</span>`;
        item.addEventListener('click', () => {
            addNodeToCanvas('stream', s.id, s.name);
        });
        list.appendChild(item);
    });
}

function renderBlueprintSidebarModels() {
    const list = document.getElementById('blueprint-models-list');
    if (!list) return;

    list.innerHTML = '';
    models.forEach(m => {
        const item = document.createElement('div');
        item.className = 'blueprint-node-tpl model-node-tpl';
        item.innerHTML = `<i class="fa-solid fa-brain"></i> <span>${m}</span>`;
        item.addEventListener('click', () => {
            addNodeToCanvas('model', m, m);
        });
        list.appendChild(item);
    });
}

function addNodeToCanvas(type, internalId, displayName, x = null, y = null) {
    const nodeId = `${type}_${internalId}`;

    if (blueprintNodes.some(n => n.id === nodeId)) {
        alert("该节点已经在画布上了！");
        return;
    }

    if (x === null || y === null) {
        const rect = canvasContainer.getBoundingClientRect();
        x = Math.max(50, (-panX + rect.width / 2 - 100) / zoom);
        y = Math.max(50, (-panY + rect.height / 2 - 40) / zoom);
    }

    const streamRec = type === 'stream' ? streams.find(s => s.id === internalId) : null;
    const device = streamRec ? (streamRec.device || 'cuda') : 'cuda';

    const nodeData = {
        id: nodeId,
        type: type,
        x: x,
        y: y,
        name: displayName,
        device: type === 'stream' ? device : null
    };

    blueprintNodes.push(nodeData);
    renderNodeHTML(nodeData);
}

function renderNodeHTML(node) {
    if (!canvas) return;
    const nodeEl = document.createElement('div');
    nodeEl.className = `canvas-node ${node.type}-node`;
    nodeEl.id = node.id;
    nodeEl.style.left = `${node.x}px`;
    nodeEl.style.top = `${node.y}px`;
    nodeEl.dataset.x = node.x;
    nodeEl.dataset.y = node.y;

    if (node.type === 'stream') {
        const streamDetails = streams.find(s => s.id === node.id.replace('stream_', '')) || {};
        const status = streamDetails.status || 'Stopped';
        const fps = streamDetails.inference_fps || 0;

        nodeEl.innerHTML = `
            <div class="canvas-node-header"><i class="fa-solid fa-video"></i> 直播视频流</div>
            <div class="canvas-node-body">
                <div class="node-title">${node.name}</div>
                <div class="node-meta">ID: ${node.id.replace('stream_', '')}</div>
                <div class="node-input-row" style="margin-top: 6px; display: flex; align-items: center; justify-content: space-between; font-size: 10px; color: var(--text-light);">
                    <span>运行设备:</span>
                    <select class="node-device-select" style="background: var(--bg-input); border: 1px solid var(--border-light); border-radius: 4px; color: var(--text-main); font-size: 9px; padding: 2px 4px; outline: none; cursor: pointer;" data-node-id="${node.id}">
                        <option value="cuda" ${node.device === 'cuda' ? 'selected' : ''}>GPU (CUDA)</option>
                        <option value="cpu" ${node.device === 'cpu' ? 'selected' : ''}>CPU</option>
                    </select>
                </div>
                <div class="node-status" style="margin-top: 6px;">
                    <span class="node-status-dot ${status}" id="node-status-dot-${node.id}"></span>
                    <span id="node-status-text-${node.id}">${status === 'Running' ? '运行中' : status === 'Reconnecting' ? '重连中' : '离线'}</span>
                    <span style="margin-left: auto;" id="node-fps-${node.id}">${fps} FPS</span>
                </div>
            </div>
            <div class="node-port port-output" data-node-id="${node.id}"></div>
        `;
    } else {
        nodeEl.innerHTML = `
            <div class="canvas-node-header"><i class="fa-solid fa-brain"></i> YOLO 检测模型</div>
            <div class="canvas-node-body">
                <div class="node-title" title="${node.name}">${node.name}</div>
                <div class="node-meta">权重文件 (.pt)</div>
            </div>
            <div class="node-port port-input" data-node-id="${node.id}"></div>
        `;
    }

    canvas.appendChild(nodeEl);

    const header = nodeEl.querySelector('.canvas-node-header');
    header.addEventListener('mousedown', (e) => {
        draggingNode = node.id;
        const rect = nodeEl.getBoundingClientRect();
        offsetNodeX = e.clientX - rect.left;
        offsetNodeY = e.clientY - rect.top;
        e.stopPropagation();
    });

    const outputPort = nodeEl.querySelector('.port-output');
    if (outputPort) {
        outputPort.addEventListener('mousedown', (e) => {
            const coords = getPortCoords(node.id, true);
            drawingConnection = {
                sourceNodeId: node.id,
                startX: coords.x,
                startY: coords.y
            };
            createTempWire(coords.x, coords.y);
            e.stopPropagation();
            e.preventDefault();
        });
    }

    nodeEl.addEventListener('dblclick', (e) => {
        if (e.target.tagName === 'INPUT' || e.target.classList.contains('node-port') || e.target.tagName === 'SELECT') return;
        removeNodeFromCanvas(node.id);
    });

    const deviceSelect = nodeEl.querySelector('.node-device-select');
    if (deviceSelect) {
        deviceSelect.addEventListener('change', (e) => {
            const val = e.target.value;
            const targetNode = blueprintNodes.find(n => n.id === node.id);
            if (targetNode) {
                targetNode.device = val;
                console.log(`Node device updated: ${node.id} -> ${val}`);
            }
        });
    }
}

function removeNodeFromCanvas(nodeId) {
    blueprintNodes = blueprintNodes.filter(n => n.id !== nodeId);
    blueprintConnections = blueprintConnections.filter(c => c.source !== nodeId && c.target !== nodeId);

    const el = document.getElementById(nodeId);
    if (el) el.remove();

    drawConnections();
}

function getPortCoords(nodeId, isOutput) {
    const nodeEl = document.getElementById(nodeId);
    if (!nodeEl) return { x: 0, y: 0 };

    const x = parseFloat(nodeEl.dataset.x);
    const y = parseFloat(nodeEl.dataset.y);
    const width = nodeEl.offsetWidth || 200;
    const height = nodeEl.offsetHeight || 90;

    if (isOutput) {
        return { x: x + width, y: y + height / 2 };
    } else {
        return { x: x, y: y + height / 2 };
    }
}

function getBezierPath(x1, y1, x2, y2) {
    const dx = Math.abs(x2 - x1) * 0.5;
    const cp1x = x1 + dx;
    const cp1y = y1;
    const cp2x = x2 - dx;
    const cp2y = y2;
    return `M ${x1} ${y1} C ${cp1x} ${cp1y}, ${cp2x} ${cp2y}, ${x2} ${y2}`;
}

function createTempWire(startX, startY) {
    const old = document.getElementById('temp-wire');
    if (old) old.remove();

    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('id', 'temp-wire');
    path.setAttribute('class', 'blueprint-temp-wire');
    path.setAttribute('d', getBezierPath(startX, startY, startX, startY));
    if (svgOverlay) svgOverlay.appendChild(path);
}

function updateTempWire(clientX, clientY) {
    const tempWire = document.getElementById('temp-wire');
    if (!tempWire || !drawingConnection) return;

    const rect = canvas.getBoundingClientRect();
    const endX = (clientX - rect.left) / zoom;
    const endY = (clientY - rect.top) / zoom;

    tempWire.setAttribute('d', getBezierPath(drawingConnection.startX, drawingConnection.startY, endX, endY));
}

function drawConnections() {
    if (!svgOverlay) return;
    const paths = svgOverlay.querySelectorAll('path:not(.blueprint-temp-wire)');
    paths.forEach(p => p.remove());

    blueprintConnections.forEach((conn, index) => {
        const sourceCoords = getPortCoords(conn.source, true);
        const targetCoords = getPortCoords(conn.target, false);

        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('class', 'blueprint-connection-line active');
        path.setAttribute('d', getBezierPath(sourceCoords.x, sourceCoords.y, targetCoords.x, targetCoords.y));
        path.setAttribute('style', 'cursor: pointer; pointer-events: stroke;');
        path.setAttribute('marker-end', 'url(#arrow)');

        path.addEventListener('mouseenter', () => {
            path.setAttribute('stroke', 'var(--accent)');
            path.style.strokeWidth = "5";
        });
        path.addEventListener('mouseleave', () => {
            path.setAttribute('stroke', 'var(--primary)');
            path.style.strokeWidth = "3.5";
        });

        path.addEventListener('dblclick', (e) => {
            e.stopPropagation();
            if (confirm("确定要删除这条连接线吗？")) {
                blueprintConnections.splice(index, 1);
                drawConnections();
            }
        });

        svgOverlay.appendChild(path);
    });
}

function updateBlueprintNodesStatus() {
    blueprintNodes.forEach(node => {
        if (node.type !== 'stream') return;
        const streamId = node.id.replace('stream_', '');
        const streamDetails = streams.find(s => s.id === streamId);
        if (!streamDetails) return;

        const status = streamDetails.status || 'Stopped';
        const fps = streamDetails.inference_fps || 0;

        const dot = document.getElementById(`node-status-dot-${node.id}`);
        const text = document.getElementById(`node-status-text-${node.id}`);
        const fpsEl = document.getElementById(`node-fps-${node.id}`);

        if (dot) dot.className = `node-status-dot ${status}`;
        if (text) text.textContent = status === 'Running' ? '运行中' : status === 'Reconnecting' ? '重连中' : '离线';
        if (fpsEl) fpsEl.textContent = `${fps} FPS`;
    });
}

async function saveBlueprint() {
    try {
        const payload = {
            nodes: blueprintNodes,
            connections: blueprintConnections
        };

        const res = await fetch(`${API_BASE}/blueprint`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.detail || "应用失败");
        }

        await fetchStreams();
        alert("🚀 蓝图配置保存成功，模型加载并部署完成！");
    } catch (e) {
        alert(`部署失败: ${e.message}`);
    }
}

async function loadBlueprint() {
    try {
        const res = await fetch(`${API_BASE}/blueprint`);
        if (!res.ok) throw new Error();
        const data = await res.json();

        canvas.querySelectorAll('.canvas-node').forEach(el => el.remove());

        blueprintNodes = data.nodes || [];
        blueprintConnections = data.connections || [];

        const validStreamIds = streams.map(s => s.id);

        blueprintNodes = blueprintNodes.filter(n => {
            if (n.type === 'stream') {
                return validStreamIds.includes(n.id.replace('stream_', ''));
            } else if (n.type === 'model') {
                return models.includes(n.id.replace('model_', ''));
            }
            return false;
        });

        const validNodeIds = blueprintNodes.map(n => n.id);
        blueprintConnections = blueprintConnections.filter(c =>
            validNodeIds.includes(c.source) && validNodeIds.includes(c.target)
        );

        blueprintNodes.forEach(node => {
            renderNodeHTML(node);
        });

        setTimeout(() => {
            drawConnections();
        }, 200);

    } catch (e) {
        console.error("Failed to load blueprint config:", e);
    }
}

function clearBlueprint() {
    if (!confirm("是否要清空画布上的所有节点和连线？")) return;
    canvas.querySelectorAll('.canvas-node').forEach(el => el.remove());
    blueprintNodes = [];
    blueprintConnections = [];
    drawConnections();
}

function runBlueprintAutoLayout() {
    if (blueprintNodes.length === 0) return;

    const streamsNodes = blueprintNodes.filter(n => n.type === 'stream');
    const modelsNodes = blueprintNodes.filter(n => n.type === 'model');

    const startXStreams = 100;
    const startXModels = 450;
    const startY = 100;
    const gapY = 150;

    streamsNodes.forEach((node, i) => {
        node.x = startXStreams;
        node.y = startY + i * gapY;
        const el = document.getElementById(node.id);
        if (el) {
            el.style.left = `${node.x}px`;
            el.style.top = `${node.y}px`;
            el.dataset.x = node.x;
            el.dataset.y = node.y;
        }
    });

    modelsNodes.forEach((node, i) => {
        node.x = startXModels;
        node.y = startY + i * gapY;
        const el = document.getElementById(node.id);
        if (el) {
            el.style.left = `${node.x}px`;
            el.style.top = `${node.y}px`;
            el.dataset.x = node.x;
            el.dataset.y = node.y;
        }
    });

    drawConnections();

    panX = 50;
    panY = 20;
    zoom = 1.0;
    updateCanvasTransform();
}


/******************************************************************************
 * SHARE MODAL ACTIONS & URL BUILDER
 ******************************************************************************/

async function openShareModal(streamId, streamName) {
    const modal = document.getElementById('share-modal');
    const title = document.getElementById('share-modal-title');
    if (!modal) return;

    title.textContent = `通道 [${streamName}] 外流直播地址`;

    const host = window.location.hostname;

    const rtspUrl = `rtsp://${host}:${zlmPorts.rtsp}/live/${streamId}_yolo`;
    const rtmpUrl = `rtmp://${host}:${zlmPorts.rtmp}/live/${streamId}_yolo`;
    const flvUrl = `http://${host}:${zlmPorts.http}/live/${streamId}_yolo.live.flv`;
    const hlsUrl = `http://${host}:${zlmPorts.http}/live/${streamId}_yolo/hls.m3u8`;

    document.getElementById('share-url-rtsp').value = rtspUrl;
    document.getElementById('share-url-rtmp').value = rtmpUrl;
    document.getElementById('share-url-flv').value = flvUrl;
    document.getElementById('share-url-hls').value = hlsUrl;

    modal.style.display = 'flex';

    // Log this sensitive action to audit logs
    try {
        await fetch(`${API_BASE}/logs/audit`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                action: "查看推流地址",
                details: `查看通道 [${streamName}] (${streamId}) 外流直播地址信息`
            })
        });
    } catch (e) {
        console.error("Failed to write audit log for viewing stream URLs:", e);
    }
}

function closeShareModal() {
    const modal = document.getElementById('share-modal');
    if (modal) modal.style.display = 'none';
}

async function copyUrl(inputId, buttonEl) {
    const input = document.getElementById(inputId);
    if (!input) return;

    try {
        await navigator.clipboard.writeText(input.value);

        const originalHTML = buttonEl.innerHTML;
        buttonEl.innerHTML = `<i class="fa-solid fa-check"></i> 已复制`;
        buttonEl.style.background = "var(--success)";
        buttonEl.style.borderColor = "var(--success)";

        setTimeout(() => {
            buttonEl.innerHTML = originalHTML;
            buttonEl.style.background = "";
            buttonEl.style.borderColor = "";
        }, 1500);
    } catch (err) {
        console.error("Failed to copy text:", err);
        alert("复制失败，请手动选择并复制文本框内容。");
    }
}


/******************************************************************************
 * SYSTEM AUDIT LOGS DISPLAY
 ******************************************************************************/

async function fetchAuditLogs() {
    const tbody = document.getElementById('audit-logs-tbody');
    if (!tbody) return;

    try {
        const res = await fetch(`${API_BASE}/logs`);
        if (!res.ok) throw new Error();
        const logs = await res.json();

        tbody.innerHTML = '';
        if (logs.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" style="text-align: center; padding: 30px; color: var(--text-light); opacity: 0.5;">
                        <i class="fa-solid fa-list-check" style="font-size: 24px; margin-bottom: 8px; display: block;"></i>
                        暂无审计日志记录
                    </td>
                </tr>
            `;
            return;
        }

        logs.forEach(log => {
            const tr = document.createElement('tr');

            // Format timestamp slightly (e.g. 2026-07-01 10:20:30)
            const dateStr = log.timestamp ? log.timestamp.replace('T', ' ').substring(0, 19) : 'N/A';

            tr.innerHTML = `
                <td style="padding: 14px 16px; font-family: monospace;">${dateStr}</td>
                <td style="padding: 14px 16px;"><span class="badge-highlight">${log.username || 'unknown'}</span></td>
                <td style="padding: 14px 16px; font-weight: 600; color: var(--accent);">${log.action}</td>
                <td style="padding: 14px 16px; color: var(--text-main); font-weight: 500;">${log.details || ''}</td>
                <td style="padding: 14px 16px; font-family: monospace; font-size: 11px;">${log.ip_address || ''}</td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error("Failed to fetch audit logs:", e);
    }
}


/******************************************************************************
 * RULES & ANALYSIS CONFIGURATION
 ******************************************************************************/

let sseEventSource = null;

async function openRulesModal(streamId, streamName) {
    document.getElementById('rules-stream-id').value = streamId;
    document.getElementById('rules-modal-title').textContent = `智能分析规则与防区配置 - [${streamName}]`;

    // Initialize forms to default
    document.getElementById('rules-config-form').reset();

    try {
        const res = await fetch(`${API_BASE}/streams/${streamId}/rules`);
        if (!res.ok) throw new Error("获取配置失败");
        const rules = await res.json();

        // Load Audio ONNX config
        await fetchAudioModels();
        const audioConf = rules.audio_config || {};
        document.getElementById('rule-audio-enable').checked = !!audioConf.enabled;
        const audioModelSelect = document.getElementById('rule-audio-model');
        if (audioModelSelect) {
            audioModelSelect.value = audioConf.model_name || '';
            onRuleAudioModelSelectChange(audioModelSelect.value);
        }
        document.getElementById('rule-audio-thresh').value = audioConf.audio_threshold || 0.70;

        // Check selected display_names
        const selectedClasses = audioConf.selected_classes || ['Scream', 'Shout', 'Crying', 'Splash'];
        document.querySelectorAll('#rule-audio-classes-box input[type="checkbox"]').forEach(cb => {
            cb.checked = selectedClasses.includes(cb.value);
        });

    } catch (e) {
        console.warn("Failed to load stream rules, using default templates:", e);
    }

    document.getElementById('rules-modal').style.display = 'flex';
}

function closeRulesModal() {
    document.getElementById('rules-modal').style.display = 'none';
}

// Bind save event handler on form submit
document.getElementById('rules-config-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const streamId = document.getElementById('rules-stream-id').value;

    const selectedAudioClasses = Array.from(
        document.querySelectorAll('#rule-audio-classes-box input[type="checkbox"]:checked')
    ).map(cb => cb.value);

    const payload = {
        rules_config: {
            audio_config: {
                enabled: document.getElementById('rule-audio-enable').checked,
                model_name: document.getElementById('rule-audio-model').value,
                selected_classes: selectedAudioClasses,
                audio_threshold: parseFloat(document.getElementById('rule-audio-thresh').value) || 0.70
            }
        }
    };

    try {
        const res = await fetch(`${API_BASE}/streams/${streamId}/rules`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!res.ok) throw new Error();
        alert("通道音频 AI 分析配置保存成功，音频引擎已即时热更新！");
        closeRulesModal();
        await fetchStreams();
    } catch(err) {
        alert("保存规则配置失败，请检查网络或后端状态。");
    }
});


/******************************************************************************
 * EVENTS & ALARMS ticker CENTER
 ******************************************************************************/

let eventsHistory = [];

async function fetchEventLogs() {
    const tbody = document.getElementById('events-ticker-tbody');
    if (!tbody) return;

    try {
        const res = await fetch(`${API_BASE}/events?limit=100`);
        if (!res.ok) throw new Error();
        eventsHistory = await res.json();
        renderEventTable();
        updateAlarmCounters();
    } catch (e) {
        console.error("Failed to fetch events history:", e);
    }
}

function renderEventTable() {
    const tbody = document.getElementById('events-ticker-tbody');
    if (!tbody) return;

    tbody.innerHTML = '';
    if (eventsHistory.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="6" style="text-align: center; color: var(--text-muted); padding: 30px;"><i class="fa-solid fa-circle-info"></i> 暂无分析告警触发，系统正常运行中</td>
            </tr>
        `;
        return;
    }

    eventsHistory.forEach(ev => {
        const tr = document.createElement('tr');
        tr.style.borderBottom = '1px solid rgba(0,0,0,0.03)';

        let typeBadgeColor = 'var(--text-muted)';
        let typeBgColor = 'rgba(100,116,139,0.1)';
        if (ev.rule_type === 'drowning') {
            typeBadgeColor = '#ef4444';
            typeBgColor = 'rgba(239, 68, 68, 0.1)';
        } else if (ev.rule_type === 'intrusion') {
            typeBadgeColor = '#f59e0b';
            typeBgColor = 'rgba(245, 158, 11, 0.1)';
        } else if (ev.rule_type === 'crowding') {
            typeBadgeColor = '#3b82f6';
            typeBgColor = 'rgba(59, 130, 246, 0.1)';
        } else if (ev.rule_type === 'loitering') {
            typeBadgeColor = '#8b5cf6';
            typeBgColor = 'rgba(139, 92, 246, 0.1)';
        } else if (ev.rule_type === 'audio_alert' || (ev.event_name && ev.event_name.startsWith('Audio_'))) {
            typeBadgeColor = '#7c3aed';
            typeBgColor = 'rgba(124, 58, 237, 0.15)';
        } else if (ev.rule_type === 'detection') {
            typeBadgeColor = 'var(--primary)';
            typeBgColor = 'rgba(109, 40, 217, 0.1)';
        }

        const dateStr = ev.timestamp ? ev.timestamp.replace('T', ' ').substring(0, 19) : 'N/A';
        let confidenceVal = '--';
        if (ev.confidence !== null && ev.confidence !== undefined) {
            confidenceVal = (ev.confidence <= 1 ? ev.confidence : ev.confidence / 100).toFixed(2);
        }

        tr.innerHTML = `
            <td style="padding: 10px 12px; font-family: monospace; font-size: 11px;">${dateStr}</td>
            <td style="padding: 10px 12px; font-weight: bold;"><span class="badge-highlight">${ev.stream_id}</span></td>
            <td style="padding: 10px 12px;"><span style="color: ${typeBadgeColor}; background: ${typeBgColor}; padding: 2px 6px; border-radius: 4px; font-weight: bold; font-size: 10px;">${ev.event_name}</span></td>
            <td style="padding: 10px 12px; font-weight: bold; color: var(--primary); font-family: monospace;">${confidenceVal}</td>
            <td style="padding: 10px 12px; color: ${ev.rule_type === 'audio_alert' ? '#7c3aed' : 'var(--text-main)'}; font-weight: 600;">${ev.details || ''}</td>
            <td style="padding: 10px 12px; font-family: monospace; font-size: 11px;">${ev.tracker_id !== -1 && ev.tracker_id !== null ? ev.tracker_id : 'N/A'}</td>
        `;
        tbody.appendChild(tr);
    });
}

function updateAlarmCounters() {
    const counts = { drowning: 0, intrusion: 0, crowding: 0, loitering: 0 };
    eventsHistory.forEach(ev => {
        if (ev.rule_type in counts) {
            counts[ev.rule_type]++;
        }
    });

    document.getElementById('stat-drowning-count').textContent = counts.drowning;
    document.getElementById('stat-intrusion-count').textContent = counts.intrusion;
    document.getElementById('stat-crowding-count').textContent = counts.crowding;
    document.getElementById('stat-loitering-count').textContent = counts.loitering;
}

function clearEventList() {
    eventsHistory = [];
    renderEventTable();
    updateAlarmCounters();
}

function showAlarmToast(title, body) {
    const container = document.getElementById('toast-container') || (() => {
        const c = document.createElement('div');
        c.id = 'toast-container';
        c.style.position = 'fixed';
        c.style.top = '80px';
        c.style.right = '20px';
        c.style.zIndex = '99999';
        c.style.display = 'flex';
        c.style.flexDirection = 'column';
        c.style.gap = '10px';
        document.body.appendChild(c);
        return c;
    })();

    const toast = document.createElement('div');
    toast.className = 'glass-card toast-alarm';
    toast.style.background = 'rgba(239, 68, 68, 0.95)';
    toast.style.color = '#fff';
    toast.style.padding = '12px 18px';
    toast.style.borderRadius = '8px';
    toast.style.boxShadow = '0 10px 15px -3px rgba(0, 0, 0, 0.2)';
    toast.style.border = '1px solid rgba(255,255,255,0.2)';
    toast.style.fontSize = '12px';
    toast.style.fontWeight = 'bold';
    toast.style.minWidth = '280px';
    toast.style.maxWidth = '360px';
    toast.style.transition = 'all 0.3s ease';

    toast.innerHTML = `
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 4px;">
            <span><i class="fa-solid fa-triangle-exclamation"></i> ${title}</span>
            <button style="background:none; border:none; color:#fff; font-size:14px; cursor:pointer;" onclick="this.parentElement.parentElement.remove()">&times;</button>
        </div>
        <div style="font-weight: normal; opacity: 0.9;">${body}</div>
    `;
    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => toast.remove(), 300);
    }, 6000);
}

function initEventsSseStream() {
    if (sseEventSource) {
        sseEventSource.close();
    }

    // Prefill developer docs urls
    const sseDocEl = document.getElementById('sse-api-url');
    const histDocEl = document.getElementById('history-api-url');
    if (sseDocEl) sseDocEl.textContent = `EventSource("${window.location.origin}/api/events/stream")`;
    if (histDocEl) histDocEl.textContent = `GET ${window.location.origin}/api/events?limit=100`;

    sseEventSource = new EventSource(`${API_BASE}/events/stream`);

    sseEventSource.onmessage = function (event) {
        try {
            const ev = JSON.parse(event.data);
            if (ev.type === 'realtime_stats') {
                updateRealtimeStreamCard(ev.stream_id, ev.counts);
                return;
            }
            console.log("SSE alarm received:", ev);

            // Insert event to history list
            eventsHistory.unshift(ev);
            if (eventsHistory.length > 200) {
                eventsHistory.pop();
            }

            renderEventTable();
            updateAlarmCounters();

            // Trigger premium warning sound & toast only for actual rule violation alarms
            if (ev.rule_type !== 'detection') {
                showAlarmToast(ev.event_name, `通道 [${ev.stream_id}] 触发警告: ${ev.details}`);
            }
        } catch (err) {
            console.error("Failed to parse SSE data:", err);
        }
    };

    sseEventSource.onerror = function (err) {
        console.warn("SSE connection error, attempting reconnect...", err);
    };
}

const modelClassesCache = {};

async function fetchAndDisplayModelClasses(streamId, modelName) {
    const el = document.getElementById(`realtime-classes-${streamId}`);
    if (!el) return;

    if (modelClassesCache[modelName]) {
        el.textContent = modelClassesCache[modelName].join(', ') || '无';
        return;
    }

    try {
        const res = await fetch(`${API_BASE}/models/${modelName}/classes`);
        if (res.ok) {
            const data = await res.json();
            const classes = data.classes || [];
            modelClassesCache[modelName] = classes;
            el.textContent = classes.join(', ') || '无';
        } else {
            el.textContent = '获取失败';
        }
    } catch (e) {
        el.textContent = '错误';
    }
}

function renderRealtimeAnalysisGrid() {
    const grid = document.getElementById('realtime-analysis-grid');
    if (!grid) return;

    const runningStreams = streams.filter(s => s.status === 'Running' || s.status === 'Reconnecting');

    if (runningStreams.length === 0) {
        grid.innerHTML = `
            <div class="glass-card" style="padding: 20px; text-align: center; color: var(--text-muted); grid-column: 1 / -1;">
                <i class="fa-solid fa-circle-info" style="font-size:20px; margin-bottom:6px; display:block;"></i>
                暂无运行中的智能分析通道，请在“监控大屏”或“直播流管理”启动分析
            </div>
        `;
        return;
    }

    const existingCards = Array.from(grid.querySelectorAll('.stream-realtime-card'));
    const existingIds = existingCards.map(c => c.dataset.streamId);
    const runningIds = runningStreams.map(s => s.id);

    const needsRebuild = existingIds.sort().join(',') !== runningIds.sort().join(',');

    if (needsRebuild) {
        grid.innerHTML = '';
        runningStreams.forEach(s => {
            const card = document.createElement('div');
            card.className = 'glass-card stream-realtime-card';
            card.dataset.streamId = s.id;
            card.style.padding = '14px 18px';
            card.style.borderLeft = '4px solid var(--primary)';
            card.style.minHeight = '120px';

            card.innerHTML = `
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:10px;">
                    <h4 style="margin:0; font-weight:bold; display:flex; align-items:center; gap:6px; color: var(--text-main); font-size: 13px;">
                        <span class="status-dot Running"></span> 通道: ${s.name}
                    </h4>
                    <span style="font-size:10px; color:var(--text-muted); font-weight:bold;" id="realtime-fps-${s.id}">FPS: ${s.inference_fps}</span>
                </div>
                <div style="font-size:11px; color:var(--text-muted); margin-bottom:8px;">
                    运行模型: <span id="realtime-model-${s.id}" style="color:var(--primary); font-weight:bold;">${s.selected_model}</span>
                </div>
                <div style="margin-bottom:8px;">
                    <div style="font-size:10px; color:var(--text-light); margin-bottom:4px; font-weight:500;">实时识别信息:</div>
                    <div id="realtime-tags-${s.id}" style="display:flex; flex-wrap:wrap; gap:6px;">
                        <span style="font-size:10px; color:var(--text-muted); font-style:italic;">等待实时数据...</span>
                    </div>
                </div>
                <div style="font-size:10px; color:var(--text-muted); border-top:1px solid rgba(255,255,255,0.08); padding-top:6px; margin-top:6px;">
                    模型可识别类别: <span id="realtime-classes-${s.id}" style="color:var(--accent); font-weight:600; word-break: break-all;">加载中...</span>
                </div>
            `;
            grid.appendChild(card);

            fetchAndDisplayModelClasses(s.id, s.selected_model);
        });
    } else {
        runningStreams.forEach(s => {
            const fpsEl = document.getElementById(`realtime-fps-${s.id}`);
            const modelEl = document.getElementById(`realtime-model-${s.id}`);
            if (fpsEl) fpsEl.textContent = `FPS: ${s.inference_fps}`;
            if (modelEl && modelEl.textContent !== s.selected_model) {
                modelEl.textContent = s.selected_model;
                fetchAndDisplayModelClasses(s.id, s.selected_model);
            }
        });
    }
}

function updateRealtimeStreamCard(streamId, counts) {
    const container = document.getElementById(`realtime-tags-${streamId}`);
    if (!container) return;

    const classes = Object.keys(counts).sort();
    if (classes.length === 0) {
        container.innerHTML = `<span style="font-size:10px; color:var(--text-muted); font-style:italic;">无检测目标</span>`;
        return;
    }

    container.innerHTML = classes.map(cls => `
        <span style="background:rgba(109, 40, 217, 0.08); color:var(--primary); padding:3px 8px; border-radius:4px; border:1px solid rgba(109, 40, 217, 0.15); font-weight:bold; font-size:10px; display:flex; align-items:center; gap:4px;">
            ${cls}: <span style="background:var(--primary); color:#fff; border-radius:10px; padding:0px 5px; font-size:9px;">${counts[cls]}</span>
        </span>
    `).join('');
}

function updateSandboxStreamsDropdown() {
    const dropdown = document.getElementById('api-sandbox-stream');
    if (!dropdown) return;
    dropdown.innerHTML = streams.map(s => `<option value="${s.id}">${s.name} (${s.id})</option>`).join('');

    // Toggle stream select display based on selected API
    const apiSelect = document.getElementById('api-sandbox-select');
    const streamContainer = document.getElementById('api-sandbox-stream-container');
    if (apiSelect && streamContainer) {
        streamContainer.style.display = apiSelect.value === 'stream-urls' ? 'block' : 'none';
    }
}

async function testSandboxApi() {
    const apiType = document.getElementById('api-sandbox-select').value;
    const streamId = document.getElementById('api-sandbox-stream').value;
    const resultPre = document.getElementById('api-sandbox-result');
    const statusSpan = document.getElementById('api-sandbox-status');

    if (!resultPre) return;

    resultPre.textContent = "正在向后端发送请求，请稍候...";
    if (statusSpan) statusSpan.style.display = 'none';

    try {
        let url = `${API_BASE}/realtime/data`;
        if (apiType === 'stream-urls') {
            if (!streamId) {
                alert("未选择监控通道！");
                resultPre.textContent = "请先选择一个通道发送请求。";
                return;
            }
            url = `${API_BASE}/streams/${streamId}/urls`;
        }

        const res = await fetch(url);
        if (statusSpan) {
            statusSpan.style.display = 'inline';
            statusSpan.textContent = `HTTP ${res.status} ${res.statusText}`;
            statusSpan.className = res.ok ? 'text-success' : 'text-danger';
            statusSpan.style.color = res.ok ? 'var(--success)' : '#ef4444';
        }

        const data = await res.json();
        resultPre.textContent = JSON.stringify(data, null, 2);
    } catch (err) {
        resultPre.textContent = `请求失败: ${err.message}`;
    }
}

// Expose audio functions on window scope for inline HTML handlers
window.uploadAudioModelFiles = uploadAudioModelFiles;
window.deleteAudioModel = deleteAudioModel;
window.fetchAudioModels = fetchAudioModels;
window.onRuleAudioModelSelectChange = onRuleAudioModelSelectChange;
window.selectAllRuleAudioClasses = selectAllRuleAudioClasses;
window.clearRuleAudioClasses = clearRuleAudioClasses;
