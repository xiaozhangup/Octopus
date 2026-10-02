"use strict";

const $ = (selector) => document.querySelector(selector);
const model = { events: new Map(), groups: new Map(), sessions: new Map(), sessionIds: new Set(), losses: new Map(), files: [], warnings: [] };
let selectedGroup = null;
let selectedEvent = null;
let viewGroups = [];
let stackWrap = false;
const expanded = { context: true, logs: false };
let listPage = 0;
let occurrencePage = 0;
let importing = false;
const pageSize = 50;

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = String(text);
  return node;
}

function button(text, className, action) {
  const node = element("button", className, text);
  node.type = "button";
  node.addEventListener("click", action);
  return node;
}

const dateFormat = new Intl.DateTimeFormat("zh-CN", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", second: "2-digit", fractionalSecondDigits: 3, hourCycle: "h23" });
function date(time) { return dateFormat.format(time); }
function tableTime(time) {
  const parts = Object.fromEntries(dateFormat.formatToParts(time).map((part) => [part.type, part.value]));
  const node = element("time", "cell-time", `${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`);
  node.title = date(time);
  node.dateTime = new Date(time).toISOString();
  node.append(element("span", "", `.${parts.fractionalSecond}`));
  return node;
}

function plugin(record) { return record.context["plugin.name"] || "服务端 / 未标注插件"; }
function shortType(type) { return type.split(".").at(-1); }
function source(record) {
  const names = { event: "事件监听", task: "调度任务", "plugin.enable": "插件启用", "plugin.disable": "插件停用", "plugin.lifecycle": "插件生命周期", "world.tick": "世界 Tick", log: "普通日志" };
  const value = record.context.source || "log";
  return names[value] || value;
}
function levelBadge(level) { return element("span", `level ${level.toLowerCase()}`, level); }
function isObject(value) { return value !== null && typeof value === "object" && !Array.isArray(value); }
function validTime(time) { return Number.isSafeInteger(time) && time >= 0 && time < 8640000000000000; }

function validate(record) {
  if (!isObject(record) || record.schema !== 1) throw new Error("不支持的 JSONL 格式或 schema 版本");
  if (typeof record.session_id !== "string" || !record.session_id || record.session_id.length > 128) throw new Error("session_id 无效");
  if (record.type === "session") {
    if (typeof record.server !== "string" || typeof record.java !== "string" || !validTime(record.started_at)) throw new Error("会话信息不完整");
    return;
  }
  if (typeof record.id !== "string" || !record.id || record.id.length > 256 || !validTime(record.time)) throw new Error("记录 ID 或时间无效");
  if (record.type === "loss") {
    if (!Number.isSafeInteger(record.count) || record.count <= 0) throw new Error("丢弃记录计数无效");
    return;
  }
  if (!["issue", "occurrence"].includes(record.type)) throw new Error("未知的记录类型");
  if (!/^[a-f0-9]{64}$/.test(record.fingerprint) || record.issue_id !== record.fingerprint) throw new Error("异常指纹无效");
  for (const key of ["logger", "thread", "message", "exception"]) {
    if (typeof record[key] !== "string") throw new Error(`缺少字符串字段 ${key}`);
  }
  if (!["WARN", "ERROR", "FATAL"].includes(record.level)) throw new Error("异常级别无效");
  if (!isObject(record.context) || !Object.values(record.context).every((value) => typeof value === "string")) throw new Error("异常上下文格式无效");
  if (record.type === "issue") {
    if (typeof record.stack !== "string" || typeof record.truncated !== "boolean" || !Array.isArray(record.breadcrumbs) || record.breadcrumbs.length > 100) throw new Error("完整异常样本格式无效");
    for (const entry of record.breadcrumbs) {
      if (!isObject(entry) || !validTime(entry.time) || !["thread", "logger", "level", "message"].every((key) => typeof entry[key] === "string")) throw new Error("前置日志格式无效");
    }
  }
}

function addRecord(record, fileName, definitions) {
  model.sessionIds.add(record.session_id);
  if (record.type === "session") { model.sessions.set(record.session_id, record); return false; }
  const identity = `${record.session_id}:${record.id}`;
  if (record.type === "loss") {
    if (model.losses.has(identity)) return false;
    model.losses.set(identity, record.count);
    return false;
  }
  record._file = fileName;
  record._identity = identity;
  record._search = [record.exception, record.message, record.logger, record.thread, record.id, record.session_id, record.fingerprint, ...Object.entries(record.context).flat()].join("\n").toLowerCase();
  if (record.type === "issue") record._stackSearch = record.stack.toLowerCase();
  if (record.type === "issue") definitions.set(`${record.session_id}:${record.issue_id}`, record);
  else record._fileSample = definitions.get(`${record.session_id}:${record.issue_id}`);
  if (model.events.has(identity)) {
    const existing = model.events.get(identity);
    // A later complete file can supply a sample missing from a partial import.
    if (record._fileSample && !existing._fileSample) existing._fileSample = record._fileSample;
    return false;
  }
  model.events.set(identity, record);
  let group = model.groups.get(record.fingerprint);
  if (!group) {
    group = { id: record.fingerprint, records: [] };
    model.groups.set(group.id, group);
  }
  group.records.push(record);
  return true;
}

async function importFiles(files) {
  if (importing || !files.length) return;
  importing = true;
  $("#import-button").disabled = true;
  $("#clear-button").disabled = true;
  let added = 0;
  let invalid = 0;
  for (const file of files) {
    $("#import-status").textContent = `正在读取 ${file.name}…`;
    if (file.size > 64 * 1024 * 1024) {
      model.warnings.push(`${file.name}：文件超过 64 MiB。Hamster 单个文件通常不超过 16 MiB。`);
      continue;
    }
    const definitions = new Map();
    let valid = 0;
    let lineNumber = 0;
    let shownWarnings = 0;
    let fileInvalid = 0;
    function parseLine(line, tail) {
      lineNumber++;
      if (!line.trim()) return;
      try {
        if (line.length > 512 * 1024) throw new Error("单条记录过长，请检查是否为 Hamster 日志");
        const record = JSON.parse(lineNumber === 1 ? line.replace(/^\uFEFF/, "") : line);
        validate(record);
        if (addRecord(record, file.name, definitions)) added++;
        valid++;
      } catch (error) {
        fileInvalid++;
        invalid++;
        if (shownWarnings++ < 20) model.warnings.push(`${file.name}:${lineNumber} · ${error.message}${tail ? "；末行可能在下载或写入时截断" : ""}`);
      }
    }
    try {
      const reader = file.stream().pipeThrough(new TextDecoderStream()).getReader();
      let buffer = "";
      let oversized = false;
      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += value;
        let newline;
        while ((newline = buffer.indexOf("\n")) !== -1) {
          const line = buffer.slice(0, newline);
          buffer = buffer.slice(newline + 1);
          parseLine(oversized ? " ".repeat(512 * 1024 + 1) + line : line, false);
          oversized = false;
          if (lineNumber % 2000 === 0) await new Promise((resolve) => setTimeout(resolve, 0));
        }
        // An imported file can contain an arbitrarily long malformed line.
        if (buffer.length > 512 * 1024) { buffer = ""; oversized = true; }
      }
      if (buffer.length || oversized) parseLine(oversized ? " ".repeat(512 * 1024 + 1) : buffer, true);
      if (fileInvalid > 20) model.warnings.push(`${file.name}：另有 ${fileInvalid - 20} 条无效记录。`);
      if (valid) model.files.push({ name: file.name, size: file.size, valid });
      else model.warnings.push(`${file.name}：没有可导入的 Hamster v1 记录。`);
    } catch (error) { model.warnings.push(`${file.name}：读取失败 · ${error.message}`); }
  }
  for (const group of model.groups.values()) group.records.sort((a, b) => b.time - a.time || b.id.localeCompare(a.id, undefined, { numeric: true }));
  linkSamples();
  updateFilters();
  render();
  $("#import-status").textContent = `导入完成 · 新增 ${added.toLocaleString()} 次异常${invalid ? ` · 跳过 ${invalid.toLocaleString()} 条无效记录` : ""} · 重复记录不计数`;
  importing = false;
  $("#import-button").disabled = false;
  $("#clear-button").disabled = !model.files.length && !model.warnings.length;
}

