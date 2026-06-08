const icDir = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23F5A623'><path d='M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>`;
const icFile = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23717970'><path d='M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z'/></svg>`;

let mode = 'local';
let currentView = 'files';
let target = '';
let targetName = '';
let currentRoot = '';
let currentParent = '';
let currentFile = '';
let pendingDelFolder = null;
let warnToastTimer = null;
let syncPaths = [];
let syncLoadedKey = '';
let cspRegistry = null;
let cspLoadedKey = '';
let cspRawDirty = false;
let pendingCspIndex = -1;
let proxyLoadedKey = '';
let dialogClosing = false;
let loadingCount = 0;
let heartbeatTimer = null;
let remoteHealthTimer = null;
let remoteHealth = {};
let remoteHealthPending = {};
let fileSelection = new Set();
let currentFiles = [];
let devicePanelOpen = false;
let syncMode = 'push';
let proxyEnabled = false;
let proxyMode = 'form';
let proxyRules = [];

const REQUEST_TIMEOUT = 12000;
const FILE_TIMEOUT = 15000;
const UPLOAD_TIMEOUT = 60000;
const SYNC_TIMEOUT = 600000;
const REMOTE_HEALTH_INTERVAL = 6000;
const REMOTE_HEALTH_BLOCK_MS = 18000;

