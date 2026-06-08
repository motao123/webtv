const icDir = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23F5A623'><path d='M10 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z'/></svg>`;
const icFile = `data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='%23717970'><path d='M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z'/></svg>`;
let currentRoot = '';
let currentFile = '';
let currentParent = '';
let longPressTimer = null;
let longPressTriggered = false;
let pendingDelFolder = null;
let warnToastTimer = null;
let danmakuMode = 1;
let danmakuSize = 25;
let dialogClosing = false;
const authToken = new URLSearchParams(location.search).get('token') || '';
function authParams(extra = {}) { return authToken ? { ...extra, token: authToken } : extra; }
function authUrl(url) {
    if (!authToken || String(url || '').includes('token=')) return url;
    return url + (String(url).includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(authToken);
}

function search() {
    doAction('search', { word: $('#keyword').val() });
}

function push() {
    doAction('push', { url: $('#push_url').val() });
}

function setting() {
    doAction('setting', { text: $('#setting_text').val(), name: $('#setting_name').val() });
}

function sendDanmaku() {
    const text = $('#danmaku_text').val().trim();
    if (!text) return;
    doAction('danmaku', { text: `[0.0,${danmakuMode},${danmakuSize},16777215]${text}` });
    $('#danmaku_text').val('');
}

function showDanmakuModeDialog() {
    $('#danmakuModeDialog .md-dialog-list-item').removeClass('active');
    $(`#danmakuModeDialog .md-dialog-list-item[data-val="${danmakuMode}"]`).addClass('active');
    openDialog('danmakuModeDialog');
}

function setDanmakuMode(val, label) {
    danmakuMode = val;
    $('#danmaku_mode_label').text(label);
    closeDialog('danmakuModeDialog');
}

function showDanmakuSizeDialog() {
    $('#danmakuSizeDialog .md-dialog-list-item').removeClass('active');
    $(`#danmakuSizeDialog .md-dialog-list-item[data-val="${danmakuSize}"]`).addClass('active');
    openDialog('danmakuSizeDialog');
}

function setDanmakuSize(val, label) {
    danmakuSize = val;
    $('#danmaku_size_label').text(label);
    closeDialog('danmakuSizeDialog');
}

function doAction(action, kv) {
    $.post(authUrl('/action'), authParams({ ...kv, do: action }));
}

function openDialog(id) {
    $('#' + id).show();
    history.pushState({ dialog: id }, '');
}

function closeDialog(id) {
    dialogClosing = true;
    $('#' + id).hide();
    history.back();
}

function startLongPress(callback) {
    longPressTriggered = false;
    longPressTimer = setTimeout(() => {
        longPressTriggered = true;
        callback();
    }, 500);
}

function cancelLongPress() {
    if (longPressTimer) { clearTimeout(longPressTimer); longPressTimer = null; }
}

function escPath(s) {
    return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function escHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function fileUrl(path, download = false) {
    const encoded = path.split('/').map(encodeURIComponent).join('/');
    return authUrl('/file' + encoded + (download ? '?download=1' : ''));
}

function buildParentItem() {
    return `<div class="file-item">
    <a class="file-main" href="javascript:void(0)" onclick="history.back()">
    <img class="file-icon" src="${icDir}" alt="">
    <div class="file-info"><div class="file-name">..</div></div>
    </a>
    </div>`;
}

function buildDirItem(name, time, path) {
    const ep = escPath(path);
    return `<div class="file-item">
    <a class="file-main" href="javascript:void(0)" ontouchstart="startLongPress(()=>showDelFolderDialog('${ep}',currentRoot))" ontouchmove="cancelLongPress()" ontouchend="cancelLongPress()" oncontextmenu="return false" onclick="if(!longPressTriggered)listFile('${ep}',true)">
    <img class="file-icon" src="${icDir}" alt="">
    <div class="file-info"><div class="file-name">${escHtml(name)}</div><div class="file-time">${escHtml(time)}</div></div>
    </a>
    <button class="file-action danger" type="button" onclick="showDelFolderDialog('${ep}',currentRoot)">刪除</button>
    </div>`;
}

function buildFileItem(name, time, path) {
    const ep = escPath(path);
    return `<div class="file-item">
    <a class="file-main" href="javascript:void(0)" ontouchstart="startLongPress(()=>showDelFileDialog('${ep}'))" ontouchmove="cancelLongPress()" ontouchend="cancelLongPress()" oncontextmenu="return false" onclick="if(!longPressTriggered)selectFile('${ep}')">
    <img class="file-icon" src="${icFile}" alt="">
    <div class="file-info"><div class="file-name">${escHtml(name)}</div><div class="file-time">${escHtml(time)}</div></div>
    </a>
    <button class="file-action" type="button" onclick="downloadPath('${ep}')">下載</button>
    <button class="file-action danger" type="button" onclick="showDelFileDialog('${ep}')">刪除</button>
    </div>`;
}

function addFile(node) {
    $('#file_list').append(node);
}

function selectFile(path) {
    currentFile = path;
    $("#fileUrl").text("file:/" + path);
    openDialog('fileInfoDialog');
}

function pushFile(yes) {
    closeDialog('fileInfoDialog');
    if (yes === 1) doAction('file', { path: "file:/" + currentFile });
}

function downloadFile() {
    closeDialog('fileInfoDialog');
    downloadPath(currentFile);
}

function downloadPath(path) {
    if (!path) return;
    const a = document.createElement('a');
    a.href = fileUrl(path, true);
    a.download = path.split('/').filter(Boolean).pop() || 'download';
    document.body.appendChild(a);
    a.click();
    a.remove();
}

function listFile(path, addHistory = false) {
    const loadingTimer = setTimeout(() => $('#loadingToast').show(), 200);
    $.get(fileUrl(path), function (res) {
        clearTimeout(loadingTimer);
        let info;
        try {
            info = JSON.parse(res);
        } catch (e) {
            $('#loadingToast').hide();
            warnToast('回應格式錯誤');
            return;
        }
        const parent = info.parent;
        currentRoot = path;
        currentParent = parent;
        $('#filePath').text(path || '/');
        const array = info.files;
        if (path === '' && array.length === 0) warnToast('可能沒有存儲權限');
        $('#file_list').html('');
        if (parent !== '.') addFile(buildParentItem());
        array.forEach(node => {
            if (node.dir === 1) addFile(buildDirItem(node.name, node.time, node.path));
            else addFile(buildFileItem(node.name, node.time, node.path));
        });
        if (addHistory) history.pushState(path, '');
        $('#loadingToast').hide();
    }).fail(function () {
        clearTimeout(loadingTimer);
        $('#loadingToast').hide();
        warnToast('載入失敗');
    });
}

function uploadFile() {
    $('#file_uploader').click();
}

function onFileSelected() {
    const files = $('#file_uploader')[0].files;
    if (files.length === 0) return;
    const tip = Array.from(files).map(f => f.name).join(', ');
    $('#uploadTipContent').text(tip);
    openDialog('uploadTip');
}

function confirmUpload(yes) {
    closeDialog('uploadTip');
    if (yes !== 1) return;
    const files = $('#file_uploader')[0].files;
    if (files.length === 0) return;
    const formData = new FormData();
    formData.append('path', currentRoot);
    if (authToken) formData.append('token', authToken);
    Array.from(files).forEach((f, i) => formData.append('files-' + i, f));
    $('#loadingToast').show();
    $.ajax({
        url: authUrl('/upload'),
        type: 'post',
        data: formData,
        processData: false,
        contentType: false,
        complete: function () {
            $('#loadingToast').hide();
            $('#file_uploader').val('');
            listFile(currentRoot);
        }
    });
}

function showNewFolderDialog() {
    openDialog('newFolder');
}

function confirmNewFolder(yes) {
    closeDialog('newFolder');
    const name = $('#newFolderContent').val().trim();
    $('#newFolderContent').val('');
    if (yes !== 1 || name.length === 0) return;
    $('#loadingToast').show();
    $.post(authUrl('/newFolder'), authParams({ path: currentRoot, name }), function () {
        $('#loadingToast').hide();
        listFile(currentRoot);
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('新增失敗');
    });
}

function showDelFolderDialog(path, refreshPath) {
    pendingDelFolder = { path, refreshPath };
    $('#delFolderContent').text('是否刪除 ' + path);
    openDialog('delFolder');
}

function confirmDelFolder(yes) {
    closeDialog('delFolder');
    if (yes !== 1 || !pendingDelFolder) { pendingDelFolder = null; return; }
    const { path, refreshPath } = pendingDelFolder;
    pendingDelFolder = null;
    $('#loadingToast').show();
    $.post(authUrl('/delFolder'), authParams({ path }), function () {
        $('#loadingToast').hide();
        listFile(refreshPath);
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('刪除失敗');
    });
}

function showDelFileDialog(path) {
    currentFile = path;
    $('#delFileContent').text('是否刪除 ' + path);
    openDialog('delFile');
}

function confirmDelFile(yes) {
    closeDialog('delFile');
    if (yes !== 1) return;
    $('#loadingToast').show();
    $.post(authUrl('/delFile'), authParams({ path: currentFile }), function () {
        $('#loadingToast').hide();
        listFile(currentRoot);
    }).fail(function () {
        $('#loadingToast').hide();
        warnToast('刪除失敗');
    });
}

function warnToast(msg) {
    $('#warnToastContent').text(msg);
    $('#warnToast').show();
    if (warnToastTimer) clearTimeout(warnToastTimer);
    warnToastTimer = setTimeout(() => { $('#warnToast').hide(); warnToastTimer = null; }, 1000);
}

function showPanel(id) {
    for (let i = 1; i <= 5; i++) {
        document.getElementById('panel' + i).classList.toggle('active', i === id);
        document.getElementById('tab' + i).classList.toggle('active', i === id);
    }
    if (id === 5 && document.getElementById('file_list').innerHTML === '') listFile('');
}

const tab = parseInt(new URLSearchParams(window.location.search).get('tab')) || 1;
history.replaceState(null, '');
showPanel(tab);

window.addEventListener('popstate', function () {
    if (dialogClosing) { dialogClosing = false; return; }
    const visible = $('.md-dialog-overlay:visible');
    if (visible.length) { visible.first().hide(); return; }
    listFile(currentParent);
});

$(function () {
    $('#keyword').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); search(); } });
    $('#push_url').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); push(); } });
    $('#danmaku_text').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); sendDanmaku(); } });
    $('#setting_name, #setting_text').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); setting(); } });
    $('#newFolderContent').on('keydown', function (e) { if (e.key === 'Enter') { this.blur(); confirmNewFolder(1); } });
});