function linkSamples() {
  // Resolve samples once after import, including files imported out of order.
  for (const group of model.groups.values()) {
    const samples = new Map();
    for (let i = group.records.length - 1; i >= 0; i--) {
      const record = group.records[i];
      if (record.type === "issue") samples.set(record.session_id, record);
      else record._sample = record._fileSample || samples.get(record.session_id);
    }
  }
}

function updateSelect(id, values, label) {
  const select = $(id);
  const current = select.value;
  select.replaceChildren(new Option(label, ""));
  for (const [value, text] of values) select.add(new Option(text, value));
  select.value = values.some(([value]) => value === current) ? current : "";
}

function updateFilters() {
  const records = [...model.events.values()];
  updateSelect("#plugin-filter", [...new Set(records.map(plugin))].sort().map((name) => [name, name]), "所有插件");
  const sources = new Map(records.map((record) => [record.context.source || "log", source(record)]));
  updateSelect("#source-filter", [...sources].sort((a, b) => a[1].localeCompare(b[1])), "所有来源");
  updateSelect("#session-filter", [...model.sessionIds].map((id) => {
    const session = model.sessions.get(id);
    return [id, `${id.slice(0, 8)} · ${session ? date(session.started_at) : "缺少会话头"}`];
  }), "所有会话");
}

