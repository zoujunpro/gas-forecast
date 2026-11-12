import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const workbookPath = "outputs/元数据采集_模版_已填写.xlsx";
const metadata = JSON.parse(await fs.readFile("work/mysql_metadata.json", "utf8"));
const input = await FileBlob.load(workbookPath);
const workbook = await SpreadsheetFile.importXlsx(input);

const columnsByTable = new Map();
for (const column of metadata.columns) {
  if (!columnsByTable.has(column.TABLE_NAME)) columnsByTable.set(column.TABLE_NAME, []);
  columnsByTable.get(column.TABLE_NAME).push(column.COLUMN_NAME);
}

for (const table of metadata.tables) {
  const sheetName = table.TABLE_NAME;
  let sheet = workbook.worksheets.getItemOrNullObject(sheetName);
  if (sheet.isNullObject) sheet = workbook.worksheets.add(sheetName);
  const fields = columnsByTable.get(sheetName) ?? [];
  sheet.getRange("A1").values = [["字段名称"]];
  sheet.getRange("A1").format.fill = "#B4D0F5";
  sheet.getRange("A1").format.font = { name: "Arial", size: 11, bold: true, color: "#000000" };
  sheet.getRange("A1").format.horizontalAlignment = "center";
  sheet.getRange("A1").format.verticalAlignment = "center";
  sheet.getRange("A1").format.borders = { preset: "all", style: "thin", color: "#000000" };
  sheet.getRange("A1").format.rowHeight = 28;
  if (fields.length > 0) {
    const body = sheet.getRange(`A2:A${fields.length + 1}`);
    body.values = fields.map((field) => [field]);
    body.format.font = { name: "Arial", size: 10, color: "#000000" };
    body.format.horizontalAlignment = "left";
    body.format.verticalAlignment = "center";
    body.format.borders = { preset: "all", style: "thin", color: "#B7B7B7" };
    body.format.rowHeight = 23;
  }
  sheet.getRange("A:A").format.columnWidth = 34;
  sheet.freezePanes.freezeRows(1);
  sheet.showGridLines = true;
}

workbook.recalculate();
const sheetCheck = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 6000 });
const errorCheck = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
});

await fs.mkdir("work/table_sheets_preview", { recursive: true });
for (const table of metadata.tables) {
  const fields = columnsByTable.get(table.TABLE_NAME) ?? [];
  const preview = await workbook.render({
    sheetName: table.TABLE_NAME,
    range: `A1:A${fields.length + 1}`,
    scale: 1.5,
    format: "png",
  });
  await fs.writeFile(`work/table_sheets_preview/${table.TABLE_NAME}.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(workbookPath);
console.log(sheetCheck.ndjson);
console.log(errorCheck.ndjson);
console.log(JSON.stringify({ output: workbookPath, addedSheets: metadata.tables.map((t) => t.TABLE_NAME) }));
