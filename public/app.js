(function () {
  "use strict";

  // Font Awesome Free 6.7.2 solid icons (CC BY 4.0) — https://fontawesome.com
  // Paths mirrored from the Android app's own Fa icon set (FaIcons.kt) for a consistent look.
  var FA = {
    pencil: [512, 512, "M410.3 231l11.3-11.3-33.9-33.9-62.1-62.1L291.7 89.8l-11.3 11.3-22.6 22.6L58.6 322.9c-10.4 10.4-18 23.3-22.2 37.4L1 480.7c-2.5 8.4-.2 17.5 6.1 23.7s15.3 8.5 23.7 6.1l120.3-35.4c14.1-4.2 27-11.8 37.4-22.2L387.7 253.7 410.3 231zM160 399.4l-9.1 22.7c-4 3.1-8.5 5.4-13.3 6.9L59.4 452l23-78.1c1.4-4.9 3.8-9.4 6.9-13.3l22.7-9.1 0 32c0 8.8 7.2 16 16 16l32 0zM362.7 18.7L348.3 33.2 325.7 55.8 314.3 67.1l33.9 33.9 62.1 62.1 33.9 33.9 11.3-11.3 22.6-22.6 14.5-14.5c25-25 25-65.5 0-90.5L453.3 18.7c-25-25-65.5-25-90.5 0zm-47.4 168l-144 144c-6.2 6.2-16.4 6.2-22.6 0s-6.2-16.4 0-22.6l144-144c6.2-6.2 16.4-6.2 22.6 0s6.2 16.4 0 22.6z"],
    eraser: [576, 512, "M290.7 57.4L57.4 290.7c-25 25-25 65.5 0 90.5l80 80c12 12 28.3 18.7 45.3 18.7L288 480l9.4 0L512 480c17.7 0 32-14.3 32-32s-14.3-32-32-32l-124.1 0L518.6 285.3c25-25 25-65.5 0-90.5L381.3 57.4c-25-25-65.5-25-90.5 0zM297.4 416l-9.4 0-105.4 0-80-80L227.3 211.3 364.7 348.7 297.4 416z"],
    fillDrip: [576, 512, "M41.4 9.4C53.9-3.1 74.1-3.1 86.6 9.4L168 90.7l53.1-53.1c28.1-28.1 73.7-28.1 101.8 0L474.3 189.1c28.1 28.1 28.1 73.7 0 101.8L283.9 481.4c-37.5 37.5-98.3 37.5-135.8 0L30.6 363.9c-37.5-37.5-37.5-98.3 0-135.8L122.7 136 41.4 54.6c-12.5-12.5-12.5-32.8 0-45.3zm176 221.3L168 181.3 75.9 273.4c-4.2 4.2-7 9.3-8.4 14.6l319.2 0 42.3-42.3c3.1-3.1 3.1-8.2 0-11.3L277.7 82.9c-3.1-3.1-8.2-3.1-11.3 0L213.3 136l49.4 49.4c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0zM512 512c-35.3 0-64-28.7-64-64c0-25.2 32.6-79.6 51.2-108.7c6-9.4 19.5-9.4 25.5 0C543.4 368.4 576 422.8 576 448c0 35.3-28.7 64-64 64z"],
    eyeDropper: [512, 512, "M341.6 29.2L240.1 130.8l-9.4-9.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l160 160c12.5 12.5 32.8 12.5 45.3 0s12.5-32.8 0-45.3l-9.4-9.4L482.8 170.4c39-39 39-102.2 0-141.1s-102.2-39-141.1 0zM55.4 323.3c-15 15-23.4 35.4-23.4 56.6l0 42.4L5.4 462.2c-8.5 12.7-6.8 29.6 4 40.4s27.7 12.5 40.4 4L89.7 480l42.4 0c21.2 0 41.6-8.4 56.6-23.4L309.4 335.9l-45.3-45.3L143.4 411.3c-3 3-7.1 4.7-11.3 4.7L96 416l0-36.1c0-4.2 1.7-8.3 4.7-11.3L221.4 247.9l-45.3-45.3L55.4 323.3z"],
    undo: [512, 512, "M125.7 160l50.3 0c17.7 0 32 14.3 32 32s-14.3 32-32 32L48 224c-17.7 0-32-14.3-32-32L16 64c0-17.7 14.3-32 32-32s32 14.3 32 32l0 51.2L97.6 97.6c87.5-87.5 229.3-87.5 316.8 0s87.5 229.3 0 316.8s-229.3 87.5-316.8 0c-12.5-12.5-12.5-32.8 0-45.3s32.8-12.5 45.3 0c62.5 62.5 163.8 62.5 226.3 0s62.5-163.8 0-226.3s-163.8-62.5-226.3 0L125.7 160z"],
    redo: [512, 512, "M386.3 160L336 160c-17.7 0-32 14.3-32 32s14.3 32 32 32l128 0c17.7 0 32-14.3 32-32l0-128c0-17.7-14.3-32-32-32s-32 14.3-32 32l0 51.2L414.4 97.6c-87.5-87.5-229.3-87.5-316.8 0s-87.5 229.3 0 316.8s229.3 87.5 316.8 0c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0c-62.5 62.5-163.8 62.5-226.3 0s-62.5-163.8 0-226.3s163.8-62.5 226.3 0L386.3 160z"],
    circleXmark: [512, 512, "M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM175 175c9.4-9.4 24.6-9.4 33.9 0l47 47 47-47c9.4-9.4 24.6-9.4 33.9 0s9.4 24.6 0 33.9l-47 47 47 47c9.4 9.4 9.4 24.6 0 33.9s-24.6 9.4-33.9 0l-47-47-47 47c-9.4 9.4-24.6 9.4-33.9 0s-9.4-24.6 0-33.9l47-47-47-47c-9.4-9.4-9.4-24.6 0-33.9z"],
    floppyDisk: [448, 512, "M64 32C28.7 32 0 60.7 0 96L0 416c0 35.3 28.7 64 64 64l320 0c35.3 0 64-28.7 64-64l0-242.7c0-17-6.7-33.3-18.7-45.3L352 50.7C340 38.7 323.7 32 306.7 32L64 32zm0 96c0-17.7 14.3-32 32-32l192 0c17.7 0 32 14.3 32 32l0 64c0 17.7-14.3 32-32 32L96 224c-17.7 0-32-14.3-32-32l0-64zM224 288a64 64 0 1 1 0 128 64 64 0 1 1 0-128z"],
    paperPlane: [512, 512, "M498.1 5.6c10.1 7 15.4 19.1 13.5 31.2l-64 416c-1.5 9.7-7.4 18.2-16 23s-18.9 5.4-28 1.6L284 427.7l-68.5 74.1c-8.9 9.7-22.9 12.9-35.2 8.1S160 493.2 160 480l0-83.6c0-4 1.5-7.8 4.2-10.8L331.8 202.8c5.8-6.3 5.6-16-.4-22s-15.7-6.4-22-.7L106 360.8 17.7 316.6C7.1 311.3 .3 300.7 0 288.9s5.9-22.8 16.1-28.7l448-256c10.7-6.1 23.9-5.5 34 1.4z"],
    trashCan: [448, 512, "M135.2 17.7C140.6 6.8 151.7 0 163.8 0L284.2 0c12.1 0 23.2 6.8 28.6 17.7L320 32l96 0c17.7 0 32 14.3 32 32s-14.3 32-32 32L32 96C14.3 96 0 81.7 0 64S14.3 32 32 32l96 0 7.2-14.3zM32 128l384 0 0 320c0 35.3-28.7 64-64 64L96 512c-35.3 0-64-28.7-64-64l0-320zm96 64c-8.8 0-16 7.2-16 16l0 224c0 8.8 7.2 16 16 16s16-7.2 16-16l0-224c0-8.8-7.2-16-16-16zm96 0c-8.8 0-16 7.2-16 16l0 224c0 8.8 7.2 16 16 16s16-7.2 16-16l0-224c0-8.8-7.2-16-16-16zm96 0c-8.8 0-16 7.2-16 16l0 224c0 8.8 7.2 16 16 16s16-7.2 16-16l0-224c0-8.8-7.2-16-16-16z"],
    angleUp: [448, 512, "M201.4 137.4c12.5-12.5 32.8-12.5 45.3 0l160 160c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0L224 205.3 86.6 342.6c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3l160-160z"],
    angleDown: [448, 512, "M201.4 374.6c12.5 12.5 32.8 12.5 45.3 0l160-160c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L224 306.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l160 160z"],
    upload: [512, 512, "M288 109.3L288 352c0 17.7-14.3 32-32 32s-32-14.3-32-32l0-242.7-73.4 73.4c-12.5 12.5-32.8 12.5-45.3 0s-12.5-32.8 0-45.3l128-128c12.5-12.5 32.8-12.5 45.3 0l128 128c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0L288 109.3zM64 352l128 0c0 35.3 28.7 64 64 64s64-28.7 64-64l128 0c35.3 0 64 28.7 64 64l0 32c0 35.3-28.7 64-64 64L64 512c-35.3 0-64-28.7-64-64l0-32c0-35.3 28.7-64 64-64zM432 456a24 24 0 1 0 0-48 24 24 0 1 0 0 48z"],
    rotate: [512, 512, "M142.9 142.9c-17.5 17.5-30.1 38-37.8 59.8c-5.9 16.7-24.2 25.4-40.8 19.5s-25.4-24.2-19.5-40.8C55.6 150.7 73.2 122 97.6 97.6c87.2-87.2 228.3-87.5 315.8-1L455 55c6.9-6.9 17.2-8.9 26.2-5.2s14.8 12.5 14.8 22.2l0 128c0 13.3-10.7 24-24 24l-8.4 0c0 0 0 0 0 0L344 224c-9.7 0-18.5-5.8-22.2-14.8s-1.7-19.3 5.2-26.2l41.1-41.1c-62.6-61.5-163.1-61.2-225.3 1zM16 312c0-13.3 10.7-24 24-24l7.6 0 .7 0L168 288c9.7 0 18.5 5.8 22.2 14.8s1.7 19.3-5.2 26.2l-41.1 41.1c62.6 61.5 163.1 61.2 225.3-1c17.5-17.5 30.1-38 37.8-59.8c5.9-16.7 24.2-25.4 40.8-19.5s25.4 24.2 19.5 40.8c-10.8 30.6-28.4 59.3-52.9 83.8c-87.2 87.2-228.3 87.5-315.8 1L57 457c-6.9 6.9-17.2 8.9-26.2 5.2S16 449.7 16 440l0-119.6 0-.7 0-7.6z"],
    folderOpen: [576, 512, "M88.7 223.8L0 375.8 0 96C0 60.7 28.7 32 64 32l117.5 0c17 0 33.3 6.7 45.3 18.7l26.5 26.5c12 12 28.3 18.7 45.3 18.7L416 96c35.3 0 64 28.7 64 64l0 32-336 0c-22.8 0-43.8 12.1-55.3 31.8zm27.6 16.1C122.1 230 132.6 224 144 224l400 0c11.5 0 22 6.1 27.7 16.1s5.7 22.2-.1 32.1l-112 192C453.9 474 443.4 480 432 480L32 480c-11.5 0-22-6.1-27.7-16.1s-5.7-22.2 .1-32.1l112-192z"],
    copy: [448, 512, "M208 0L332.1 0c12.7 0 24.9 5.1 33.9 14.1l67.9 67.9c9 9 14.1 21.2 14.1 33.9L448 336c0 26.5-21.5 48-48 48l-192 0c-26.5 0-48-21.5-48-48l0-288c0-26.5 21.5-48 48-48zM48 128l80 0 0 64-64 0 0 256 192 0 0-32 64 0 0 48c0 26.5-21.5 48-48 48L48 512c-26.5 0-48-21.5-48-48L0 176c0-26.5 21.5-48 48-48z"],
    plus: [448, 512, "M256 80c0-17.7-14.3-32-32-32s-32 14.3-32 32l0 144L48 224c-17.7 0-32 14.3-32 32s14.3 32 32 32l144 0 0 144c0 17.7 14.3 32 32 32s32-14.3 32-32l0-144 144 0c17.7 0 32-14.3 32-32s-14.3-32-32-32l-144 0 0-144z"],
    ghost: [384, 512, "M40.1 467.1l-11.2 9c-3.2 2.5-7.1 3.9-11.1 3.9C8 480 0 472 0 462.2L0 192C0 86 86 0 192 0S384 86 384 192l0 270.2c0 9.8-8 17.8-17.8 17.8c-4 0-7.9-1.4-11.1-3.9l-11.2-9c-13.4-10.7-32.8-9-44.1 3.9L269.3 506c-3.3 3.8-8.2 6-13.3 6s-9.9-2.2-13.3-6l-26.6-30.5c-12.7-14.6-35.4-14.6-48.2 0L141.3 506c-3.3 3.8-8.2 6-13.3 6s-9.9-2.2-13.3-6L84.2 471c-11.3-12.9-30.7-14.6-44.1-3.9zM160 192a32 32 0 1 0 -64 0 32 32 0 1 0 64 0zm96 32a32 32 0 1 0 0-64 32 32 0 1 0 0 64z"],
    play: [384, 512, "M73 39c-14.8-9.1-33.4-9.4-48.5-.9S0 62.6 0 80L0 432c0 17.4 9.4 33.4 24.5 41.9s33.7 8.1 48.5-.9L361 297c14.3-8.7 23-24.2 23-41s-8.7-32.2-23-41L73 39z"],
    stop: [384, 512, "M0 128C0 92.7 28.7 64 64 64H320c35.3 0 64 28.7 64 64V384c0 35.3-28.7 64-64 64H64c-35.3 0-64-28.7-64-64V128z"],
    magnifyingGlass: [512, 512, "M416 208c0 45.9-14.9 88.3-40 122.7L502.6 457.4c12.5 12.5 12.5 32.8 0 45.3s-32.8 12.5-45.3 0L330.7 376c-34.4 25.2-76.8 40-122.7 40C93.1 416 0 322.9 0 208S93.1 0 208 0S416 93.1 416 208zM208 352a144 144 0 1 0 0-288 144 144 0 1 0 0 288z"]
  };

  function iconSvg(name) {
    var def = FA[name];
    if (!def) return "";
    return '<svg class="fa-ico" viewBox="0 0 ' + def[0] + ' ' + def[1] + '" aria-hidden="true" focusable="false"><path fill="currentColor" d="' + def[2] + '"/></svg>';
  }

  function applyIcons(root) {
    (root || document).querySelectorAll("[data-icon]").forEach(function (el) {
      el.innerHTML = iconSvg(el.dataset.icon);
    });
  }

  var W = 64, H = 16;
  var CELL = 12; // internal canvas px per grid cell
  var MAX_HISTORY = 50;

  var AUTOSAVE_KEY = "nc_led_studio_autosave_v1";
  var PROJECTS_KEY = "nc_led_studio_projects_v1";

  // ---------- state ----------
  function blankGrid() { return new Array(W * H).fill(null); }

  var state = {
    mode: "image",
    name: "My Design",
    color: "#ff2d2d",
    tool: "pencil",
    image: { pixels: blankGrid() },
    anim: {
      frames: [{ pixels: blankGrid(), delay: 120 }],
      current: 0,
      ghost: false,
      playing: false,
      playTimer: null
    }
  };

  var undoStack = [];
  var redoStack = [];

  // ---------- dom ----------
  var $ = function (id) { return document.getElementById(id); };
  var panel = $("panel");
  var ctx = panel.getContext("2d");
  var statusText = $("statusText");
  var nameInput = $("nameInput");
  var colorInput = $("colorInput");
  var swatch = $("swatch");
  var animBar = $("animBar");
  var framesStrip = $("framesStrip");
  var frameLabel = $("frameLabel");
  var speedInput = $("speedInput");
  var ghostToggle = $("ghostToggle");
  var playBtn = $("playBtn");
  var openDialog = $("openDialog");
  var savedList = $("savedList");

  function setStatus(msg) { statusText.textContent = msg; }

  // ---------- active pixel helpers ----------
  function activePixels() {
    if (state.mode === "image") return state.image.pixels;
    return state.anim.frames[state.anim.current].pixels;
  }
  function setActivePixels(px) {
    if (state.mode === "image") state.image.pixels = px;
    else state.anim.frames[state.anim.current].pixels = px;
  }
  function clonePixels(px) { return px.slice(); }

  // ---------- undo/redo ----------
  function resetHistory() { undoStack = []; redoStack = []; updateUndoRedoButtons(); }
  function snapshotBeforeEdit() {
    undoStack.push(clonePixels(activePixels()));
    if (undoStack.length > MAX_HISTORY) undoStack.shift();
    redoStack = [];
    updateUndoRedoButtons();
  }
  function undo() {
    if (!undoStack.length) return;
    redoStack.push(clonePixels(activePixels()));
    setActivePixels(undoStack.pop());
    renderAll();
    updateUndoRedoButtons();
    autosave();
  }
  function redo() {
    if (!redoStack.length) return;
    undoStack.push(clonePixels(activePixels()));
    setActivePixels(redoStack.pop());
    renderAll();
    updateUndoRedoButtons();
    autosave();
  }
  function updateUndoRedoButtons() {
    $("undoBtn").disabled = undoStack.length === 0;
    $("redoBtn").disabled = redoStack.length === 0;
    $("undoBtn").style.opacity = undoStack.length ? 1 : 0.4;
    $("redoBtn").style.opacity = redoStack.length ? 1 : 0.4;
  }

  // ---------- drawing primitives ----------
  function idx(x, y) { return y * W + x; }
  function inBounds(x, y) { return x >= 0 && x < W && y >= 0 && y < H; }

  function setPixel(px, x, y, color) {
    if (!inBounds(x, y)) return;
    px[idx(x, y)] = color;
  }

  function floodFill(px, x, y, color) {
    if (!inBounds(x, y)) return;
    var target = px[idx(x, y)];
    if (target === color) return;
    var stack = [[x, y]];
    var seen = new Uint8Array(W * H);
    while (stack.length) {
      var p = stack.pop();
      var cx = p[0], cy = p[1];
      if (!inBounds(cx, cy)) continue;
      var i = idx(cx, cy);
      if (seen[i]) continue;
      if (px[i] !== target) continue;
      seen[i] = 1;
      px[i] = color;
      stack.push([cx + 1, cy]);
      stack.push([cx - 1, cy]);
      stack.push([cx, cy + 1]);
      stack.push([cx, cy - 1]);
    }
  }

  function lineCells(x0, y0, x1, y1) {
    var pts = [];
    var dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
    var sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    var err = dx + dy;
    while (true) {
      pts.push([x0, y0]);
      if (x0 === x1 && y0 === y1) break;
      var e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
    return pts;
  }

  // ---------- rendering ----------
  function renderGridCanvas(targetCtx, px, w, h, cell, opts) {
    opts = opts || {};
    targetCtx.fillStyle = "#000";
    targetCtx.fillRect(0, 0, w * cell, h * cell);

    if (opts.ghostPixels) {
      for (var gy = 0; gy < h; gy++) {
        for (var gx = 0; gx < w; gx++) {
          var gc = opts.ghostPixels[idx(gx, gy)];
          if (!gc) continue;
          targetCtx.globalAlpha = 0.25;
          drawDot(targetCtx, gx, gy, cell, gc);
        }
      }
      targetCtx.globalAlpha = 1;
    }

    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var c = px[idx(x, y)];
        drawDot(targetCtx, x, y, cell, c || "#161616");
      }
    }
  }

  function drawDot(targetCtx, x, y, cell, color) {
    var cx = x * cell + cell / 2;
    var cy = y * cell + cell / 2;
    var r = cell * 0.38;
    targetCtx.beginPath();
    targetCtx.arc(cx, cy, r, 0, Math.PI * 2);
    targetCtx.fillStyle = color;
    targetCtx.fill();
  }

  function renderMainCanvas() {
    var ghost = null;
    if (state.mode === "animation" && state.anim.ghost) {
      var prevIndex = state.anim.current - 1;
      if (prevIndex >= 0) ghost = state.anim.frames[prevIndex].pixels;
    }
    renderGridCanvas(ctx, activePixels(), W, H, CELL, { ghostPixels: ghost });
  }

  function renderFramesStrip() {
    framesStrip.innerHTML = "";
    state.anim.frames.forEach(function (frame, i) {
      var card = document.createElement("div");
      card.className = "frame-card" + (i === state.anim.current ? " active" : "");

      var main = document.createElement("div");
      main.className = "frame-card-main";

      var thumbCanvas = document.createElement("canvas");
      thumbCanvas.className = "frame-thumb";
      thumbCanvas.width = W * 3;
      thumbCanvas.height = H * 3;
      var thumbCtx = thumbCanvas.getContext("2d");
      renderGridCanvas(thumbCtx, frame.pixels, W, H, 3, {});
      main.appendChild(thumbCanvas);

      var meta = document.createElement("div");
      meta.className = "frame-meta";

      var idxLabel = document.createElement("div");
      idxLabel.className = "frame-index";
      idxLabel.textContent = "Frame " + (i + 1);
      meta.appendChild(idxLabel);

      var delayWrap = document.createElement("label");
      delayWrap.className = "frame-delay-wrap";
      delayWrap.textContent = "Delay ";
      var delayInput = document.createElement("input");
      delayInput.type = "number";
      delayInput.className = "frame-delay";
      delayInput.min = "20";
      delayInput.max = "5000";
      delayInput.step = "10";
      delayInput.value = frame.delay;
      delayInput.title = "Delay (ms)";
      delayInput.addEventListener("click", function (e) { e.stopPropagation(); });
      delayInput.addEventListener("change", function () {
        var v = parseInt(delayInput.value, 10);
        frame.delay = isNaN(v) ? 120 : Math.max(20, Math.min(5000, v));
        delayInput.value = frame.delay;
        autosave();
      });
      delayWrap.appendChild(delayInput);
      delayWrap.appendChild(document.createTextNode(" ms"));
      meta.appendChild(delayWrap);

      main.appendChild(meta);
      card.appendChild(main);

      var controls = document.createElement("div");
      controls.className = "frame-controls";

      controls.appendChild(makeFrameBtn("plus", "Add frame below (blank)", function (e) {
        e.stopPropagation();
        state.anim.frames.splice(i + 1, 0, { pixels: blankGrid(), delay: frame.delay });
        state.anim.current = i + 1;
        resetHistory();
        renderAll();
        autosave();
      }));

      controls.appendChild(makeFrameBtn("copy", "Duplicate below", function (e) {
        e.stopPropagation();
        state.anim.frames.splice(i + 1, 0, { pixels: clonePixels(frame.pixels), delay: frame.delay });
        state.anim.current = i + 1;
        resetHistory();
        renderAll();
        autosave();
      }));

      var delBtn = makeFrameBtn("trashCan", "Delete frame", function (e) {
        e.stopPropagation();
        if (state.anim.frames.length <= 1) { setStatus("An animation needs at least one frame."); return; }
        state.anim.frames.splice(i, 1);
        if (state.anim.current >= state.anim.frames.length) state.anim.current = state.anim.frames.length - 1;
        resetHistory();
        renderAll();
        autosave();
      });
      if (state.anim.frames.length <= 1) delBtn.disabled = true;
      controls.appendChild(delBtn);

      var upBtn = makeFrameBtn("angleUp", "Move up", function (e) {
        e.stopPropagation();
        if (i === 0) return;
        var tmp = state.anim.frames[i - 1];
        state.anim.frames[i - 1] = state.anim.frames[i];
        state.anim.frames[i] = tmp;
        state.anim.current = i - 1;
        renderAll();
        autosave();
      });
      upBtn.disabled = i === 0;
      controls.appendChild(upBtn);

      var downBtn = makeFrameBtn("angleDown", "Move down", function (e) {
        e.stopPropagation();
        if (i === state.anim.frames.length - 1) return;
        var tmp2 = state.anim.frames[i + 1];
        state.anim.frames[i + 1] = state.anim.frames[i];
        state.anim.frames[i] = tmp2;
        state.anim.current = i + 1;
        renderAll();
        autosave();
      });
      downBtn.disabled = i === state.anim.frames.length - 1;
      controls.appendChild(downBtn);

      card.appendChild(controls);

      card.addEventListener("click", function () {
        state.anim.current = i;
        resetHistory();
        renderAll();
      });

      framesStrip.appendChild(card);
    });

    frameLabel.textContent = "Frame " + (state.anim.current + 1) + " of " + state.anim.frames.length;
  }

  function makeFrameBtn(iconName, title, onClick) {
    var b = document.createElement("button");
    b.innerHTML = iconSvg(iconName);
    b.title = title;
    b.addEventListener("click", onClick);
    return b;
  }

  function renderAll() {
    renderMainCanvas();
    if (state.mode === "animation") renderFramesStrip();
  }

  // ---------- pointer / drawing interaction ----------
  var drawing = false;
  var lastCell = null;

  function cellFromEvent(e) {
    var rect = panel.getBoundingClientRect();
    var sx = panel.width / rect.width;
    var sy = panel.height / rect.height;
    var px = (e.clientX - rect.left) * sx;
    var py = (e.clientY - rect.top) * sy;
    var x = Math.floor(px / CELL);
    var y = Math.floor(py / CELL);
    return [x, y];
  }

  function paintAt(x, y) {
    var px = activePixels();
    if (state.tool === "pencil") setPixel(px, x, y, state.color);
    else if (state.tool === "eraser") setPixel(px, x, y, null);
  }

  panel.addEventListener("pointerdown", function (e) {
    if (state.anim.playing) return;
    panel.setPointerCapture(e.pointerId);
    var c = cellFromEvent(e);
    var x = c[0], y = c[1];
    if (!inBounds(x, y)) return;

    if (state.tool === "fill") {
      snapshotBeforeEdit();
      floodFill(activePixels(), x, y, state.color);
      renderAll();
      autosave();
      return;
    }
    if (state.tool === "eyedropper") {
      var picked = activePixels()[idx(x, y)];
      if (picked) {
        state.color = picked;
        colorInput.value = picked;
        updateSwatch();
      }
      return;
    }

    drawing = true;
    lastCell = [x, y];
    snapshotBeforeEdit();
    paintAt(x, y);
    renderMainCanvas();
  });

  panel.addEventListener("pointermove", function (e) {
    if (!drawing || state.anim.playing) return;
    var c = cellFromEvent(e);
    var x = c[0], y = c[1];
    if (!inBounds(x, y)) return;
    if (lastCell) {
      var pts = lineCells(lastCell[0], lastCell[1], x, y);
      for (var i = 0; i < pts.length; i++) paintAt(pts[i][0], pts[i][1]);
    } else {
      paintAt(x, y);
    }
    lastCell = [x, y];
    renderMainCanvas();
  });

  function endStroke(e) {
    if (drawing) {
      drawing = false;
      lastCell = null;
      autosave();
    }
    try { panel.releasePointerCapture(e.pointerId); } catch (err) {}
  }
  panel.addEventListener("pointerup", endStroke);
  panel.addEventListener("pointercancel", endStroke);
  panel.addEventListener("pointerleave", function (e) { if (drawing) endStroke(e); });

  // ---------- toolbar ----------
  document.querySelectorAll(".tool[data-tool]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      document.querySelectorAll(".tool[data-tool]").forEach(function (b) { b.classList.remove("active"); });
      btn.classList.add("active");
      state.tool = btn.dataset.tool;
    });
  });

  $("undoBtn").addEventListener("click", undo);
  $("redoBtn").addEventListener("click", redo);
  $("clearBtn").addEventListener("click", function () {
    snapshotBeforeEdit();
    setActivePixels(blankGrid());
    renderAll();
    autosave();
  });

  function updateSwatch() { swatch.style.background = state.color; }
  colorInput.addEventListener("input", function () {
    state.color = colorInput.value;
    updateSwatch();
  });
  updateSwatch();

  document.addEventListener("keydown", function (e) {
    var mod = e.ctrlKey || e.metaKey;
    if (!mod) return;
    if (e.key === "z" && !e.shiftKey) { e.preventDefault(); undo(); }
    else if (e.key === "y" || (e.key === "z" && e.shiftKey)) { e.preventDefault(); redo(); }
  });

  // ---------- tabs ----------
  document.querySelectorAll(".tab[data-mode]").forEach(function (btn) {
    btn.addEventListener("click", function () {
      stopPlayback();
      document.querySelectorAll(".tab[data-mode]").forEach(function (b) {
        b.classList.remove("active");
        b.setAttribute("aria-selected", "false");
      });
      btn.classList.add("active");
      btn.setAttribute("aria-selected", "true");
      state.mode = btn.dataset.mode;
      animBar.classList.toggle("hidden", state.mode !== "animation");
      resetHistory();
      renderAll();
    });
  });

  // ---------- animation controls ----------
  speedInput.addEventListener("change", function () {
    var v = parseInt(speedInput.value, 10);
    if (isNaN(v)) v = 120;
    v = Math.max(20, Math.min(5000, v));
    speedInput.value = v;
    state.anim.frames[state.anim.current].delay = v;
    renderFramesStrip();
    autosave();
  });

  ghostToggle.addEventListener("change", function () {
    state.anim.ghost = ghostToggle.checked;
    renderMainCanvas();
  });

  var playIcon = playBtn.querySelector('[data-icon]');
  var playLabel = $("playLabel");

  function stopPlayback() {
    if (state.anim.playTimer) { clearTimeout(state.anim.playTimer); state.anim.playTimer = null; }
    state.anim.playing = false;
    playIcon.dataset.icon = "play";
    applyIcons(playBtn);
    playLabel.textContent = "Play";
  }

  playBtn.addEventListener("click", function () {
    if (state.anim.playing) { stopPlayback(); return; }
    if (state.anim.frames.length < 2) { setStatus("Add more than one frame to preview playback."); return; }
    state.anim.playing = true;
    playIcon.dataset.icon = "stop";
    applyIcons(playBtn);
    playLabel.textContent = "Stop";
    var step = function () {
      renderGridCanvas(ctx, state.anim.frames[state.anim.current].pixels, W, H, CELL, {});
      var delay = state.anim.frames[state.anim.current].delay || 120;
      state.anim.playTimer = setTimeout(function () {
        state.anim.current = (state.anim.current + 1) % state.anim.frames.length;
        if (state.anim.playing) step();
      }, delay);
    };
    step();
  });

  // ---------- name / new ----------
  nameInput.addEventListener("input", function () {
    state.name = nameInput.value || "My Design";
    autosave();
  });

  $("newBtn").addEventListener("click", function () {
    if (!confirm("Start a new design? Unsaved changes in the current design will be lost (saved designs in Open are not affected).")) return;
    stopPlayback();
    state.name = "My Design";
    nameInput.value = state.name;
    state.image.pixels = blankGrid();
    state.anim.frames = [{ pixels: blankGrid(), delay: 120 }];
    state.anim.current = 0;
    resetHistory();
    renderAll();
    autosave();
    setStatus("New design started.");
  });

  // ---------- upload image / GIF (trim, position, zoom, rotate) ----------
  var MAX_IMPORT_FRAMES = 40; // matches the Android app's MAX_ANIM_FRAMES

  var uploadFileInput = $("uploadFileInput");
  $("uploadBtn").addEventListener("click", function () { uploadFileInput.click(); });
  uploadFileInput.addEventListener("change", function () {
    var file = uploadFileInput.files && uploadFileInput.files[0];
    uploadFileInput.value = "";
    if (!file) return;

    var reader = new FileReader();
    reader.onload = function () {
      var buf = reader.result;
      var bytes = new Uint8Array(buf);
      var isGif = bytes.length > 3 && bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46; // "GIF"

      if (isGif) {
        var decoded = null;
        try { decoded = decodeGifFrames(bytes); } catch (e) { decoded = null; }
        if (decoded && decoded.frames.length > 1) {
          startGifImport(decoded);
          return;
        }
      }

      // Single still image (or a GIF we couldn't multi-frame decode / only has one frame).
      var img = new Image();
      var url = URL.createObjectURL(file);
      img.onload = function () {
        URL.revokeObjectURL(url);
        openCropDialog([{ source: img, delay: 1000 }]);
      };
      img.onerror = function () { URL.revokeObjectURL(url); setStatus("Could not load that image file."); };
      img.src = url;
    };
    reader.onerror = function () { setStatus("Could not read that file."); };
    reader.readAsArrayBuffer(file);
  });

  function toHex(n) { var h = n.toString(16); return h.length === 1 ? "0" + h : h; }

  // ---- GIF decoding (vendor/omggif.js) with frame-disposal compositing ----
  function decodeGifFrames(bytes) {
    var reader = new GifReader(bytes);
    var gw = reader.width, gh = reader.height;
    var n = reader.numFrames();
    var buf = new Uint8ClampedArray(gw * gh * 4);
    var frames = [];
    var prevInfo = null;
    var snapshot = null;

    function rectClear(info) {
      for (var y = 0; y < info.height; y++) {
        var row = ((info.y + y) * gw + info.x) * 4;
        buf.fill(0, row, row + info.width * 4);
      }
    }
    function rectSnapshot(info) {
      var out = new Uint8ClampedArray(info.width * info.height * 4);
      for (var y = 0; y < info.height; y++) {
        var srcOff = ((info.y + y) * gw + info.x) * 4;
        out.set(buf.subarray(srcOff, srcOff + info.width * 4), y * info.width * 4);
      }
      return out;
    }
    function rectRestore(info, snap) {
      for (var y = 0; y < info.height; y++) {
        var dstOff = ((info.y + y) * gw + info.x) * 4;
        buf.set(snap.subarray(y * info.width * 4, (y + 1) * info.width * 4), dstOff);
      }
    }

    for (var i = 0; i < n; i++) {
      var info = reader.frameInfo(i);
      if (prevInfo) {
        if (prevInfo.disposal === 2) rectClear(prevInfo);
        else if (prevInfo.disposal === 3 && snapshot) rectRestore(prevInfo, snapshot);
      }
      snapshot = (info.disposal === 3) ? rectSnapshot(info) : null;

      reader.decodeAndBlitFrameRGBA(i, buf);

      var frameCanvas = document.createElement("canvas");
      frameCanvas.width = gw; frameCanvas.height = gh;
      frameCanvas.getContext("2d").putImageData(new ImageData(buf.slice(), gw, gh), 0, 0);
      frames.push({ source: frameCanvas, delay: Math.max(20, (info.delay || 0) * 10) });

      prevInfo = info;
    }
    return { width: gw, height: gh, frames: frames };
  }

  // ---- Trim dialog (for GIFs longer than MAX_IMPORT_FRAMES) ----
  var trimDialog = $("trimDialog");
  var trimInInput = $("trimInInput");
  var trimOutInput = $("trimOutInput");
  var trimInVal = $("trimInVal");
  var trimOutVal = $("trimOutVal");
  var trimCountLabel = $("trimCountLabel");
  var trimTotalFrames = $("trimTotalFrames");
  var trimPreviewCanvas = $("trimPreviewCanvas");
  var trimPreviewCtx = trimPreviewCanvas.getContext("2d");
  var trimPlayBtn = $("trimPlayBtn");
  var trimPreviewFrameNum = $("trimPreviewFrameNum");
  var trimPreviewFrameTotal = $("trimPreviewFrameTotal");
  var trimPending = null; // { decoded }
  var trimPreview = { playing: false, timer: null, frameIndex: 0 };

  function startGifImport(decoded) {
    if (decoded.frames.length <= MAX_IMPORT_FRAMES) {
      openCropDialog(decoded.frames);
      return;
    }
    trimPending = { decoded: decoded };
    var n = decoded.frames.length;
    trimTotalFrames.textContent = n;
    trimInInput.min = 0; trimInInput.max = n - 1; trimInInput.value = 0;
    trimOutInput.min = 0; trimOutInput.max = n - 1; trimOutInput.value = MAX_IMPORT_FRAMES - 1;
    updateTrimLabels();
    stopTrimPreview();
    trimPreview.frameIndex = 0;
    renderTrimPreviewFrame(0);
    trimDialog.showModal();
  }

  function trimTimeAt(index) {
    var t = 0;
    for (var i = 0; i < index; i++) t += trimPending.decoded.frames[i].delay;
    return (t / 1000).toFixed(2);
  }

  function updateTrimLabels() {
    var inIdx = parseInt(trimInInput.value, 10);
    var outIdx = parseInt(trimOutInput.value, 10);
    trimInVal.textContent = trimTimeAt(inIdx) + "s";
    trimOutVal.textContent = trimTimeAt(outIdx + 1) + "s";
    var count = outIdx - inIdx + 1;
    trimCountLabel.textContent = "Selected " + count + " of " + trimPending.decoded.frames.length + " frames";
  }

  // ---- Trim preview (play/pause the clipped in/out range) ----
  function renderTrimPreviewFrame(i) {
    if (!trimPending) return;
    var frames = trimPending.decoded.frames;
    if (i < 0 || i >= frames.length) return;
    var source = frames[i].source;
    var cw = trimPreviewCanvas.width, ch = trimPreviewCanvas.height;
    var c = trimPreviewCtx;
    c.fillStyle = "#000";
    c.fillRect(0, 0, cw, ch);
    var sw = source.width, sh = source.height;
    var scale = Math.min(cw / sw, ch / sh);
    var dw = sw * scale, dh = sh * scale;
    c.drawImage(source, (cw - dw) / 2, (ch - dh) / 2, dw, dh);
    trimPreviewFrameNum.textContent = i + 1;
    trimPreviewFrameTotal.textContent = frames.length;
  }

  function stopTrimPreview() {
    if (trimPreview.timer) { clearTimeout(trimPreview.timer); trimPreview.timer = null; }
    trimPreview.playing = false;
    trimPlayBtn.classList.remove("is-playing");
    trimPlayBtn.innerHTML = iconSvg("play");
  }

  function startTrimPreview() {
    if (!trimPending) return;
    var inIdx = parseInt(trimInInput.value, 10);
    var outIdx = parseInt(trimOutInput.value, 10);
    if (trimPreview.frameIndex < inIdx || trimPreview.frameIndex > outIdx) trimPreview.frameIndex = inIdx;
    trimPreview.playing = true;
    trimPlayBtn.classList.add("is-playing");
    trimPlayBtn.innerHTML = iconSvg("stop");
    var step = function () {
      var curIn = parseInt(trimInInput.value, 10);
      var curOut = parseInt(trimOutInput.value, 10);
      if (trimPreview.frameIndex < curIn || trimPreview.frameIndex > curOut) trimPreview.frameIndex = curIn;
      renderTrimPreviewFrame(trimPreview.frameIndex);
      var delay = trimPending.decoded.frames[trimPreview.frameIndex].delay || 100;
      trimPreview.timer = setTimeout(function () {
        trimPreview.frameIndex++;
        if (trimPreview.frameIndex > curOut) trimPreview.frameIndex = curIn;
        if (trimPreview.playing) step();
      }, delay);
    };
    step();
  }

  trimPlayBtn.addEventListener("click", function () {
    if (trimPreview.playing) stopTrimPreview(); else startTrimPreview();
  });

  trimInInput.addEventListener("input", function () {
    var inIdx = parseInt(trimInInput.value, 10);
    var outIdx = parseInt(trimOutInput.value, 10);
    if (outIdx < inIdx) outIdx = inIdx;
    if (outIdx - inIdx + 1 > MAX_IMPORT_FRAMES) outIdx = inIdx + MAX_IMPORT_FRAMES - 1;
    trimOutInput.value = outIdx;
    updateTrimLabels();
    stopTrimPreview();
    trimPreview.frameIndex = inIdx;
    renderTrimPreviewFrame(inIdx);
  });
  trimOutInput.addEventListener("input", function () {
    var inIdx = parseInt(trimInInput.value, 10);
    var outIdx = parseInt(trimOutInput.value, 10);
    if (inIdx > outIdx) inIdx = outIdx;
    if (outIdx - inIdx + 1 > MAX_IMPORT_FRAMES) inIdx = outIdx - MAX_IMPORT_FRAMES + 1;
    trimInInput.value = inIdx;
    updateTrimLabels();
    stopTrimPreview();
    trimPreview.frameIndex = outIdx;
    renderTrimPreviewFrame(outIdx);
  });
  $("trimCancelBtn").addEventListener("click", function () {
    stopTrimPreview();
    trimPending = null;
    trimDialog.close();
  });
  $("trimContinueBtn").addEventListener("click", function () {
    if (!trimPending) return;
    var inIdx = parseInt(trimInInput.value, 10);
    var outIdx = parseInt(trimOutInput.value, 10);
    var selected = trimPending.decoded.frames.slice(inIdx, outIdx + 1);
    stopTrimPreview();
    trimPending = null;
    trimDialog.close();
    openCropDialog(selected);
  });

  // ---- Position / zoom / rotate dialog ----
  // Preview canvas is drawn at PX_PER_UNIT px per grid unit, with margin around the
  // frame so the image can be dragged partly (or fully) outside it.
  var PX_PER_UNIT = 10;
  var cropCanvas = $("cropCanvas");
  var cropCtx = cropCanvas.getContext("2d");
  var FRAME_W = W * PX_PER_UNIT, FRAME_H = H * PX_PER_UNIT;
  var FRAME_X = (cropCanvas.width - FRAME_W) / 2, FRAME_Y = (cropCanvas.height - FRAME_H) / 2;
  var cropDialog = $("cropDialog");
  var cropHint = $("cropHint");
  var cropZoomInput = $("cropZoom");
  var cropZoomVal = $("cropZoomVal");
  var cropRotateInput = $("cropRotate");
  var cropRotateVal = $("cropRotateVal");
  var cropApplyBtn = $("cropApplyBtn");

  var crop = null; // { frames:[{source,delay}], naturalW, naturalH, coverZoom, zoomPct, rotateDeg, centerX, centerY }

  function cropCoverZoom(nw, nh) { return Math.max(W / nw, H / nh); }
  function sourceSize(source) {
    return [source.naturalWidth || source.width, source.naturalHeight || source.height];
  }

  function openCropDialog(frames) {
    var size = sourceSize(frames[0].source);
    crop = {
      frames: frames,
      naturalW: size[0],
      naturalH: size[1],
      coverZoom: cropCoverZoom(size[0], size[1]),
      zoomPct: 100,
      rotateDeg: 0,
      centerX: W / 2,
      centerY: H / 2
    };
    cropZoomInput.value = 100;
    cropZoomVal.textContent = "100%";
    cropRotateInput.value = 0;
    cropRotateVal.textContent = "0°";
    if (frames.length > 1) {
      cropHint.textContent = "Drag, zoom and rotate apply to all " + frames.length + " imported frames together.";
      cropApplyBtn.textContent = "Apply to " + frames.length + " Frames";
    } else {
      cropHint.textContent = "Drag the image to move it. It doesn't have to fill the frame — zoom out to leave empty space.";
      cropApplyBtn.textContent = "Apply";
    }
    renderCrop();
    cropDialog.showModal();
  }

  function renderCrop() {
    if (!crop) return;
    var c = cropCtx;
    c.clearRect(0, 0, cropCanvas.width, cropCanvas.height);
    c.fillStyle = "#06070a";
    c.fillRect(0, 0, cropCanvas.width, cropCanvas.height);

    var zoom = crop.coverZoom * (crop.zoomPct / 100);
    var drawW = crop.naturalW * zoom * PX_PER_UNIT;
    var drawH = crop.naturalH * zoom * PX_PER_UNIT;
    var cx = FRAME_X + crop.centerX * PX_PER_UNIT;
    var cy = FRAME_Y + crop.centerY * PX_PER_UNIT;

    c.save();
    c.translate(cx, cy);
    c.rotate(crop.rotateDeg * Math.PI / 180);
    c.drawImage(crop.frames[0].source, -drawW / 2, -drawH / 2, drawW, drawH);
    c.restore();

    c.fillStyle = "rgba(6,7,10,0.72)";
    c.fillRect(0, 0, cropCanvas.width, FRAME_Y);
    c.fillRect(0, FRAME_Y + FRAME_H, cropCanvas.width, cropCanvas.height - (FRAME_Y + FRAME_H));
    c.fillRect(0, FRAME_Y, FRAME_X, FRAME_H);
    c.fillRect(FRAME_X + FRAME_W, FRAME_Y, cropCanvas.width - (FRAME_X + FRAME_W), FRAME_H);

    c.strokeStyle = "#3fa9ff";
    c.lineWidth = 2;
    c.strokeRect(FRAME_X + 1, FRAME_Y + 1, FRAME_W - 2, FRAME_H - 2);
  }

  function cropClientToCanvasDelta(dxClient, dyClient) {
    var rect = cropCanvas.getBoundingClientRect();
    var scaleX = cropCanvas.width / rect.width;
    var scaleY = cropCanvas.height / rect.height;
    return [dxClient * scaleX / PX_PER_UNIT, dyClient * scaleY / PX_PER_UNIT];
  }

  var cropDragging = false;
  var cropLastClient = null;

  cropCanvas.addEventListener("pointerdown", function (e) {
    if (!crop) return;
    cropCanvas.setPointerCapture(e.pointerId);
    cropDragging = true;
    cropLastClient = [e.clientX, e.clientY];
  });
  cropCanvas.addEventListener("pointermove", function (e) {
    if (!cropDragging || !crop) return;
    var d = cropClientToCanvasDelta(e.clientX - cropLastClient[0], e.clientY - cropLastClient[1]);
    crop.centerX += d[0];
    crop.centerY += d[1];
    cropLastClient = [e.clientX, e.clientY];
    renderCrop();
  });
  function cropEndDrag(e) {
    cropDragging = false;
    try { cropCanvas.releasePointerCapture(e.pointerId); } catch (err) {}
  }
  cropCanvas.addEventListener("pointerup", cropEndDrag);
  cropCanvas.addEventListener("pointercancel", cropEndDrag);

  cropCanvas.addEventListener("wheel", function (e) {
    if (!crop) return;
    e.preventDefault();
    var next = clamp(parseInt(cropZoomInput.value, 10) - Math.sign(e.deltaY) * 5, 10, 400);
    cropZoomInput.value = next;
    crop.zoomPct = next;
    cropZoomVal.textContent = next + "%";
    renderCrop();
  }, { passive: false });

  function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

  cropZoomInput.addEventListener("input", function () {
    if (!crop) return;
    crop.zoomPct = parseInt(cropZoomInput.value, 10);
    cropZoomVal.textContent = crop.zoomPct + "%";
    renderCrop();
  });

  cropRotateInput.addEventListener("input", function () {
    if (!crop) return;
    crop.rotateDeg = parseInt(cropRotateInput.value, 10);
    cropRotateVal.textContent = crop.rotateDeg + "°";
    renderCrop();
  });

  function normalizeRotation(deg) {
    deg = ((deg + 180) % 360 + 360) % 360 - 180;
    return deg;
  }

  $("cropRotateLeftBtn").addEventListener("click", function () {
    if (!crop) return;
    crop.rotateDeg = normalizeRotation(crop.rotateDeg - 90);
    cropRotateInput.value = crop.rotateDeg;
    cropRotateVal.textContent = crop.rotateDeg + "°";
    renderCrop();
  });
  $("cropRotateRightBtn").addEventListener("click", function () {
    if (!crop) return;
    crop.rotateDeg = normalizeRotation(crop.rotateDeg + 90);
    cropRotateInput.value = crop.rotateDeg;
    cropRotateVal.textContent = crop.rotateDeg + "°";
    renderCrop();
  });

  $("cropResetBtn").addEventListener("click", function () {
    if (!crop) return;
    crop.zoomPct = 100;
    crop.rotateDeg = 0;
    crop.centerX = W / 2;
    crop.centerY = H / 2;
    cropZoomInput.value = 100;
    cropZoomVal.textContent = "100%";
    cropRotateInput.value = 0;
    cropRotateVal.textContent = "0°";
    renderCrop();
  });

  $("cropCancelBtn").addEventListener("click", function () {
    crop = null;
    cropDialog.close();
  });

  function rasterizeCropSource(source) {
    var zoom = crop.coverZoom * (crop.zoomPct / 100);
    var drawW = crop.naturalW * zoom;
    var drawH = crop.naturalH * zoom;

    var off = document.createElement("canvas");
    off.width = W; off.height = H;
    var offCtx = off.getContext("2d");
    offCtx.fillStyle = "#000";
    offCtx.fillRect(0, 0, W, H);
    offCtx.save();
    offCtx.translate(crop.centerX, crop.centerY);
    offCtx.rotate(crop.rotateDeg * Math.PI / 180);
    offCtx.drawImage(source, -drawW / 2, -drawH / 2, drawW, drawH);
    offCtx.restore();
    var data = offCtx.getImageData(0, 0, W, H).data;

    var px = blankGrid();
    for (var y = 0; y < H; y++) {
      for (var x = 0; x < W; x++) {
        var i = (y * W + x) * 4;
        if (data[i] < 8 && data[i + 1] < 8 && data[i + 2] < 8) continue;
        px[idx(x, y)] = "#" + toHex(data[i]) + toHex(data[i + 1]) + toHex(data[i + 2]);
      }
    }
    return px;
  }

  function isFrameBlank(px) { return px.every(function (p) { return !p; }); }

  $("cropApplyBtn").addEventListener("click", function () {
    if (!crop) return;

    if (state.mode === "animation" && crop.frames.length > 1) {
      var newFrames = crop.frames.map(function (f) {
        return { pixels: rasterizeCropSource(f.source), delay: clamp(Math.round(f.delay) || 120, 20, 5000) };
      });
      var insertAt = state.anim.current + 1;
      if (state.anim.frames.length === 1 && isFrameBlank(state.anim.frames[0].pixels)) {
        state.anim.frames = [];
        insertAt = 0;
      }
      state.anim.frames.splice.apply(state.anim.frames, [insertAt, 0].concat(newFrames));
      state.anim.current = insertAt;
      resetHistory();
      renderAll();
      autosave();
      setStatus("Imported " + newFrames.length + " frame(s) from the GIF.");
    } else {
      snapshotBeforeEdit();
      setActivePixels(rasterizeCropSource(crop.frames[0].source));
      renderAll();
      autosave();
      var note = crop.frames.length > 1 ? " Only the first frame was used — switch to the Animation tab to import the full GIF." : "";
      setStatus("Image applied to the grid." + note + " Clean it up with the pencil/eraser.");
    }

    crop = null;
    cropDialog.close();
  });

  // ---------- persistence ----------
  function serializeProject() {
    return {
      name: state.name,
      mode: state.mode,
      image: { pixels: state.image.pixels },
      anim: {
        frames: state.anim.frames.map(function (f) { return { pixels: f.pixels, delay: f.delay }; }),
        current: state.anim.current,
        ghost: state.anim.ghost
      },
      savedAt: new Date().toISOString()
    };
  }

  function loadProject(data) {
    stopPlayback();
    state.name = data.name || "My Design";
    state.mode = data.mode === "animation" ? "animation" : "image";
    state.image.pixels = (data.image && data.image.pixels && data.image.pixels.length === W * H) ? data.image.pixels.slice() : blankGrid();
    var frames = (data.anim && Array.isArray(data.anim.frames) && data.anim.frames.length) ? data.anim.frames : [{ pixels: blankGrid(), delay: 120 }];
    state.anim.frames = frames.map(function (f) {
      var p = (f.pixels && f.pixels.length === W * H) ? f.pixels.slice() : blankGrid();
      return { pixels: p, delay: f.delay || 120 };
    });
    state.anim.current = Math.min((data.anim && data.anim.current) || 0, state.anim.frames.length - 1);
    state.anim.ghost = !!(data.anim && data.anim.ghost);

    nameInput.value = state.name;
    ghostToggle.checked = state.anim.ghost;
    speedInput.value = state.anim.frames[state.anim.current].delay;
    document.querySelectorAll(".tab[data-mode]").forEach(function (b) {
      var on = b.dataset.mode === state.mode;
      b.classList.toggle("active", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    animBar.classList.toggle("hidden", state.mode !== "animation");
    resetHistory();
    renderAll();
  }

  var autosaveTimer = null;
  function autosave() {
    if (autosaveTimer) clearTimeout(autosaveTimer);
    autosaveTimer = setTimeout(function () {
      try { localStorage.setItem(AUTOSAVE_KEY, JSON.stringify(serializeProject())); }
      catch (e) { /* storage full or unavailable - ignore */ }
    }, 250);
  }

  function loadAutosave() {
    try {
      var raw = localStorage.getItem(AUTOSAVE_KEY);
      if (!raw) return false;
      var data = JSON.parse(raw);
      loadProject(data);
      return true;
    } catch (e) { return false; }
  }

  function getSavedProjects() {
    try { return JSON.parse(localStorage.getItem(PROJECTS_KEY) || "[]"); }
    catch (e) { return []; }
  }
  function setSavedProjects(list) {
    try { localStorage.setItem(PROJECTS_KEY, JSON.stringify(list)); }
    catch (e) { setStatus("Could not save - browser storage may be full."); }
  }

  $("saveBtn").addEventListener("click", function () {
    var list = getSavedProjects();
    var proj = serializeProject();
    var existingIdx = list.findIndex(function (p) { return p.name === proj.name; });
    if (existingIdx >= 0) {
      if (!confirm('A saved design named "' + proj.name + '" already exists. Overwrite it?')) return;
      proj.id = list[existingIdx].id;
      list[existingIdx] = proj;
    } else {
      proj.id = (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : String(Date.now());
      list.push(proj);
    }
    setSavedProjects(list);
    setStatus('Saved "' + proj.name + '" to this browser.');
  });

  $("exportJsonBtn").addEventListener("click", function () {
    var proj = serializeProject();
    var blob = new Blob([JSON.stringify(proj, null, 2)], { type: "application/json" });
    downloadBlob(blob, safeFilename(state.name) + ".json");
    setStatus("Project backup downloaded.");
  });

  function safeFilename(name) {
    return (name || "design").replace(/[^a-z0-9_\-]+/gi, "_").slice(0, 60) || "design";
  }

  function downloadBlob(blob, filename) {
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
  }

  // ---------- open dialog ----------
  $("openBtn").addEventListener("click", function () {
    renderSavedList();
    openDialog.showModal();
  });
  $("closeDialogBtn").addEventListener("click", function () { openDialog.close(); });

  function renderSavedList() {
    var list = getSavedProjects();
    savedList.innerHTML = "";
    if (!list.length) {
      var empty = document.createElement("div");
      empty.className = "empty";
      empty.textContent = "No saved designs yet. Use Save to add one.";
      savedList.appendChild(empty);
      return;
    }
    list.slice().reverse().forEach(function (proj) {
      var li = document.createElement("li");

      var left = document.createElement("div");
      var title = document.createElement("div");
      title.textContent = proj.name + "  —  " + (proj.mode === "animation" ? proj.anim.frames.length + " frame(s)" : "image");
      var meta = document.createElement("div");
      meta.className = "meta";
      meta.textContent = proj.savedAt ? new Date(proj.savedAt).toLocaleString() : "";
      left.appendChild(title);
      left.appendChild(meta);
      li.appendChild(left);

      var actions = document.createElement("div");
      actions.className = "row-actions";

      var loadBtn = document.createElement("button");
      loadBtn.textContent = "Load";
      loadBtn.addEventListener("click", function () {
        loadProject(proj);
        autosave();
        openDialog.close();
        setStatus('Loaded "' + proj.name + '".');
      });
      actions.appendChild(loadBtn);

      var delBtn = document.createElement("button");
      delBtn.textContent = "Delete";
      delBtn.addEventListener("click", function () {
        if (!confirm('Delete "' + proj.name + '"? This cannot be undone.')) return;
        var all = getSavedProjects().filter(function (p) { return p.id !== proj.id; });
        setSavedProjects(all);
        renderSavedList();
      });
      actions.appendChild(delBtn);

      li.appendChild(actions);
      savedList.appendChild(li);
    });
  }

  $("importFileBtn").addEventListener("click", function () { $("importFileInput").click(); });
  $("importFileInput").addEventListener("change", function () {
    var file = this.files && this.files[0];
    this.value = "";
    if (!file) return;
    var reader = new FileReader();
    reader.onload = function () {
      try {
        var data = JSON.parse(String(reader.result));
        loadProject(data);
        autosave();
        openDialog.close();
        setStatus('Imported "' + (data.name || "design") + '".');
      } catch (e) {
        alert("That file is not a valid design project.");
      }
    };
    reader.readAsText(file);
  });

  // ---------- GIF export ----------
  function pixelsToNativeCanvas(pixels) {
    var c = document.createElement("canvas");
    c.width = W; c.height = H;
    var cctx = c.getContext("2d");
    cctx.fillStyle = "#000";
    cctx.fillRect(0, 0, W, H);
    for (var y = 0; y < H; y++) {
      for (var x = 0; x < W; x++) {
        var col = pixels[idx(x, y)];
        if (!col) continue;
        cctx.fillStyle = col;
        cctx.fillRect(x, y, 1, 1);
      }
    }
    return c;
  }

  $("exportGifBtn").addEventListener("click", function () {
    var btn = this;
    btn.disabled = true;
    var prevLabel = btn.textContent;
    btn.textContent = "Rendering…";
    setStatus("Encoding GIF…");

    var gif = new GIF({
      workers: 2,
      quality: 10,
      width: W,
      height: H,
      workerScript: "vendor/gif.worker.js"
    });

    if (state.mode === "image") {
      gif.addFrame(pixelsToNativeCanvas(state.image.pixels), { delay: 1000 });
    } else {
      state.anim.frames.forEach(function (f) {
        gif.addFrame(pixelsToNativeCanvas(f.pixels), { delay: f.delay || 120 });
      });
    }

    gif.on("finished", function (blob) {
      downloadBlob(blob, safeFilename(state.name) + ".gif");
      btn.disabled = false;
      btn.textContent = prevLabel;
      setStatus("GIF downloaded — upload it to the Android app.");
    });

    gif.on("abort", function () {
      btn.disabled = false;
      btn.textContent = prevLabel;
      setStatus("GIF export was aborted.");
    });

    gif.render();
  });

  // ---------- init ----------
  applyIcons();
  if (!loadAutosave()) {
    renderAll();
  }
  updateUndoRedoButtons();
  setStatus("Ready.");
})();
