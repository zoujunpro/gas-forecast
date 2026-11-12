import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "/Users/zoujun/Documents/元数据采集_模版.xlsx";
const outputDir = "outputs";
const outputPath = `${outputDir}/元数据采集_模版_已填写.xlsx`;
const metadata = JSON.parse(await fs.readFile("work/mysql_metadata.json", "utf8"));

const appNames = {
  auditors: "用户与权限管理",
  lng_report_tasks: "LNG报告任务管理",
  lng_reports: "LNG报告管理",
  market_hotspots: "市场热点管理",
  price_index: "价格指数管理",
  submissions: "数据报送与审核",
  users: "用户管理",
};

const tableDescriptions = {
  auditors: "维护审核人员账号及启用状态。",
  lng_report_tasks: "记录LNG报告生成任务的类型、状态、进度、结果和异常信息。",
  lng_reports: "保存按日期生成的LNG报告提纲、章节、来源和执行信息。",
  market_hotspots: "保存按日期生成的市场热点候选项、摘要、来源和执行信息。",
  price_index: "保存日期对应的加权价格指数及记录创建时间。",
  submissions: "保存用户按日期报送的数据，以及价格、气量和审核结果。",
  users: "维护用户、企业名称、管理员联系电话及更新时间。",
};

const fieldNames = {
  user_id: "用户标识", enabled: "启用状态", updated_at: "更新时间",
  id: "记录标识", task_type: "任务类型", status: "处理状态",
  progress: "处理进度", message: "处理消息", result: "任务结果",
  error_message: "错误信息", created_at: "创建时间", report_date: "报告日期",
  outline: "报告提纲", sections: "报告章节", source: "数据来源",
  execution_time: "执行耗时", candidates: "热点候选项", summary: "热点摘要",
  date: "数据日期", weighted_price: "加权价格", value: "报送值",
  submitted_at: "报送时间", price: "报送价格", volume: "报送气量",
  approved_price: "审核价格", approved_volume: "审核气量", approved_by: "审核人",
  approved_at: "审核时间", company_name: "企业名称", admin_phone: "管理员联系电话",
};

const fieldPurposes = {
  user_id: "标识用户，并用于关联用户、审核人员或报送记录。",
  enabled: "表示审核人员账号是否启用。",
  updated_at: "记录该行最后一次更新时间。",
  id: "唯一标识当前业务记录。",
  task_type: "区分报告任务的业务类型。",
  status: "记录任务、报告或热点生成流程的当前状态。",
  progress: "记录任务完成进度。",
  message: "保存任务处理过程中的提示信息。",
  result: "以JSON结构保存任务执行结果。",
  error_message: "保存任务或生成流程失败时的错误信息。",
  created_at: "记录该行创建时间。",
  report_date: "标识报告或热点内容对应的业务日期。",
  outline: "保存LNG报告的文本提纲。",
  sections: "以JSON结构保存LNG报告章节内容。",
  source: "记录内容生成或数据采集的来源。",
  execution_time: "记录生成流程的执行耗时。",
  candidates: "以JSON结构保存市场热点候选项。",
  summary: "保存市场热点摘要文本。",
  date: "记录价格指数或报送数据对应的日期。",
  weighted_price: "保存计算后的加权价格指数。",
  value: "保存用户提交的通用数值。",
  submitted_at: "记录用户完成报送的时间。",
  price: "保存用户报送的价格。",
  volume: "保存用户报送的气量。",
  approved_price: "保存审核确认后的价格。",
  approved_volume: "保存审核确认后的气量。",
  approved_by: "记录执行审核的用户标识。",
  approved_at: "记录审核完成时间。",
  company_name: "保存用户所属企业名称。",
  admin_phone: "保存企业管理员联系电话。",
};

function structureDescription(column) {
  const parts = [`类型：${column.COLUMN_TYPE}`, column.IS_NULLABLE === "NO" ? "非空" : "可空"];
  if (column.COLUMN_KEY === "PRI") parts.push("主键");
  else if (column.COLUMN_KEY === "UNI") parts.push("唯一索引");
  else if (column.COLUMN_KEY === "MUL") parts.push("普通索引");
  if (column.COLUMN_DEFAULT !== null) parts.push(`默认值：${column.COLUMN_DEFAULT}`);
  if (column.EXTRA) parts.push(column.EXTRA);
  return parts.join("；");
}

const columnsByTable = new Map();
for (const column of metadata.columns) {
  if (!columnsByTable.has(column.TABLE_NAME)) columnsByTable.set(column.TABLE_NAME, []);
  columnsByTable.get(column.TABLE_NAME).push(column);
}