function escPath(s) { return String(s || '').replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/\\/g, '\\\\').replace(/'/g, "\\'"); }
function escHtml(s) { return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
function itemId(path) { return String(path || '').replace(/[^a-zA-Z0-9_-]/g, '_'); }
const authToken = new URLSearchParams(location.search).get('token') || '';
function authParams(extra = {}) { return authToken ? { ...extra, token: authToken } : extra; }
function authUrl(url) {
    if (!authToken || String(url || '').includes('token=')) return url;
    return url + (String(url).includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(authToken);
}
function remoteManageActive() { return mode === 'remote' && currentView !== 'sync' && !!target; }
function targetParam() { return authParams(remoteManageActive() ? { target } : {}); }
function targetQuery(extra = {}) { return new URLSearchParams(authParams({ ...targetParam(), ...extra })).toString(); }
function activeKey() { return mode + ':' + target; }
function healthKey(url = target) { return String(url || '').replace(/\/+$/, ''); }

function fileApi(path, download = false) {
    if (mode === 'remote' && target) return authUrl('/manage/remote/file?' + new URLSearchParams({ target, path, download: download ? '1' : '' }).toString());
    const encoded = path.split('/').map(encodeURIComponent).join('/');
    return authUrl('/file' + encoded + (download ? '?download=1' : ''));
}

function archiveApi() {
    return authUrl(mode === 'remote' && target ? '/manage/remote/archive' : '/manage/file/archive');
}

function heartbeat(close = false) {
    $.ajax({ url: authUrl('/manage/session'), type: 'post', data: authParams(close ? { close: 'true' } : {}), timeout: 3000, cache: false })
        .done(res => {
            const data = parseJson(res);
            renderServiceStatus(data);
        })
        .fail(() => {
            $('#serviceStatus').text('连接异常').addClass('off').removeClass('warn');
            $('#keepAliveHint').css('display', 'flex');
        });
}

function renderServiceStatus(data) {
    const running = !!(data && data.running && data.serverRunning !== false);
    const optimized = !!(data && (data.backgroundSettingsNeeded != null ? data.backgroundSettingsNeeded : data.batteryOptimized));
    const missingLock = !!(data && running && (!data.wakeLock || !data.wifiLock));
    const text = !running ? '页面已关闭' : optimized ? '后台设置' : missingLock ? '保活异常' : '页面运行中';
    const title = running ? `后台受限: ${optimized ? '是' : '否'}，CPU锁: ${data.wakeLock ? '是' : '否'}，Wi-Fi锁: ${data.wifiLock ? '是' : '否'}` : '管理页服务未运行';
    $('#serviceStatus').text(text).toggleClass('off', !running).toggleClass('warn', running && (optimized || missingLock)).attr('title', title);
    $('#backgroundGuideText').text(data && data.backgroundGuide ? data.backgroundGuide : 'App 进入后台后可能断开连接，请允许后台高耗电或后台运行。');
    $('#keepAliveHint').css('display', running && optimized ? 'flex' : 'none');
}

function startHeartbeat() {
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    heartbeat(false);
    heartbeatTimer = setInterval(() => heartbeat(false), 20000);
}

function startRemoteHealth() {
    if (remoteHealthTimer) clearInterval(remoteHealthTimer);
    remoteHealthTimer = null;
    if (!target || !(mode === 'remote' || currentView === 'sync')) {
        updateTargetHealthUi();
        return;
    }
    pingRemote(true);
    remoteHealthTimer = setInterval(() => pingRemote(false), REMOTE_HEALTH_INTERVAL);
}

function pingRemote(force = false, callback) {
    const key = healthKey();
    if (!key) { if (callback) callback(false); return; }
    if (remoteHealthPending[key]) {
        if (callback) setTimeout(() => {
            const state = targetHealth(key);
            if (state && state.ok != null && !state.checking) callback(!!state.ok);
            else pingRemote(force, callback);
        }, 500);
        return;
    }
    remoteHealthPending[key] = true;
    if (!remoteHealth[key]) remoteHealth[key] = { checking: true, time: 0 };
    else remoteHealth[key].checking = true;
    updateTargetHealthUi();
    $.ajax({ url: authUrl('/manage/remote/ping'), data: authParams({ target: key }), timeout: 1800, cache: false })
        .done(res => {
            let data = {};
            try { data = parseJson(res); } catch (e) {}
            const device = data.device || {};
            remoteHealth[key] = {
                ok: !!data.ok,
                checking: false,
                time: Date.now(),
                message: data.message || '',
                name: device.name || ''
            };
            if (target === key && device.name && !targetName) targetName = device.name;
        })
        .fail((xhr, status) => {
            remoteHealth[key] = { ok: false, checking: false, time: Date.now(), message: status || 'failed' };
        })
        .always(() => {
            remoteHealthPending[key] = false;
            updateTargetText();
            updateTargetHealthUi();
            renderDeviceHealth();
            if (callback) callback(!!(remoteHealth[key] && remoteHealth[key].ok));
        });
}

function targetHealth(url = target) {
    return remoteHealth[healthKey(url)] || null;
}

function isRemoteOffline(url = target) {
    const state = targetHealth(url);
    return !!(state && state.ok === false && Date.now() - state.time < REMOTE_HEALTH_BLOCK_MS);
}

function isRemoteRequest(url, data) {
    if (url === '/manage/remote/ping') return false;
    if (data && data.target) return true;
    if (String(url || '').includes('target=')) return true;
    return String(url || '').startsWith('/manage/remote/');
}

function blockOfflineRemote(url, data) {
    const remote = data && data.target ? data.target : target;
    if (!isRemoteRequest(url, data) || !isRemoteOffline(remote)) return false;
    warnToast('远端设备离线，已停止操作');
    pingRemote(true);
    return true;
}

function updateTargetHealthUi() {
    const state = targetHealth();
    const selected = !!target;
    const offline = selected && state && state.ok === false;
    const online = selected && state && state.ok === true;
    const checking = selected && (!state || state.checking);
    $('#remotePicker').toggleClass('remote-offline', !!offline).toggleClass('remote-online', !!online).toggleClass('remote-checking', !!checking);
    $('#targetStatusDot').toggleClass('ok-dot', !!online).toggleClass('offline-dot', !!offline).toggleClass('pending-dot', !online && !offline);
    $('#targetStatusText').text(!selected ? '远端设备' : offline ? '远端离线' : online ? '远端在线' : '检测中');
}

function renderDeviceHealth() {
    $('#deviceList .device-item').each(function () {
        const key = healthKey($(this).data('ip'));
        const state = remoteHealth[key];
        $(this).toggleClass('offline', !!(state && state.ok === false)).toggleClass('online', !!(state && state.ok === true));
    });
}

function stopManagePage() {
    postAction('/manage/session', { stop: 'true' }, () => {
        $('#serviceStatus').text('页面已关闭').addClass('off');
        warnToast('管理页面已关闭');
    }, '关闭失败');
}

function openBackgroundSettings() {
    const data = mode === 'remote' && target ? { target } : {};
    postAction('/manage/background/settings', data, res => {
        let info = {};
        try { info = parseJson(res); } catch (e) {}
        warnToast(info.opened ? '已尝试打开后台设置' : (info.guide || '未找到可直达的后台设置页'));
        heartbeat(false);
    }, '后台设置打开失败');
}

function showLoading() { loadingCount++; $('#loadingToast').show(); }
function hideLoading() { loadingCount = Math.max(0, loadingCount - 1); if (loadingCount === 0) $('#loadingToast').hide(); }

function requestError(xhr, status, fallback) {
    if (status === 'timeout') return '请求超时，请确认 App 仍在前台或设备未被系统限制后台运行';
    if (status === 'abort') return '请求已取消';
    return xhr && xhr.responseText ? xhr.responseText : fallback;
}

function parseJson(res) {
    return typeof res === 'string' ? JSON.parse(res) : res;
}

function ajaxJson(options, done, failText = '加载失败') {
    showLoading();
    $.ajax({ timeout: REQUEST_TIMEOUT, cache: false, ...options, url: authUrl(options.url), data: authParams(options.data || {}) })
        .done(res => {
            try { done(parseJson(res)); }
            catch (e) { warnToast('响应格式错误'); }
        })
        .fail((xhr, status) => warnToast(requestError(xhr, status, failText)))
        .always(hideLoading);
}

function getJson(url, done, failText = '加载失败') {
    if (blockOfflineRemote(url, null)) return;
    ajaxJson({ url }, done, failText);
}

function postJson(url, data, done, failText = '保存失败') {
    const payload = { ...targetParam(), ...data };
    if (blockOfflineRemote(url, payload)) return;
    ajaxJson({ url, type: 'post', data: payload }, done, failText);
}

function postAction(url, data, done, failText = '操作失败') {
    if (blockOfflineRemote(url, data)) return;
    showLoading();
    $.ajax({ url: authUrl(url), type: 'post', data: authParams(data || {}), timeout: REQUEST_TIMEOUT, cache: false })
        .done(done)
        .fail((xhr, status) => warnToast(requestError(xhr, status, failText)))
        .always(hideLoading);
}

function setManageMode(next) {
    mode = next;
    const fallbackView = mode === 'local' && (currentView === 'push' || currentView === 'search');
    if (fallbackView) currentView = 'files';
    $('#modeLocal').toggleClass('active', mode === 'local');
    $('#modeRemote').toggleClass('active', mode === 'remote');
    $('body').toggleClass('remote-mode', mode === 'remote').toggleClass('local-mode', mode === 'local');
    if (fallbackView) activateManageView('files');
    updateRemotePicker();
    updateTargetText();
    resetViewState();
    if (mode === 'remote' || currentView === 'sync') loadDevices();
    startRemoteHealth();
    if (mode === 'remote' && target) pingRemote(true, ok => { if (ok) loadCurrentView(true); else warnToast('远端设备离线'); });
    else loadCurrentView(true);
}

function updateTargetText() {
    $('#manageTargetText').text(mode === 'remote' ? (target ? '远端管理 · ' + targetName : '远端管理 · 请选择设备') : '本机管理 · 当前 App 设备');
    $('#targetDeviceText').html(target ? `<span>${escHtml(targetName || target)}</span><small>${escHtml(target)}</small>` : '<span>请选择设备</span>');
    $('#syncTargetText').text(targetName || target || '未选择');
}

function resetViewState() {
    currentRoot = '';
    currentParent = '';
    syncLoadedKey = '';
    cspLoadedKey = '';
    proxyLoadedKey = '';
    $('#file_list').html('');
    currentFiles = [];
    clearFileSelection();
}

function loadDevices(scan = false) {
    getJson('/manage/devices' + (scan ? '?scan=true' : ''), data => renderDevices(data.devices || []), '设备列表加载失败');
}

function scanDevices() {
    devicePanelOpen = true;
    updateRemotePicker();
    loadDevices(true);
    setTimeout(loadDevices, 1200);
    setTimeout(loadDevices, 2600);
}

function renderDevices(devices) {
    $('#deviceList').html(devices.map(device => {
        const active = target === device.ip ? ' active' : '';
        const state = targetHealth(device.ip);
        const health = state && state.ok === false ? ' offline' : state && state.ok === true ? ' online' : '';
        return `<button class="device-item${active}${health}" data-ip="${escHtml(device.ip || '')}" type="button" onclick="selectDevice('${escPath(device.ip)}','${escPath(device.name || device.ip)}')"><i class="device-dot"></i><span>${escHtml(device.name || device.ip)}</span><small>${escHtml(device.ip || '')}</small></button>`;
    }).join('') || '<div class="empty-state">未发现设备，请确认电视和手机在同一局域网，并已打开 App</div>');
    updateRemotePicker();
    renderDeviceHealth();
}

function selectDevice(ip, name) {
    target = ip;
    targetName = name;
    devicePanelOpen = false;
    updateTargetText();
    updateRemotePicker();
    resetViewState();
    startRemoteHealth();
    pingRemote(true, ok => { if (ok) loadCurrentView(true); else warnToast('远端设备离线，稍后会自动重试'); });
    loadDevices();
}

function toggleDevicePanel() {
    devicePanelOpen = !devicePanelOpen;
    updateRemotePicker();
    if (devicePanelOpen) loadDevices();
}

function updateRemotePicker() {
    const visible = mode === 'remote' || currentView === 'sync';
    $('#remotePicker').css('display', visible ? 'grid' : 'none');
    $('#deviceList').toggle(visible && devicePanelOpen);
    $('#changeDeviceBtn').text(target ? (devicePanelOpen ? '收起列表' : '更换设备') : (devicePanelOpen ? '收起列表' : '选择设备'));
    updateTargetText();
}

function showManageView(view) {
    currentView = view;
    activateManageView(view);
    updateRemotePicker();
    if (currentView === 'sync') loadDevices();
    startRemoteHealth();
    if (mode === 'remote' && currentView !== 'sync' && target) {
        if (isRemoteOffline(target)) {
            warnToast('远端设备离线，已停止加载');
            pingRemote(true);
            return;
        }
        pingRemote(true, ok => { if (ok) loadCurrentView(false); else warnToast('远端设备离线'); });
    } else {
        loadCurrentView(false);
    }
}

function activateManageView(view) {
    $('.manage-view').removeClass('active');
    $('#view' + view.charAt(0).toUpperCase() + view.slice(1)).addClass('active');
    $('.manage-nav .md-nav-item').removeClass('active');
    $('#nav' + view.charAt(0).toUpperCase() + view.slice(1)).addClass('active');
}

function ensureTarget() {
    if (mode !== 'remote' || currentView === 'sync' || target) return true;
    warnToast('请先选择远端设备');
    devicePanelOpen = true;
    updateRemotePicker();
    loadDevices();
    return false;
}

function loadCurrentView(force) {
    if (!ensureTarget()) return;
    if (currentView === 'files') listFile(force ? '' : currentRoot);
    if (currentView === 'sync') loadSyncManage(force);
    if (currentView === 'csp') loadCspManage(force);
    if (currentView === 'proxy') loadProxyManage(force);
}

function formatFileSize(size, isDir) {
    if (isDir) return '文件夹';
    const value = Number(size);
    if (!Number.isFinite(value) || value < 0) return '-';
    if (value < 1024) return value + ' B';
    const units = ['KB', 'MB', 'GB', 'TB'];
    let n = value / 1024;
    let unit = units[0];
    for (let i = 1; i < units.length && n >= 1024; i++) { n /= 1024; unit = units[i]; }
    return (n >= 100 ? n.toFixed(0) : n >= 10 ? n.toFixed(1) : n.toFixed(2)).replace(/\.0+$/, '') + ' ' + unit;
}

function renderFileBreadcrumb(path) {
    const parts = String(path || '').split('/').filter(Boolean);
    const rows = [`<button class="crumb" type="button" onclick="listFile('')">全部文件</button>`];
    let current = '';
    parts.forEach(part => {
        current += '/' + part;
        rows.push(`<span class="crumb-sep">/</span><button class="crumb" type="button" onclick="listFile('${escPath(current)}')">${escHtml(part)}</button>`);
    });
    $('#fileBreadcrumb').html(rows.join(''));
    $('#fileUpBtn').prop('disabled', currentParent === '.');
}

function buildDirItem(name, time, path, size) {
    const ep = escPath(path);
    const checked = fileSelection.has(path) ? ' checked' : '';
    return `<div class="file-item file-row is-dir"><label class="tree-check file-check"><input type="checkbox" aria-label="选择 ${escHtml(name)}" onchange="toggleFileSelection('${ep}',this.checked)"${checked}></label><button class="file-main file-name-cell" type="button" onclick="listFile('${ep}')"><img class="file-icon" src="${icDir}" alt=""><div class="file-info"><div class="file-name">${escHtml(name)}</div><div class="file-time mobile-meta">${escHtml(time)} · ${formatFileSize(size, true)}</div></div></button><div class="file-time file-time-cell">${escHtml(time)}</div><div class="file-size-cell">${formatFileSize(size, true)}</div><div class="file-actions-cell"><button class="file-action" type="button" onclick="downloadArchive(['${ep}'])">打包</button><button class="file-action danger" type="button" onclick="showDelFolderDialog('${ep}',currentRoot)">删除</button></div></div>`;
}

function buildFileItem(name, time, path, size) {
    const ep = escPath(path);
    const checked = fileSelection.has(path) ? ' checked' : '';
    return `<div class="file-item file-row"><label class="tree-check file-check"><input type="checkbox" aria-label="选择 ${escHtml(name)}" onchange="toggleFileSelection('${ep}',this.checked)"${checked}></label><button class="file-main file-name-cell" type="button" onclick="selectFile('${ep}')"><img class="file-icon" src="${icFile}" alt=""><div class="file-info"><div class="file-name">${escHtml(name)}</div><div class="file-time mobile-meta">${escHtml(time)} · ${formatFileSize(size, false)}</div></div></button><div class="file-time file-time-cell">${escHtml(time)}</div><div class="file-size-cell">${formatFileSize(size, false)}</div><div class="file-actions-cell"><button class="file-action" type="button" onclick="downloadPath('${ep}')">下载</button><button class="file-action danger" type="button" onclick="showDelFileDialog('${ep}')">删除</button></div></div>`;
}

function toggleFileSelection(path, checked) { checked ? fileSelection.add(path) : fileSelection.delete(path); updateFileSelection(); }
function toggleSelectAll(checked) { fileSelection = checked ? new Set(currentFiles) : new Set(); $('#file_list input[type=checkbox]').prop('checked', checked); updateFileSelection(); }
function clearFileSelection() { fileSelection.clear(); $('#file_list input[type=checkbox],#fileSelectAll').prop('checked', false); updateFileSelection(); }
function updateFileSelection() {
    const count = fileSelection.size;
    const total = currentFiles.length;
    const all = total > 0 && count === total;
    const partial = count > 0 && count < total;
    $('#fileSelectionText').text(count ? `已选择 ${count} 项` : `${total} 项`);
    $('#fileZipBtn,#fileClearBtn').prop('disabled', count === 0);
    $('#fileSelectionBar').toggleClass('active', count > 0);
    $('#fileSelectAll').prop('checked', all).prop('indeterminate', partial);
}

function listFile(path = '') {
    if (!ensureTarget()) return;
    if (blockOfflineRemote(fileApi(path), null)) return;
    showLoading();
    $.ajax({ url: fileApi(path), timeout: FILE_TIMEOUT, cache: false })
        .done(res => {
        let info;
        try { info = parseJson(res); }
        catch (e) { warnToast('响应格式错误'); return; }
        currentRoot = path;
        currentParent = info.parent || '';
        const files = info.files || [];
        currentFiles = files.map(node => node.path).filter(Boolean);
        renderFileBreadcrumb(path);
        fileSelection.clear();
        updateFileSelection();
        const rows = [];
        files.forEach(node => rows.push(node.dir === 1 ? buildDirItem(node.name, node.time, node.path, node.size) : buildFileItem(node.name, node.time, node.path, node.size)));
        $('#file_list').html(rows.join('') || '<div class="empty-state file-empty"><div>当前目录没有文件</div></div>');
    })
        .fail((xhr, status) => warnToast(requestError(xhr, status, '加载失败')))
        .always(hideLoading);
}

function uploadFile() { if (ensureTarget()) $('#file_uploader').click(); }
function onFileSelected() {
    const files = $('#file_uploader')[0].files;
    if (!files.length) return;
    $('#uploadTipContent').text(Array.from(files).map(f => f.name).join(', '));
    openDialog('uploadTip');
}

function confirmUpload(yes) {
    closeDialog('uploadTip');
    if (yes !== 1) return;
    const files = $('#file_uploader')[0].files;
    if (!files.length) return;
    const formData = new FormData();
    formData.append('path', currentRoot);
    const remote = mode === 'remote' && !!target;
    if (blockOfflineRemote(remote ? '/manage/remote/upload' : '/upload', remote ? { target } : {})) return;
    if (remote) formData.append('target', target);
    if (authToken) formData.append('token', authToken);
    Array.from(files).forEach((file, index) => formData.append('files-' + index, file));
    showLoading();
    $.ajax({ url: authUrl(remote ? '/manage/remote/upload' : '/upload'), type: 'post', data: formData, processData: false, contentType: false, timeout: UPLOAD_TIMEOUT })
        .done(() => listFile(currentRoot))
        .fail((xhr, status) => warnToast(requestError(xhr, status, '上传失败')))
        .always(() => { $('#file_uploader').val(''); hideLoading(); });
}

function showNewFolderDialog() { if (ensureTarget()) openDialog('newFolder'); }
function confirmNewFolder(yes) {
    closeDialog('newFolder');
    const name = $('#newFolderContent').val().trim();
    $('#newFolderContent').val('');
    if (yes !== 1 || !name) return;
    const remote = mode === 'remote' && !!target;
    postAction(remote ? '/manage/remote/newFolder' : '/newFolder', { ...(remote ? { target } : {}), path: currentRoot, name }, () => listFile(currentRoot), '新增失败');
}

function showDelFolderDialog(path, refreshPath) { pendingDelFolder = { path, refreshPath }; $('#delFolderContent').text('是否删除 ' + path); openDialog('delFolder'); }
function confirmDelFolder(yes) {
    closeDialog('delFolder');
    if (yes !== 1 || !pendingDelFolder) { pendingDelFolder = null; return; }
    const { path, refreshPath } = pendingDelFolder;
    pendingDelFolder = null;
    const remote = mode === 'remote' && !!target;
    postAction(remote ? '/manage/remote/delFolder' : '/delFolder', { ...(remote ? { target } : {}), path }, () => listFile(refreshPath), '删除失败');
}

function showDelFileDialog(path) { currentFile = path; $('#delFileContent').text('是否删除 ' + path); openDialog('delFile'); }
function confirmDelFile(yes) {
    closeDialog('delFile');
    if (yes !== 1) return;
    const remote = mode === 'remote' && !!target;
    postAction(remote ? '/manage/remote/delFile' : '/delFile', { ...(remote ? { target } : {}), path: currentFile }, () => listFile(currentRoot), '删除失败');
}

function selectFile(path) { currentFile = path; $('#fileUrl').text('file:/' + path); openDialog('fileInfoDialog'); }
function downloadFile() { closeDialog('fileInfoDialog'); downloadPath(currentFile); }
function downloadPath(path) {
    if (!path) return;
    if (blockOfflineRemote(fileApi(path, true), null)) return;
    const a = document.createElement('a');
    a.href = fileApi(path, true);
    a.download = path.split('/').filter(Boolean).pop() || 'download';
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function downloadSelectedArchive() { downloadArchive(Array.from(fileSelection)); }
function downloadArchive(paths) {
    if (!paths || !paths.length) return;
    const query = new URLSearchParams({ ...targetParam(), paths: paths.join('\n') }).toString();
    if (blockOfflineRemote(archiveApi(), targetParam())) return;
    const a = document.createElement('a');
    a.href = archiveApi() + '?' + query;
    a.download = paths.length === 1 ? (paths[0].split('/').filter(Boolean).pop() || 'files') + '.zip' : 'webhtv-files.zip';
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function loadSyncManage(force = false) {
    if (syncLoadedKey === activeKey() && !force) return;
    getJson('/manage/sync/paths?' + targetQuery(), data => { syncPaths = data.paths || []; syncLoadedKey = activeKey(); renderSyncPaths(); loadSyncTree(''); });
}

function loadSyncTree(path = '') {
    getJson('/manage/sync/tree?' + targetQuery({ path }), data => {
        $('#syncTreePath').text(data.path || '/');
        const rows = [];
        if (data.parent !== '.') rows.push(`<div class="tree-row"><button class="tree-main" type="button" onclick="loadSyncTree('${escPath(data.parent || '')}')"><img class="file-icon" src="${icDir}" alt=""><span>..</span></button></div>`);
        (data.dirs || []).forEach(item => rows.push(buildSyncDir(item)));
        if (data.truncated) rows.push('<div class="empty-state compact">当前目录过多，仅显示前 300 个目录</div>');
        $('#syncTree').html(rows.join('') || '<div class="empty-state">没有可选目录</div>');
        renderSyncPaths();
    });
}

function buildSyncDir(item) {
    const path = item.path || '';
    const ep = escPath(path);
    const checked = syncPaths.includes(path) ? ' checked' : '';
    return `<div class="tree-row"><label class="tree-check"><input id="sync_${itemId(path)}" type="checkbox" onchange="toggleSyncPath('${ep}',this.checked)"${checked}></label><button class="tree-main" type="button" onclick="loadSyncTree('${ep}')"><img class="file-icon" src="${icDir}" alt=""><span>${escHtml(item.name || path)}</span></button></div>`;
}

function toggleSyncPath(path, checked) { syncPaths = syncPaths.filter(item => item !== path); if (checked) syncPaths.push(path); renderSyncPaths(); }
function removeSyncPath(path) { syncPaths = syncPaths.filter(item => item !== path); renderSyncPaths(); }
function renderSyncPaths() {
    syncPaths = Array.from(new Set(syncPaths.filter(Boolean)));
    $('#syncPathChips').html(syncPaths.map(path => `<button class="path-chip" type="button" onclick="removeSyncPath('${escPath(path)}')">${escHtml(path)} ×</button>`).join('') || '<span class="empty-state compact">未选择目录</span>');
    syncPaths.forEach(path => { const el = document.getElementById('sync_' + itemId(path)); if (el) el.checked = true; });
}
function saveSyncPaths() { postJson('/manage/sync/paths', { paths: syncPaths.join('\n') }, data => { syncPaths = data.paths || []; renderSyncPaths(); warnToast('同步目录已保存'); }); }
function detectSyncPaths() { postJson('/manage/sync/detect', {}, data => { syncPaths = data.paths || []; renderSyncPaths(); warnToast('已自动加入本地包目录'); }, '自动识别失败'); }
function setSyncMode(next) {
    syncMode = next === 'pull' ? 'pull' : 'push';
    $('#syncModePush').toggleClass('active', syncMode === 'push');
    $('#syncModePull').toggleClass('active', syncMode === 'pull');
    $('#syncDirectionBtn').text(syncMode === 'push' ? '推送' : '拉取');
}
function toggleSyncMode() { setSyncMode(syncMode === 'push' ? 'pull' : 'push'); }
function syncOptionIds() { return ['syncOptConfig', 'syncOptSpider', 'syncOptWebHome', 'syncOptSearch', 'syncOptHistory', 'syncOptKeep', 'syncOptSettings']; }
function allSyncSelected() { return syncOptionIds().every(id => $('#' + id).prop('checked')); }
function toggleSyncSelection() {
    const checked = !allSyncSelected();
    syncOptionIds().forEach(id => $('#' + id).prop('checked', checked));
    updateSyncPathsVisible();
}
function updateSyncPathsVisible() {
    const spider = $('#syncOptSpider').prop('checked');
    $('#syncPathsPanel').toggle(spider);
    $('#syncSelectBtn').text(allSyncSelected() ? '取消' : '全选');
}
function syncOptionsPayload() {
    return {
        config: $('#syncOptConfig').prop('checked'),
        spider: $('#syncOptSpider').prop('checked'),
        webHome: $('#syncOptWebHome').prop('checked'),
        search: $('#syncOptSearch').prop('checked'),
        history: $('#syncOptHistory').prop('checked'),
        keep: $('#syncOptKeep').prop('checked'),
        settings: $('#syncOptSettings').prop('checked'),
        paths: syncPaths.join('\n')
    };
}
function startSyncManage() {
    if (!target) {
        warnToast('请先选择远端设备');
        devicePanelOpen = true;
        updateRemotePicker();
        loadDevices();
        return;
    }
    const options = syncOptionsPayload();
    if (!Object.keys(options).some(key => key !== 'paths' && options[key])) {
        warnToast('至少选择一项同步内容');
        return;
    }
    if (isRemoteOffline(target)) {
        warnToast('远端设备离线，已停止同步');
        pingRemote(true);
        return;
    }
    showLoading();
    $.ajax({
        url: authUrl('/manage/sync/start'),
        type: 'post',
        data: authParams({ device: target, mode: syncMode, options: JSON.stringify(options), paths: syncPaths.join('\n') }),
        timeout: SYNC_TIMEOUT,
        cache: false
    })
        .done(res => {
            let data = {};
            try { data = parseJson(res); } catch (e) {}
            const detail = data.files ? ` · ${data.files} 个文件 · ${formatFileSize(data.zipSize, false)}` : '';
            warnToast((syncMode === 'push' ? '推送完成' : '拉取已完成') + detail);
        })
        .fail((xhr, status) => warnToast(requestError(xhr, status, '同步失败')))
        .always(hideLoading);
}

function loadCspManage(force = false) {
    if (cspLoadedKey === activeKey() && !force) return;
    getJson('/manage/csp?' + targetQuery(), data => { cspRegistry = normalizeCspRegistry(data); cspLoadedKey = activeKey(); renderCspManage(); });
}
const CSP_KINDS = ['webHome', 'csp', 'live'];
function cspKind(item = {}) {
    if (CSP_KINDS.includes(item.kind)) return item.kind;
    if (item.live && typeof item.live === 'object') return 'live';
    if (!item.site && !item.key && (item.url || item.groups || item.epg)) return 'live';
    if (item.webHome === true) return 'webHome';
    if (item.webHome === false) return 'csp';
    const api = String(siteValue(item, 'api', ''));
    const home = String(siteValue(item, 'homePage', siteValue(item, 'webHome', '')));
    return !api && !!home ? 'webHome' : 'csp';
}
function cspKindName(kind) { return kind === 'live' ? '直播' : kind === 'webHome' ? 'WebHome' : '通用 CSP'; }
function liveDefaultObject(name = '') { return { name, type: 0, playerType: 2, ua: 'okhttp' }; }
function siteValue(item, key, fallback = '') {
    if (item[key] !== undefined && item[key] !== null && item[key] !== '') return item[key];
    return item.site && item.site[key] !== undefined && item.site[key] !== null ? item.site[key] : fallback;
}
function liveValue(item, key, fallback = '') {
    if (item[key] !== undefined && item[key] !== null && item[key] !== '') return item[key];
    return item.live && item.live[key] !== undefined && item.live[key] !== null ? item.live[key] : fallback;
}
function rawObjectFromItem(item, drop = []) {
    const object = { ...(item || {}) };
    ['id', 'enabled', 'kind', 'site', 'live', 'items', 'sites', 'lives', 'headerText', 'styleText', 'siteText', 'liveText', 'catchupText', 'coreText', 'groupsText', ...drop].forEach(key => delete object[key]);
    return object;
}
function jsonText(value, fallback) {
    const data = value === undefined || value === null ? fallback : value;
    return JSON.stringify(data, null, 2);
}
function hasJsonValue(value) {
    if (value === undefined || value === null || value === '') return false;
    if (Array.isArray(value)) return value.length > 0;
    if (typeof value === 'object') return Object.keys(value).length > 0;
    return true;
}
function assignOptionalJson(object, key, value) {
    if (!hasJsonValue(value)) delete object[key];
    else object[key] = value;
}
function buildFieldLabel(text, required = false, note = '') {
    const tag = required ? '必填' : '可选';
    const cls = required ? 'required-label' : 'optional-label';
    return `<label class="form-label live-field-label ${cls}">${escHtml(text)}<span>${escHtml(note || tag)}</span></label>`;
}
function buildLiveTextField(key, label, value, placeholder = '', required = false, note = '', type = 'text', extraClass = '') {
    return `<div class="md-field ${extraClass}">${buildFieldLabel(label, required, note)}<input class="md-input csp-field" data-key="${key}" type="${type}" value="${escHtml(value)}" placeholder="${escHtml(placeholder)}"></div>`;
}
function buildJsonTextarea(key, value, placeholder = '') {
    return `<textarea class="code-area csp-field compact-code" data-key="${key}" spellcheck="false" placeholder="${escHtml(placeholder)}">${escHtml(value)}</textarea>`;
}
function normalizeCspRegistry(data) {
    const source = { ...(data || {}) };
    const r = data || {};
    r.enabled = r.enabled !== false;
    r.insertIndex = Math.max(0, Math.min(9, Number(r.insertIndex || 0)));
    r.homeKey = r.homeKey || '';
    if (!Array.isArray(r.items)) {
        r.items = [];
        if (Array.isArray(r.sites)) r.sites.forEach(site => r.items.push({ kind: 'csp', site, ...site }));
        if (Array.isArray(r.lives)) r.lives.forEach(live => r.items.push({ kind: 'live', live, ...live }));
        if (!r.items.length && (source.kind || source.site || source.live || source.key || source.url || source.groups || source.epg)) r.items.push(source);
    }
    delete r.sites;
    delete r.lives;
    r.items.forEach((item, i) => normalizeCspItem(item, i));
    return r;
}
function normalizeCspItem(item, index = 0) {
    item.kind = cspKind(item);
    item.enabled = item.enabled !== false;
    item.id = item.id || (item.kind + '_' + Date.now() + '_' + index);
    if (item.kind === 'live') return normalizeLiveItem(item, index);
    item.live = null;
    item.site = item.site && typeof item.site === 'object' ? item.site : rawObjectFromItem(item, ['webHome']);
    item.webHome = item.kind === 'webHome';
    item.key = item.key || siteValue(item, 'key', '__custom_csp_' + item.id);
    const inferredApi = String(siteValue(item, 'api', ''));
    item.name = item.name || siteValue(item, 'name', cspKindName(item.kind) + ' ' + (index + 1));
    item.type = Number(siteValue(item, 'type', 3));
    item.api = inferredApi;
    item.ext = siteValue(item, 'ext', '');
    item.jar = String(siteValue(item, 'jar', ''));
    item.homePage = String(siteValue(item, 'homePage', siteValue(item, 'webHome', '')));
    item.click = String(siteValue(item, 'click', ''));
    item.playUrl = String(siteValue(item, 'playUrl', ''));
    item.hide = Number(siteValue(item, 'hide', 0));
    item.indexs = Number(siteValue(item, 'indexs', 0));
    item.timeout = siteValue(item, 'timeout', '');
    item.searchable = Number(siteValue(item, 'searchable', item.webHome ? 0 : 1));
    item.changeable = Number(siteValue(item, 'changeable', 1));
    item.quickSearch = Number(siteValue(item, 'quickSearch', item.webHome ? 0 : 1));
    item.categories = Array.isArray(item.categories) ? item.categories : (Array.isArray(item.site.categories) ? item.site.categories : []);
    item.header = item.header || item.site.header || {};
    item.style = item.style || item.site.style || {};
    item.headerText = JSON.stringify(item.header || {}, null, 2);
    item.styleText = JSON.stringify(item.style || {}, null, 2);
    syncCspSite(item);
    return item;
}
function normalizeLiveItem(item, index = 0) {
    item.kind = 'live';
    item.webHome = false;
    item.site = {};
    item.live = item.live && typeof item.live === 'object' ? item.live : rawObjectFromItem(item, ['webHome', 'key', 'homePage', 'playUrl', 'hide', 'searchable', 'changeable', 'quickSearch', 'indexs', 'categories', 'style']);
    item.name = item.name || liveValue(item, 'name', '直播 ' + (index + 1));
    item.type = liveValue(item, 'type', 0);
    item.playerType = liveValue(item, 'playerType', 2);
    item.url = String(liveValue(item, 'url', ''));
    item.api = String(liveValue(item, 'api', ''));
    item.ext = liveValue(item, 'ext', '');
    item.jar = String(liveValue(item, 'jar', ''));
    item.click = String(liveValue(item, 'click', ''));
    item.logo = String(liveValue(item, 'logo', ''));
    item.epg = String(liveValue(item, 'epg', ''));
    item.ua = String(liveValue(item, 'ua', 'okhttp'));
    item.origin = String(liveValue(item, 'origin', ''));
    item.referer = String(liveValue(item, 'referer', ''));
    item.timeZone = String(liveValue(item, 'timeZone', ''));
    item.keep = String(liveValue(item, 'keep', ''));
    item.timeout = liveValue(item, 'timeout', '');
    item.header = liveValue(item, 'header', {});
    item.catchup = liveValue(item, 'catchup', {});
    item.core = liveValue(item, 'core', {});
    item.groups = liveValue(item, 'groups', []);
    item.boot = liveValue(item, 'boot', '');
    item.pass = liveValue(item, 'pass', '');
    item.headerText = jsonText(item.header, {});
    item.catchupText = jsonText(item.catchup, {});
    item.coreText = jsonText(item.core, {});
    item.groupsText = jsonText(Array.isArray(item.groups) ? item.groups : [], []);
    syncCspLive(item);
    return item;
}
function syncCspItem(item) { return item.kind === 'live' ? syncCspLive(item) : syncCspSite(item); }
function assignOptional(object, key, value) {
    if (value === undefined || value === null || value === '') delete object[key];
    else object[key] = value;
}
function syncCspSite(item) {
    const site = { ...(item.site || {}) };
    item.kind = item.kind === 'webHome' ? 'webHome' : 'csp';
    item.webHome = item.kind === 'webHome';
    site.key = item.key;
    site.name = item.name;
    site.type = Number(item.type || 0);
    site.homePage = item.homePage || '';
    site.hide = Number(item.hide || 0);
    site.searchable = Number(item.searchable || 0);
    site.changeable = Number(item.changeable || 0);
    site.quickSearch = Number(item.quickSearch || 0);
    if (item.webHome) {
        site.api = '';
        site.ext = '';
        site.jar = '';
        delete site.click;
        delete site.playUrl;
    } else {
        site.api = item.api || '';
        site.ext = item.ext || '';
        site.jar = item.jar || '';
        site.click = item.click || '';
        site.playUrl = item.playUrl || '';
        site.indexs = Number(item.indexs || 0);
        if (item.timeout !== '' && item.timeout !== null) site.timeout = Number(item.timeout || 0); else delete site.timeout;
        site.categories = Array.isArray(item.categories) ? item.categories : [];
        site.header = item.header || {};
        site.style = item.style || {};
    }
    item.live = null;
    ['url', 'logo', 'epg', 'ua', 'origin', 'referer', 'timeZone', 'keep', 'playerType', 'boot', 'pass', 'catchup', 'core', 'groups', 'liveText', 'catchupText', 'coreText', 'groupsText'].forEach(key => delete item[key]);
    item.site = site;
    item.siteText = JSON.stringify(site || {}, null, 2);
}
function syncCspLive(item) {
    const live = { ...(item.live || {}) };
    item.kind = 'live';
    item.webHome = false;
    live.name = item.name || '';
    assignOptional(live, 'type', item.type === '' || item.type === null || item.type === undefined ? '' : Number(item.type || 0));
    assignOptional(live, 'playerType', item.playerType === '' || item.playerType === null || item.playerType === undefined ? '' : Number(item.playerType || 0));
    assignOptional(live, 'url', item.url || '');
    assignOptional(live, 'api', item.api || '');
    assignOptional(live, 'ext', item.ext || '');
    assignOptional(live, 'jar', item.jar || '');
    assignOptional(live, 'click', item.click || '');
    assignOptional(live, 'logo', item.logo || '');
    assignOptional(live, 'epg', item.epg || '');
    assignOptional(live, 'ua', item.ua || '');
    assignOptional(live, 'origin', item.origin || '');
    assignOptional(live, 'referer', item.referer || '');
    assignOptional(live, 'timeZone', item.timeZone || '');
    assignOptional(live, 'keep', item.keep || '');
    if (item.timeout !== '' && item.timeout !== null) live.timeout = Number(item.timeout || 0); else delete live.timeout;
    if (item.boot === true || item.boot === false) live.boot = !!item.boot; else delete live.boot;
    if (item.pass === true || item.pass === false) live.pass = !!item.pass; else delete live.pass;
    assignOptionalJson(live, 'header', item.header);
    assignOptionalJson(live, 'catchup', item.catchup);
    assignOptionalJson(live, 'core', item.core);
    assignOptionalJson(live, 'groups', Array.isArray(item.groups) ? item.groups : []);
    item.site = {};
    item.live = live;
    item.headerText = jsonText(live.header, {});
    item.catchupText = jsonText(live.catchup, {});
    item.coreText = jsonText(live.core, {});
    item.groupsText = jsonText(live.groups, []);
    item.liveText = JSON.stringify(live || {}, null, 2);
}
function stripCspMeta(registry) {
    const copy = JSON.parse(JSON.stringify(registry || {}));
    delete copy.active;
    delete copy.enabledCount;
    delete copy.itemsCount;
    (copy.items || []).forEach(item => { delete item.headerText; delete item.styleText; delete item.siteText; delete item.liveText; delete item.catchupText; delete item.coreText; delete item.groupsText; });
    return copy;
}
function renderCspManage() {
    $('#cspEnabled').prop('checked', cspRegistry.enabled !== false);
    $('#cspInsertText').text((cspRegistry.insertIndex || 0) + 1);
    $('#cspSummary').text(`${cspRegistry.active || 0}/${cspRegistry.enabledCount || 0} 可用 · ${cspRegistry.items.length} 条`);
    $('#cspList').html(cspRegistry.items.map(buildCspCard).join('') || '<div class="empty-state">还没有站点注入条目</div>');
    $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
    cspRawDirty = false;
}
function cspItemValid(item) {
    if (item.kind === 'live') return !!item.name && (!!item.url || !!(Array.isArray(item.groups) && item.groups.length) || !!(item.live && Array.isArray(item.live.groups) && item.live.groups.length));
    return item.webHome ? !!item.homePage : !!item.api;
}
function buildCspCard(item, index) {
    const invalid = item.enabled && !cspItemValid(item) ? ' invalid' : '';
    const title = item.name || cspKindName(item.kind);
    const source = item.webHome ? `<div class="source-actions"><button class="md-btn md-btn-tonal md-btn-compact" type="button" onclick="chooseCspFile(${index})">文件</button><button class="md-btn md-btn-tonal md-btn-compact" type="button" onclick="openCspCode(${index})">代码</button><button class="md-btn md-btn-tonal md-btn-compact" type="button" onclick="openCspLink(${index})">链接</button></div>` : '';
    const typeButtons = `<div class="segmented csp-type-toggle"><button class="segment ${item.kind === 'webHome' ? 'active' : ''}" onclick="setCspKind(${index},'webHome')" type="button">WebHome</button><button class="segment ${item.kind === 'csp' ? 'active' : ''}" onclick="setCspKind(${index},'csp')" type="button">通用 CSP</button><button class="segment ${item.kind === 'live' ? 'active' : ''}" onclick="setCspKind(${index},'live')" type="button">直播</button></div>`;
    const nameRow = item.kind === 'live'
        ? `<div class="field-row compact">${buildLiveTextField('name', '名称', item.name, '直播名称', true)}</div>`
        : `<div class="field-row compact"><input class="md-input csp-field" data-key="name" value="${escHtml(item.name)}" placeholder="名称"><input class="md-input csp-field" data-key="key" value="${escHtml(item.key)}" placeholder="Key"></div>`;
    const homeLine = item.kind === 'live' ? '' : `<div class="csp-home-line">${buildHomeCheck(item, index)}${source}</div>`;
    const homePage = item.kind === 'live' ? '' : `<div class="md-field"><input class="md-input csp-field" data-key="homePage" value="${escHtml(item.homePage)}" placeholder="${item.webHome ? 'WebHome 地址' : 'WebHome 首页地址，可选'}"></div>`;
    const fields = item.kind === 'live' ? buildLiveFields(item) : item.webHome ? buildAdvancedSiteFields(item) : buildCommonCspFields(item);
    return `<div class="manage-card csp-card${invalid}" data-index="${index}"><div class="csp-head"><div class="csp-title-block"><label class="check-row"><input class="csp-field" data-key="enabled" type="checkbox" ${item.enabled ? 'checked' : ''}><span>${escHtml(title)}</span></label>${typeButtons}</div><div class="card-actions"><button class="file-action" type="button" onclick="moveCspItem(${index},-1)">上移</button><button class="file-action" type="button" onclick="moveCspItem(${index},1)">下移</button><button class="file-action danger" type="button" onclick="removeCspItem(${index})">删除</button></div></div>${nameRow}${homeLine}${homePage}${fields}</div>`;
}
function buildHomeCheck(item, index) { return `<label class="check-row"><input class="csp-home" type="checkbox" ${cspRegistry.homeKey === item.key ? 'checked' : ''} onchange="setCspHome(${index},this.checked)"><span>设为首页</span></label>`; }
function buildCommonCspFields(item) {
    return `<div class="field-row compact"><input class="md-input mini-input csp-field" data-key="type" type="number" value="${escHtml(item.type)}" placeholder="类型"><input class="md-input csp-field" data-key="api" value="${escHtml(item.api)}" placeholder="API / CSP 类名"></div><div class="field-row compact"><input class="md-input csp-field" data-key="jar" value="${escHtml(item.jar)}" placeholder="Jar"><input class="md-input csp-field" data-key="ext" value="${escHtml(typeof item.ext === 'string' ? item.ext : JSON.stringify(item.ext))}" placeholder="Ext"></div><div class="field-row compact"><input class="md-input csp-field" data-key="click" value="${escHtml(item.click)}" placeholder="点击脚本"><input class="md-input csp-field" data-key="playUrl" value="${escHtml(item.playUrl)}" placeholder="播放前缀"></div><div class="field-row compact"><input class="md-input mini-input csp-field" data-key="indexs" type="number" value="${escHtml(item.indexs)}" placeholder="索引"><input class="md-input mini-input csp-field" data-key="timeout" type="number" value="${escHtml(item.timeout)}" placeholder="超时秒"><input class="md-input csp-field" data-key="categories" value="${escHtml((item.categories || []).join(','))}" placeholder="分类，逗号分隔"></div><div class="flag-grid"><label class="check-row"><input class="csp-field" data-key="hide" type="checkbox" ${item.hide ? 'checked' : ''}><span>隐藏</span></label><label class="check-row"><input class="csp-field" data-key="searchable" type="checkbox" ${item.searchable ? 'checked' : ''}><span>搜索</span></label><label class="check-row"><input class="csp-field" data-key="changeable" type="checkbox" ${item.changeable ? 'checked' : ''}><span>换源</span></label><label class="check-row"><input class="csp-field" data-key="quickSearch" type="checkbox" ${item.quickSearch ? 'checked' : ''}><span>快搜</span></label></div><details class="advanced-panel"><summary>高级参数</summary><label class="form-label">Header JSON</label><textarea class="code-area csp-field compact-code" data-key="headerText" spellcheck="false" placeholder="Header JSON">${escHtml(item.headerText)}</textarea><label class="form-label">Style JSON</label><textarea class="code-area csp-field compact-code" data-key="styleText" spellcheck="false" placeholder="Style JSON">${escHtml(item.styleText)}</textarea>${buildAdvancedSiteFields(item, false)}</details>`;
}
function buildLiveFields(item) {
    return `<div class="field-row compact live-common-row">${buildLiveTextField('url', '直播地址', item.url, 'http(s):// 或本地路径', true, 'URL/分组至少一项')}</div><div class="field-row compact">${buildLiveTextField('ua', 'UA', item.ua, 'okhttp')}${buildLiveTextField('epg', 'EPG', item.epg, 'http://...{name}...')}${buildLiveTextField('logo', 'Logo', item.logo, 'https://.../{name}.png')}</div>${buildAdvancedLiveFields(item)}`;
}
function buildAdvancedSiteFields(item, wrap = true) {
    const field = `<label class="form-label">完整 Site JSON</label><textarea class="code-area csp-field compact-code" data-key="siteText" spellcheck="false" placeholder="完整站点 JSON，可填写 docs/应用完整开发文档.md 里的其它字段">${escHtml(item.siteText)}</textarea>`;
    return wrap ? `<details class="advanced-panel"><summary>高级 Site JSON</summary>${field}</details>` : field;
}
function buildAdvancedLiveFields(item) {
    return `<details class="advanced-panel"><summary>高级直播参数（可选）</summary><div class="field-row compact advanced-field-row">${buildLiveTextField('api', 'API', item.api, 'raw 兼容字段')}${buildLiveTextField('ext', 'Ext', typeof item.ext === 'string' ? item.ext : JSON.stringify(item.ext), 'raw 兼容字段')}${buildLiveTextField('timeout', '超时秒', item.timeout, '', false, '可选', 'number', 'short')}</div><div class="field-row compact advanced-field-row">${buildLiveTextField('jar', 'Jar', item.jar, 'raw 兼容字段')}${buildLiveTextField('click', '点击脚本', item.click, '可选')}${buildLiveTextField('keep', 'Keep', item.keep, '分组@@频道@@线路，可选')}</div><div class="field-row compact advanced-field-row">${buildLiveTextField('origin', 'Origin', item.origin, '可选')}${buildLiveTextField('referer', 'Referer', item.referer, '可选')}${buildLiveTextField('timeZone', '时区', item.timeZone, 'Asia/Shanghai')}</div><div class="flag-grid live-flag-grid"><label class="check-row"><input class="csp-field" data-key="boot" type="checkbox" ${item.boot ? 'checked' : ''}><span>启动进入</span></label><label class="check-row"><input class="csp-field" data-key="pass" type="checkbox" ${item.pass ? 'checked' : ''}><span>跳过分组</span></label></div>${buildFieldLabel('Header JSON')}${buildJsonTextarea('headerText', item.headerText, 'Header JSON，例如 {"User-Agent":"okhttp"}')}${buildFieldLabel('Catchup JSON')}${buildJsonTextarea('catchupText', item.catchupText, 'Catchup JSON')}${buildFieldLabel('Core JSON')}${buildJsonTextarea('coreText', item.coreText, 'Core JSON')}${buildFieldLabel('Groups JSON')}${buildJsonTextarea('groupsText', item.groupsText, 'Groups 数组')}${buildFieldLabel('完整 Live JSON')}${buildJsonTextarea('liveText', item.liveText, '完整直播 JSON，可包含 raw JSON 的所有字段')}</details>`;
}
function updateCspGlobal() {
    if (!cspRegistry) return;
    cspRegistry.enabled = $('#cspEnabled').prop('checked');
    cspRegistry.insertIndex = Math.max(0, Math.min(9, Number(cspRegistry.insertIndex || 0)));
    $('#cspInsertText').text(cspRegistry.insertIndex + 1);
    $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
}
function stepCspInsert(delta) { if (!cspRegistry) return; cspRegistry.insertIndex = Math.max(0, Math.min(9, Number(cspRegistry.insertIndex || 0) + delta)); updateCspGlobal(); }
function syncCspFromCards(updateRaw = true) {
    if (!cspRegistry) return;
    cspRegistry.enabled = $('#cspEnabled').prop('checked');
    cspRegistry.insertIndex = Math.max(0, Math.min(9, Number(cspRegistry.insertIndex || 0)));
    $('#cspList .csp-card').each(function () {
        const item = cspRegistry.items[Number($(this).data('index'))];
        $(this).find('.csp-field').each(function () {
            const key = $(this).data('key');
            if (this.type === 'checkbox') item[key] = ['enabled', 'boot', 'pass'].includes(key) ? this.checked : (this.checked ? 1 : 0);
            else if (['type', 'playerType', 'hide', 'searchable', 'changeable', 'quickSearch', 'indexs'].includes(key)) item[key] = this.value === '' ? '' : Number(this.value || 0);
            else if (key === 'timeout') item[key] = this.value === '' ? '' : Number(this.value || 0);
            else if (key === 'categories') item[key] = this.value.split(',').map(x => x.trim()).filter(Boolean);
            else if (key === 'headerText') item.header = parseJsonField(this.value, {});
            else if (key === 'styleText') item.style = parseJsonField(this.value, {});
            else if (key === 'siteText') item.site = parseJsonField(this.value, item.site || {});
            else if (key === 'catchupText') item.catchup = parseOptionalJsonField(this.value, item.catchup || {}, {});
            else if (key === 'coreText') item.core = parseOptionalJsonField(this.value, item.core || {}, {});
            else if (key === 'groupsText') item.groups = parseOptionalJsonField(this.value, item.groups || [], []);
            else if (key === 'liveText') item.live = parseJsonField(this.value, item.live || {});
            else item[key] = this.value.trim();
        });
        syncCspItem(item);
    });
    if (updateRaw) {
        $('#cspRaw').val(JSON.stringify(stripCspMeta(cspRegistry), null, 2));
        cspRawDirty = false;
    }
}
function parseJsonField(text, fallback) { try { return text && text.trim() ? JSON.parse(text) : fallback; } catch (e) { warnToast('JSON 格式无效，已保留为空对象'); return fallback; } }
function parseOptionalJsonField(text, fallback, emptyValue) {
    try { return text && text.trim() ? JSON.parse(text) : emptyValue; }
    catch (e) { warnToast('JSON 格式无效，已保留原值'); return fallback; }
}
function addCspItem(kind) {
    if (!cspRegistry) cspRegistry = normalizeCspRegistry({});
    syncCspFromCards(false);
    if (kind === true) kind = 'webHome';
    if (kind === false) kind = 'csp';
    if (!CSP_KINDS.includes(kind)) kind = 'webHome';
    const n = cspRegistry.items.filter(x => x.kind === kind).length + 1;
    const name = cspKindName(kind) + ' ' + n;
    const seed = { kind, webHome: kind === 'webHome', name };
    if (kind === 'live') seed.live = liveDefaultObject(name);
    cspRegistry.items.push(normalizeCspItem(seed, cspRegistry.items.length));
    renderCspManage();
}
function removeCspItem(index) { syncCspFromCards(false); const item = cspRegistry.items[index]; if (item && item.kind !== 'live' && cspRegistry.homeKey === item.key) cspRegistry.homeKey = ''; cspRegistry.items.splice(index, 1); renderCspManage(); }
function moveCspItem(index, delta) { syncCspFromCards(false); const targetIndex = index + delta; if (targetIndex < 0 || targetIndex >= cspRegistry.items.length) return; const item = cspRegistry.items.splice(index, 1)[0]; cspRegistry.items.splice(targetIndex, 0, item); renderCspManage(); }
function setCspHome(index, checked) { syncCspFromCards(false); const item = cspRegistry.items[index]; if (!item || item.kind === 'live') return; cspRegistry.homeKey = checked ? item.key : ''; renderCspManage(); }
function setCspKind(index, kind) {
    syncCspFromCards(false);
    const item = cspRegistry.items[index];
    if (kind === true) kind = 'webHome';
    if (kind === false) kind = 'csp';
    if (!item || item.kind === kind || !CSP_KINDS.includes(kind)) return;
    const oldKind = item.kind;
    const oldAuto = /^(WebHome|通用 CSP|直播) \d+$/.test(item.name || '');
    if (item.kind !== 'live' && kind === 'live' && cspRegistry.homeKey === item.key) cspRegistry.homeKey = '';
    item.kind = kind;
    item.webHome = kind === 'webHome';
    if (kind === 'webHome') {
        if (oldKind === 'live') item.type = 3;
        item.api = '';
        item.ext = '';
        item.jar = '';
        item.searchable = 0;
        item.quickSearch = 0;
    } else if (kind === 'csp') {
        if (oldKind === 'live') item.type = 3;
        item.searchable = 1;
        item.quickSearch = 1;
    } else if (kind === 'live') {
        item.live = { ...liveDefaultObject(item.name), ...(item.live || {}) };
        item.type = liveValue(item, 'type', 0);
        item.playerType = liveValue(item, 'playerType', 2);
        item.ua = String(liveValue(item, 'ua', 'okhttp'));
    }
    if (oldAuto) {
        const n = cspRegistry.items.filter((x, i) => i !== index && x.kind === kind).length + 1;
        item.name = cspKindName(kind) + ' ' + n;
    }
    normalizeCspItem(item, index);
    renderCspManage();
}
function chooseCspFile(index) {
    syncCspFromCards(false);
    pendingCspIndex = index;
    $('#csp_file_uploader').val('').click();
}
function onCspFileSelected() {
    const file = $('#csp_file_uploader')[0].files[0];
    if (!file || pendingCspIndex < 0) return;
    const reader = new FileReader();
    reader.onload = e => saveCspPage(pendingCspIndex, { code: e.target.result || '' }, '文件已载入');
    reader.onerror = () => warnToast('文件读取失败');
    reader.readAsText(file);
}
function openCspCode(index) {
    syncCspFromCards(false);
    pendingCspIndex = index;
    $('#cspCodeContent').val('');
    openDialog('cspCodeDialog');
}
function confirmCspCode(yes) {
    closeDialog('cspCodeDialog');
    if (yes !== 1 || pendingCspIndex < 0) return;
    saveCspPage(pendingCspIndex, { code: $('#cspCodeContent').val() }, '代码已保存');
}
function openCspLink(index) {
    syncCspFromCards(false);
    pendingCspIndex = index;
    const item = cspRegistry.items[index] || {};
    $('#cspLinkContent').val(/^file:\/\//i.test(item.homePage || '') ? '' : (item.homePage || ''));
    openDialog('cspLinkDialog');
}
function confirmCspLink(yes) {
    closeDialog('cspLinkDialog');
    const link = $('#cspLinkContent').val().trim();
    if (yes !== 1 || pendingCspIndex < 0 || !link) return;
    saveCspPage(pendingCspIndex, { link }, '链接已设置');
}
function saveCspPage(index, data, message) {
    const item = cspRegistry && cspRegistry.items[index];
    if (!item) return;
    postJson('/manage/csp/page', { id: item.id, ...data }, res => {
        item.id = res.id || item.id;
        item.homePage = res.homePage || item.homePage;
        syncCspSite(item);
        renderCspManage();
        warnToast(message || 'WebHome 已更新，请保存生效');
        pendingCspIndex = -1;
    }, 'WebHome 保存失败');
}
function saveCspManage() {
    if (!cspRawDirty) syncCspFromCards(true);
    try { cspRegistry = normalizeCspRegistry(JSON.parse($('#cspRaw').val().trim() || '{}')); }
    catch (e) { warnToast('站点注入 JSON 格式无效'); return; }
    cspRegistry.items.forEach(syncCspItem);
    postJson('/manage/csp', { registry: JSON.stringify(stripCspMeta(cspRegistry)) }, data => { cspRegistry = normalizeCspRegistry(data); renderCspManage(); warnToast('站点注入已保存'); });
}

function loadProxyManage(force = false) {
    if (proxyLoadedKey === activeKey() && !force) return;
    getJson('/manage/proxy?' + targetQuery(), data => {
        proxyLoadedKey = activeKey();
        proxyEnabled = !!data.enabled;
        $('#proxyUrl').val(data.url || '');
        $('#proxyRules').val(formatProxyRules(parseProxyRules(data.rules || '')));
        proxyRules = parseProxyRules(data.rules || '');
        renderProxyManage(data);
    });
}
function updateProxySummary(data = {}) {
    const count = data.count != null ? data.count : proxyRules.filter(rule => rule.hosts || rule.url).length;
    const configured = count > 0 || !!cleanProxyUrl($('#proxyUrl').val());
    $('#proxySummary').text(`${proxyEnabled ? '启用' : '禁用'} · ${count || 0} 条 · ${configured ? '已配置' : '未配置'}`);
}
function renderProxyManage(data = {}) {
    $('#proxyEnabled').text(proxyEnabled ? '启用' : '禁用').toggleClass('on', proxyEnabled).toggleClass('off', !proxyEnabled);
    $('#proxyModeForm').toggleClass('active', proxyMode === 'form');
    $('#proxyModeText').toggleClass('active', proxyMode === 'text');
    $('#proxyFormPanel').toggle(proxyMode === 'form');
    $('#proxyTextPanel').toggle(proxyMode === 'text');
    if (!proxyRules.length) proxyRules = [proxyRule('', '')];
    $('#proxyRuleList').html(proxyRules.map(buildProxyRule).join(''));
    updateProxySummary(data);
}
function toggleProxyEnabled() {
    proxyEnabled = !proxyEnabled;
    renderProxyManage();
}
function setProxyMode(next) {
    if (next === proxyMode) return;
    if (proxyMode === 'form') syncProxyTextFromForm();
    else proxyRules = parseProxyRules($('#proxyRules').val());
    proxyMode = next === 'text' ? 'text' : 'form';
    if (proxyMode === 'text') $('#proxyRules').val(formatProxyRules(proxyRules));
    renderProxyManage();
}
function proxyRule(hosts, url) { return { hosts: hosts || '', url: url || '' }; }
function buildProxyRule(rule, index) {
    return `<div class="proxy-rule-card" data-index="${index}"><div class="proxy-rule-head"><span>规则 ${index + 1}</span><div class="card-actions"><button class="file-action" onclick="moveProxyRule(${index},-1)" type="button">上移</button><button class="file-action" onclick="moveProxyRule(${index},1)" type="button">下移</button><button class="file-action danger" onclick="removeProxyRule(${index})" type="button">删除</button></div></div><div class="proxy-rule-fields"><div><label class="form-label">域名 / Host</label><input class="md-input proxy-rule-hosts" value="${escHtml(rule.hosts)}" placeholder="例如 * 或 api.example.com,*.example.org"></div><div><label class="form-label">代理地址</label><input class="md-input proxy-rule-url" value="${escHtml(rule.url)}" placeholder="留空时使用默认代理地址"></div></div></div>`;
}
function syncProxyRulesFromForm() {
    const items = [];
    $('#proxyRuleList .proxy-rule-card').each(function () {
        items.push(proxyRule($(this).find('.proxy-rule-hosts').val().trim(), $(this).find('.proxy-rule-url').val().trim()));
    });
    proxyRules = items.length ? items : [proxyRule('', '')];
}
function syncProxyTextFromForm() {
    syncProxyRulesFromForm();
    $('#proxyRules').val(formatProxyRules(proxyRules));
}
function addProxyRule() {
    syncProxyRulesFromForm();
    proxyRules.push(proxyRule('', cleanProxyUrl($('#proxyUrl').val())));
    renderProxyManage();
}
function removeProxyRule(index) {
    syncProxyRulesFromForm();
    proxyRules.splice(index, 1);
    if (!proxyRules.length) proxyRules.push(proxyRule('', ''));
    renderProxyManage();
}
function moveProxyRule(index, delta) {
    syncProxyRulesFromForm();
    const next = index + delta;
    if (next < 0 || next >= proxyRules.length) return;
    const item = proxyRules.splice(index, 1)[0];
    proxyRules.splice(next, 0, item);
    renderProxyManage();
}
function parseProxyRules(text) {
    const raw = String(text || '').trim();
    if (!raw) return [];
    if (raw[0] === '{' || raw[0] === '[') return parseProxyJson(raw);
    const rows = [];
    raw.split(/\r?\n/).forEach(line => {
        const value = line.trim();
        if (!value || value.startsWith('#')) return;
        const parts = value.split(/\s+/, 2);
        if (parts.length === 1 && looksLikeProxyUrl(parts[0])) rows.push(proxyRule('*', parts[0]));
        else rows.push(proxyRule(parts[0], parts.length > 1 ? parts[1] : ''));
    });
    return rows;
}
function parseProxyJson(text) {
    try {
        const root = JSON.parse(text);
        const array = Array.isArray(root) ? root : (Array.isArray(root.proxy) ? root.proxy : [root]);
        return array.map(item => proxyRule(joinProxyValue(item.hosts), joinProxyValue(item.urls))).filter(item => item.hosts || item.url);
    } catch (e) {
        warnToast('Proxy JSON 格式无效');
        return [];
    }
}
function joinProxyValue(value) {
    if (Array.isArray(value)) return value.map(item => String(item).trim()).filter(Boolean).join(',');
    return value == null ? '' : String(value).trim();
}
function splitProxyValue(value) {
    return String(value || '').split(',').map(item => item.trim()).filter(Boolean);
}
function formatProxyRules(items) {
    const proxy = (items || []).map(item => {
        const hosts = splitProxyValue(item.hosts || '*');
        const urls = splitProxyValue(item.url);
        if (!hosts.length && !urls.length) return null;
        const rule = { hosts: hosts.length ? hosts : ['*'] };
        if (urls.length) rule.urls = urls;
        return rule;
    }).filter(Boolean);
    return proxy.length ? JSON.stringify({ proxy }, null, 2) : '';
}
function cleanProxyUrl(url) {
    const value = String(url || '').trim();
    return value.toLowerCase() === 'socks5://' ? '' : value;
}
function looksLikeProxyUrl(text) {
    return /^(https?|socks)\w*:\/\//i.test(String(text || '').trim());
}
function saveProxyManage() {
    if (proxyMode === 'form') syncProxyTextFromForm();
    else proxyRules = parseProxyRules($('#proxyRules').val());
    const rules = proxyMode === 'form' ? formatProxyRules(proxyRules) : $('#proxyRules').val().trim();
    postJson('/manage/proxy', { enabled: proxyEnabled ? 'true' : 'false', url: cleanProxyUrl($('#proxyUrl').val()), rules }, data => {
        proxyEnabled = !!data.enabled;
        proxyRules = parseProxyRules(data.rules || rules);
        $('#proxyRules').val(formatProxyRules(proxyRules));
        renderProxyManage(data);
        warnToast('Proxy 已保存');
    }, 'Proxy 保存失败');
}

function remoteSearch() { if (!ensureTarget()) return; postAction('/manage/action', { target, do: 'search', word: $('#remoteKeyword').val().trim() }, () => warnToast('已发送搜索'), '搜索发送失败'); }
function remotePush() { if (!ensureTarget()) return; postAction('/manage/action', { target, do: 'push', url: $('#remotePushUrl').val().trim() }, () => warnToast('已发送推送'), '推送发送失败'); }

function openDialog(id) { $('#' + id).show(); history.pushState({ dialog: id }, ''); }
function closeDialog(id) { dialogClosing = true; $('#' + id).hide(); history.back(); }
function warnToast(msg) { $('#warnToastContent').text(msg); $('#warnToast').show(); if (warnToastTimer) clearTimeout(warnToastTimer); warnToastTimer = setTimeout(() => { $('#warnToast').hide(); warnToastTimer = null; }, 1500); }

window.addEventListener('popstate', function () {
    if (dialogClosing) { dialogClosing = false; return; }
    const visible = $('.md-dialog-overlay:visible');
    if (visible.length) { visible.first().hide(); return; }
    if (currentView === 'files') listFile(currentParent);
});

$(function () {
    startHeartbeat();
    setManageMode('local');
    updateSyncPathsVisible();
    $('#newFolderContent').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); confirmNewFolder(1); } });
    $(document).on('input', '#cspList input.csp-field', function () { syncCspFromCards(); });
    $(document).on('change', '#cspList .csp-field', function () { syncCspFromCards(); });
    $('#cspRaw').on('input', function () { cspRawDirty = true; });
    $(document).on('change', '#viewSync input[type=checkbox]', updateSyncPathsVisible);
});