function refreshView() {
  const query = $("#search").value.trim().toLowerCase();
  const pluginName = $("#plugin-filter").value;
  const level = $("#level-filter").value;
  const origin = $("#source-filter").value;
  const session = $("#session-filter").value;
  const from = $("#time-from").value ? new Date($("#time-from").value).getTime() : -Infinity;
  const to = $("#time-to").value ? new Date($("#time-to").value).getTime() + 999 : Infinity;
  const invalid = from > to || Number.isNaN(from) || Number.isNaN(to) || !$("#time-from").validity.valid || !$("#time-to").validity.valid;
  $("#filter-status").textContent = invalid ? "时间范围无效：开始时间应不晚于结束时间。" : "";
  $("#filter-status").className = invalid ? "error" : "";
  viewGroups = [];
  if (!invalid) for (const group of model.groups.values()) {
    const records = group.records.filter((record) =>
      (!pluginName || plugin(record) === pluginName) && (!level || record.level === level) &&
      (!origin || (record.context.source || "log") === origin) && (!session || record.session_id === session) &&
      record.time >= from && record.time <= to &&
      (!query || record._search.includes(query) || (record.type === "issue" ? record._stackSearch : record._sample?._stackSearch)?.includes(query)));
    if (records.length) viewGroups.push({ group, records, id: group.id, latest: records[0], first: records.at(-1) });
  }
  const sort = $("#sort").value;
  viewGroups.sort(sort === "count" ? (a, b) => b.records.length - a.records.length || b.latest.time - a.latest.time : sort === "first" ? (a, b) => a.first.time - b.first.time : (a, b) => b.latest.time - a.latest.time);
  const matches = viewGroups.reduce((sum, view) => sum + view.records.length, 0);
  $("#result-count").textContent = `${viewGroups.length.toLocaleString()} 组 / ${matches.toLocaleString()} 次`;
  const view = viewGroups.find((entry) => entry.id === selectedGroup) || viewGroups[0];
  selectedGroup = view?.id || null;
  if (!view?.records.some((record) => record._identity === selectedEvent)) {
    selectedEvent = view?.latest._identity || null;
    occurrencePage = 0;
  }
  listPage = Math.max(0, Math.floor(viewGroups.findIndex((entry) => entry.id === selectedGroup) / pageSize));
  occurrencePage = view ? Math.max(0, Math.floor(view.records.findIndex((record) => record._identity === selectedEvent) / pageSize)) : 0;
}