const tableRows = metadata.tables.map((table) => {
  const columns = columnsByTable.get(table.TABLE_NAME) ?? [];
  const sample = columns.slice(0, 5).map((column) => column.COLUMN_NAME).join("、") + (columns.length > 5 ? "等" : "");
  return [
    metadata.server.database_name,
    table.TABLE_NAME,
    appNames[table.TABLE_NAME] ?? "待确认",
    tableDescriptions[table.TABLE_NAME] ?? "根据数据库结构整理，业务含义待确认。",
    `字段示例：${sample}`,
  ];
});

const fieldRows = metadata.columns.map((column) => [
  column.TABLE_NAME,
  column.COLUMN_NAME,
  fieldNames[column.COLUMN_NAME] ?? column.COLUMN_NAME,
  `${fieldPurposes[column.COLUMN_NAME] ?? "记录该字段对应的业务信息。"}${structureDescription(column)}。`,
]);

const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const tableSheet = workbook.worksheets.getItem("表信息");
const fieldSheet = workbook.worksheets.getItem("表字段信息");

tableSheet.getRange(`A2:E${tableRows.length + 1}`).values = tableRows;
fieldSheet.getRange(`A2:D${fieldRows.length + 1}`).values = fieldRows;

const tableBody = tableSheet.getRange(`A2:E${tableRows.length + 1}`);
tableBody.format.font = { name: "Arial", size: 10, color: "#000000" };
tableBody.format.verticalAlignment = "center";
tableBody.format.wrapText = true;
tableBody.format.borders = { preset: "all", style: "thin", color: "#B7B7B7" };
tableSheet.getRange(`A2:C${tableRows.length + 1}`).format.horizontalAlignment = "center";
tableSheet.getRange(`D2:E${tableRows.length + 1}`).format.horizontalAlignment = "left";
tableSheet.getRange(`A2:E${tableRows.length + 1}`).format.rowHeight = 44;
tableSheet.getRange("A:A").format.columnWidth = 22;
tableSheet.getRange("B:B").format.columnWidth = 23;
tableSheet.getRange("C:C").format.columnWidth = 29;
tableSheet.getRange("D:D").format.columnWidth = 42;
tableSheet.getRange("E:E").format.columnWidth = 34;
tableSheet.freezePanes.freezeRows(1);

const fieldBody = fieldSheet.getRange(`A2:D${fieldRows.length + 1}`);
fieldBody.format.font = { name: "Arial", size: 10, color: "#000000" };
fieldBody.format.verticalAlignment = "center";
fieldBody.format.wrapText = true;
fieldBody.format.borders = { preset: "all", style: "thin", color: "#B7B7B7" };
fieldSheet.getRange(`A2:C${fieldRows.length + 1}`).format.horizontalAlignment = "center";
fieldSheet.getRange(`D2:D${fieldRows.length + 1}`).format.horizontalAlignment = "left";
fieldSheet.getRange(`A2:D${fieldRows.length + 1}`).format.rowHeight = 40;
fieldSheet.getRange("A:A").format.columnWidth = 24;
fieldSheet.getRange("B:B").format.columnWidth = 25;
fieldSheet.getRange("C:C").format.columnWidth = 22;
fieldSheet.getRange("D:D").format.columnWidth = 70;
fieldSheet.freezePanes.freezeRows(1);

workbook.recalculate();

const checks = [];
checks.push((await workbook.inspect({
  kind: "table",
  range: `表信息!A1:E${tableRows.length + 1}`,
  include: "values,formulas",
  tableMaxRows: 10,
  tableMaxCols: 5,
  maxChars: 10000,
})).ndjson);
checks.push((await workbook.inspect({
  kind: "table",
  range: `表字段信息!A1:D${fieldRows.length + 1}`,
  include: "values,formulas",
  tableMaxRows: 8,
  tableMaxCols: 4,
  maxChars: 10000,
})).ndjson);
checks.push((await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
})).ndjson);

await fs.mkdir("work/filled_preview", { recursive: true });
for (const [sheetName, range, fileName] of [
  ["表信息", `A1:E${tableRows.length + 1}`, "表信息.png"],
  ["表字段信息", `A1:D${fieldRows.length + 1}`, "表字段信息.png"],
]) {
  const image = await workbook.render({ sheetName, range, scale: 1.4, format: "png" });
  await fs.writeFile(`work/filled_preview/${fileName}`, new Uint8Array(await image.arrayBuffer()));
}

await fs.mkdir(outputDir, { recursive: true });
const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(JSON.stringify({ outputPath, tableRows: tableRows.length, fieldRows: fieldRows.length }));
for (const check of checks) console.log(check);