function pagination(parent, page, total, action) {
  parent.replaceChildren();
  const pages = Math.ceil(total / pageSize);
  if (pages <= 1) return;
  const row = element("div", "pagination");
  const previous = button("上一页", "", () => action(page - 1));
  const next = button("下一页", "", () => action(page + 1));
  previous.disabled = page === 0;
  next.disabled = page + 1 === pages;
  row.append(element("span", "", `${page * pageSize + 1}–${Math.min(total, (page + 1) * pageSize)} / ${total}`), previous, element("span", "", `${page + 1} / ${pages}`), next);
  parent.append(row);
}

function table(headers, widths) {
  const node = element("table", "data-table");
  const cols = element("colgroup");
  for (const width of widths) { const col = element("col"); if (width) col.style.width = width; cols.append(col); }
  const head = element("thead");
  const row = element("tr");
  for (const text of headers) { const cell = element("th", "", text); cell.scope = "col"; row.append(cell); }
  head.append(row);
  const body = element("tbody");
  node.append(cols, head, body);
  return { node, body };
}

function selectableCell(text, selected) {
  const cell = element("td");
  const control = element("button", "row-select", text);
  control.type = "button";
  control.title = text;
  control.setAttribute("aria-pressed", String(selected));
  cell.append(control);
  return cell;
}

function selectGroup(view) {
  $("#detail-panel").scrollTop = 0;
  selectedGroup = view.id;
  selectedEvent = view.latest._identity;
  occurrencePage = 0;
  renderWorkspace();
}

function render() {
  $("#stat-events").textContent = model.events.size.toLocaleString();
  $("#stat-issues").textContent = model.groups.size.toLocaleString();
  $("#stat-sessions").textContent = model.sessionIds.size.toLocaleString();
  const lost = [...model.losses.values()].reduce((sum, count) => sum + count, 0);
  $("#stat-loss").textContent = lost.toLocaleString();
  $("#stat-loss").className = lost ? "level" : "";
  $("#file-summary").textContent = `文件 (${model.files.length})`;
  $("#file-list").replaceChildren(...model.files.map((file) => element("div", "file-row", `${file.name} · ${(file.size / 1024).toFixed(1)} KiB · ${file.valid} 条有效行`)));
  if (!model.files.length) $("#file-list").append(element("p", "", "未导入文件"));
  $("#warnings").hidden = model.warnings.length === 0;
  $("#warning-summary").textContent = `${model.warnings.length} 条导入提示（有效记录已保留）`;
  $("#warning-list").replaceChildren(...model.warnings.map((message) => element("li", "", message)));
  refreshView();
  renderWorkspace();
}

function renderWorkspace() { renderList(); renderOccurrences(); renderDetail(); }

function renderList() {
  const list = $("#issue-list");
  const { node, body } = table(["异常 / 插件", "级别", "匹配 / 总计", "最近发生"], ["", "55px", "80px", "113px"]);
  for (const view of viewGroups.slice(listPage * pageSize, (listPage + 1) * pageSize)) {
    const record = view.latest;
    const selected = selectedGroup === view.id;
    const row = element("tr", selected ? "selected" : "");
    row.addEventListener("click", () => selectGroup(view));
    const name = selectableCell(shortType(record.exception), selected);
    const subtitle = element("div", "cell-subtitle", plugin(record));
    subtitle.title = `${plugin(record)} · ${source(record)}\n${record.message}`;
    name.append(subtitle);
    const severity = element("td"); severity.append(levelBadge(record.level));
    const counts = element("td", "count-cell", `${view.records.length} / ${view.group.records.length}`);
    counts.title = `匹配记录首次：${date(view.first.time)}\n匹配记录最近：${date(record.time)}`;
    const time = element("td"); time.append(tableTime(record.time));
    row.append(name, severity, counts, time);
    body.append(row);
  }
  list.replaceChildren(node);
  if (!viewGroups.length) list.append(element("p", "empty", model.events.size ? "没有匹配记录，请调整筛选。" : "等待导入 JSONL。"));
  pagination($("#issue-pagination"), listPage, viewGroups.length, (page) => { listPage = page; renderList(); list.scrollTop = 0; });
}

function currentView() { return viewGroups.find((view) => view.id === selectedGroup); }
function sampleFor(record) { return record.type === "issue" ? record : record._sample; }

function renderOccurrences() {
  const view = currentView();
  const list = $("#occurrence-list");
  $("#occurrence-count").textContent = view ? `匹配 ${view.records.length} / 总计 ${view.group.records.length}` : "未选择分组";
  const { node, body } = table(["时间", "级别", "消息 / 世界 · 玩家 · 线程", "样本"], ["113px", "55px", "", "50px"]);
  if (view) {
    occurrencePage = Math.min(occurrencePage, Math.max(0, Math.ceil(view.records.length / pageSize) - 1));
    for (const record of view.records.slice(occurrencePage * pageSize, (occurrencePage + 1) * pageSize)) {
      const selected = record._identity === selectedEvent;
      const row = element("tr", selected ? "selected" : "");
      row.addEventListener("click", () => { selectedEvent = record._identity; $("#detail-panel").scrollTop = 0; renderOccurrences(); renderDetail(); });
      const time = selectableCell("", selected);
      time.firstChild.append(tableTime(record.time));
      time.firstChild.setAttribute("aria-label", `选择 ${date(record.time)} ${record.message}`);
      const severity = element("td"); severity.append(levelBadge(record.level));
      const message = element("td");
      const text = element("div", "cell-subtitle", record.message || "（空消息）"); text.title = record.message;
      const context = [record.context.world, record.context["player.name"], record.thread].filter(Boolean).join(" · ");
      const metadata = element("div", "cell-subtitle", context); metadata.title = context;
      message.append(text, metadata);
      row.append(time, severity, message, element("td", "small", record.type === "issue" ? "完整" : sampleFor(record) ? "引用" : "缺失"));
      body.append(row);
    }
  }
  list.replaceChildren(node);
  if (!view) list.append(element("p", "empty", "选择异常分组。"));
  pagination($("#occurrence-pagination"), occurrencePage, view?.records.length || 0, (page) => { occurrencePage = page; renderOccurrences(); list.scrollTop = 0; });
}

function section(id, title) {
  const node = element("details", "detail-section");
  node.open = expanded[id];
  node.append(element("summary", "", title));
  node.addEventListener("toggle", () => { expanded[id] = node.open; });
  return node;
}

function renderDetail() {
  const panel = $("#detail-panel");
  const view = currentView();
  panel.replaceChildren();
  if (!view) { panel.append(element("p", "empty", model.events.size ? "没有匹配的发生记录。" : "导入文件后选择发生记录，查看消息、堆栈和上下文。")); return; }
  const record = model.events.get(selectedEvent);
  const group = view.group;
  const sample = sampleFor(record);
  const heading = element("div", "detail-heading");
  heading.append(element("h2", "", record.exception), button("导出报告", "", () => exportReport(record, group, sample)));
  const meta = element("div", "detail-meta");
  meta.append(levelBadge(record.level), element("span", "", date(record.time)), element("span", "", plugin(record)), element("span", "", source(record)));
  panel.append(heading, meta, element("pre", "detail-message", record.message || "（空消息）"));
  const stackHeading = element("div", "section-heading");
  const wrap = button(stackWrap ? "取消换行" : "自动换行", "", () => { stackWrap = !stackWrap; stack.classList.toggle("wrap", stackWrap); wrap.textContent = stackWrap ? "取消换行" : "自动换行"; });
  stackHeading.append(element("h3", "", "调用堆栈"), wrap);
  const note = element("p", `sample-note${sample ? "" : " missing"}`, sample ? `${sample._identity === record._identity ? "本次完整样本" : "引用完整样本（不是本次快照）"}：${date(sample.time)} · ${sample._file}${sample.truncated ? " · 堆栈已被服务端截断" : ""}` : "缺少完整样本；请补充导入包含该会话 issue 记录的文件。");
  const stack = element("pre", `stack${stackWrap ? " wrap" : ""}`, sample?.stack || "未导入完整堆栈。");
  panel.append(stackHeading, note, stack);
  const context = section("context", "本次上下文");
  const session = model.sessions.get(record.session_id);
  const values = [["记录时间", date(record.time)], ["日志线程", record.thread], ["Logger", record.logger], ...Object.entries(record.context), ["服务端构建", session?.server || "缺少会话头"], ["Java", session?.java || "缺少会话头"], ["来源文件", record._file], ["启动会话", record.session_id], ["记录 ID", record.id]];
  const contextTable = element("table", "context-table");
  const contextBody = element("tbody");
  for (const [key, value] of values) { const row = element("tr"); const label = element("th", "", key); label.scope = "row"; row.append(label, element("td", "", value)); contextBody.append(row); }
  contextTable.append(contextBody); context.append(contextTable);
  const logs = section("logs", `样本前置日志 (${sample?.breadcrumbs.length || 0})`);
  logs.append(element("p", "sample-note", sample ? `来自 ${date(sample.time)} 样本之前的全服日志窗口，保留各条日志的线程；不代表因果关系。` : "缺少完整样本，无法显示前置日志。"));
  const logTable = table(["时间", "级别", "线程 / Logger", "消息"], []);
  logTable.node.classList.add("log-table");
  for (const entry of sample?.breadcrumbs || []) {
    const row = element("tr");
    const time = element("td"); time.append(tableTime(entry.time));
    const level = element("td"); level.append(levelBadge(entry.level));
    const origin = element("td", "mono", `${entry.thread}\n${entry.logger}`);
    const message = element("td"); message.append(element("pre", "", entry.message));
    row.append(time, level, origin, message); logTable.body.append(row);
  }
  if (logTable.body.children.length) logs.append(logTable.node);
  else logs.append(element("p", "sample-note", "没有可显示的前置日志。"));
  panel.append(context, logs, element("p", "group-id mono", `指纹 ${group.id}`));
}

function exportReport(record, group, sample) {
  const session = model.sessions.get(record.session_id);
  const lines = ["Hamster 异常报告", "", `异常：${record.exception}`, `级别：${record.level}`, `消息：${record.message}`, `时间：${date(record.time)}`, `已导入分组次数：${group.records.length}`, `当前筛选匹配次数：${currentView().records.length}`, `服务端：${session?.server || "缺少会话头"}`, `Java：${session?.java || "缺少会话头"}`, `线程：${record.thread}`, `Logger：${record.logger}`, `来源文件：${record._file}`, `会话：${record.session_id}`, `记录 ID：${record.id}`, `指纹：${group.id}`, "", "本次上下文", ...Object.entries(record.context).map(([key, value]) => `${key}: ${value}`), "", `堆栈样本：${sample ? date(sample.time) + " / " + sample._file : "缺失"}`, `样本记录 ID：${sample?.id || "缺失"}`, `堆栈截断：${sample ? (sample.truncated ? "是" : "否") : "未知"}`, sample?.stack || "未导入完整堆栈", "", "样本前置日志（全服窗口，不代表因果关系）", ...(sample?.breadcrumbs || []).map((entry) => `${date(entry.time)} [${entry.thread}/${entry.level}] [${entry.logger}] ${entry.message}`)];
  const url = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/plain;charset=utf-8" }));
  const link = element("a");
  link.href = url;
  link.download = `hamster-${group.id.slice(0, 12)}.txt`;
  document.body.append(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

$("#import-button").addEventListener("click", () => $("#file-input").click());
$("#file-input").addEventListener("change", async (event) => { await importFiles([...event.target.files]); event.target.value = ""; });
let dragDepth = 0;
for (const name of ["dragenter", "dragover"]) document.addEventListener(name, (event) => {
  if (!event.dataTransfer.types.includes("Files")) return;
  event.preventDefault();
  if (name === "dragenter") dragDepth++;
  $("#drop-overlay").hidden = false;
});
document.addEventListener("dragleave", () => { if (--dragDepth <= 0) { dragDepth = 0; $("#drop-overlay").hidden = true; } });
document.addEventListener("drop", (event) => { event.preventDefault(); dragDepth = 0; $("#drop-overlay").hidden = true; importFiles([...event.dataTransfer.files]); });

function resetFilters() {
  clearTimeout(searchTimer);
  for (const id of ["#search", "#plugin-filter", "#level-filter", "#source-filter", "#session-filter", "#time-from", "#time-to"]) $(id).value = "";
  $("#sort").value = "latest";
  listPage = 0; occurrencePage = 0;
  refreshView(); renderWorkspace();
}
$("#reset-filters").addEventListener("click", resetFilters);
$("#clear-button").addEventListener("click", () => {
  model.events.clear(); model.groups.clear(); model.sessions.clear(); model.sessionIds.clear(); model.losses.clear(); model.files.length = 0; model.warnings.length = 0;
  selectedGroup = null; selectedEvent = null; listPage = 0; occurrencePage = 0;
  updateFilters(); resetFilters(); render();
  $("#files-panel").open = false;
  $("#warnings").open = false;
  $("#clear-button").disabled = true;
  $("#import-status").textContent = "数据已清空。导入或拖入 JSONL 文件继续查看。";
});
let searchTimer;
function applyFilters() { $("#detail-panel").scrollTop = 0; listPage = 0; occurrencePage = 0; refreshView(); renderWorkspace(); }
$("#search").addEventListener("input", () => { clearTimeout(searchTimer); searchTimer = setTimeout(applyFilters, 150); });
for (const id of ["#plugin-filter", "#level-filter", "#source-filter", "#session-filter", "#sort", "#time-from", "#time-to"]) $(id).addEventListener("change", applyFilters);

function navigate(event, kind) {
  if (!["ArrowDown", "ArrowUp"].includes(event.key)) return;
  const entries = kind === "group" ? viewGroups : currentView()?.records || [];
  if (!entries.length) return;
  event.preventDefault();
  const index = entries.findIndex((entry) => kind === "group" ? entry.id === selectedGroup : entry._identity === selectedEvent);
  const next = Math.max(0, Math.min(entries.length - 1, index + (event.key === "ArrowDown" ? 1 : -1)));
  if (kind === "group") { listPage = Math.floor(next / pageSize); selectGroup(entries[next]); }
  else { $("#detail-panel").scrollTop = 0; selectedEvent = entries[next]._identity; occurrencePage = Math.floor(next / pageSize); renderOccurrences(); renderDetail(); }
  const list = kind === "group" ? $("#issue-list") : $("#occurrence-list");
  list.focus({ preventScroll: true });
  list.querySelector("tr.selected")?.scrollIntoView({ block: "nearest" });
}
$("#issue-list").addEventListener("keydown", (event) => navigate(event, "group"));
$("#occurrence-list").addEventListener("keydown", (event) => navigate(event, "record"));
document.addEventListener("keydown", (event) => {
  if (event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey && !event.target.closest("input,select,textarea,button,summary")) { event.preventDefault(); $("#search").focus(); }
  if (event.key === "Escape" && event.target === $("#search")) { clearTimeout(searchTimer); $("#search").value = ""; applyFilters(); }
});
$("#timezone").textContent = new Intl.DateTimeFormat().resolvedOptions().timeZone;
$(".totals").title = "全部已导入文件的统计，不随筛选变化；丢弃数来自服务端队列溢出记录。";
render();
