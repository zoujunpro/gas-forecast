import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const inputPath = "outputs/元数据采集_模版_含分表Sheet.xlsx";
const outputPath = "outputs/元数据采集_模版_含分表明细.xlsx";
const metadata = JSON.parse(await fs.readFile("work/mysql_metadata.json", "utf8"));
const input = await FileBlob.load(inputPath);
const workbook = await SpreadsheetFile.importXlsx(input);
const allFieldRows = workbook.worksheets.getItem("表字段信息").getRange("A2:D50").values;
const headers = [["表名", "字段名称", "指标名称", "指标描述"]];

for (const table of metadata.tables) {
  const sheetName = table.TABLE_NAME;
  const sheet = workbook.worksheets.getItem(sheetName);
  const rows = allFieldRows.filter((row) => row[0] === sheetName);
  sheet.getRange("A1:D100").clear({ applyTo: "all" });
  sheet.getRange("A1:D1").values = headers;
  sheet.getRange("A1:D1").format.fill = "#B4D0F5";
  sheet.getRange("A1:D1").format.font = { name: "Arial", size: 11, bold: true, color: "#000000" };
  sheet.getRange("A1:D1").format.horizontalAlignment = "center";
  sheet.getRange("A1:D1").format.verticalAlignment = "center";
  sheet.getRange("A1:D1").format.borders = { preset: "all", style: "thin", color: "#000000" };
  sheet.getRange("A1:D1").format.rowHeight = 28;
  if (rows.length > 0) {
    const body = sheet.getRange(`A2:D${rows.length + 1}`);
    body.values = rows;
    body.format.font = { name: "Arial", size: 10, color: "#000000" };
    body.format.verticalAlignment = "center";
    body.format.wrapText = true;
    body.format.borders = { preset: "all", style: "thin", color: "#B7B7B7" };
    sheet.getRange(`A2:C${rows.length + 1}`).format.horizontalAlignment = "center";
    sheet.getRange(`D2:D${rows.length + 1}`).format.horizontalAlignment = "left";
    body.format.rowHeight = 40;
  }
  sheet.getRange("A:A").format.columnWidth = 24;
  sheet.getRange("B:B").format.columnWidth = 25;
  sheet.getRange("C:C").format.columnWidth = 22;
  sheet.getRange("D:D").format.columnWidth = 70;
  sheet.freezePanes.freezeRows(1);
}

workbook.recalculate();
const errors = await workbook.inspect({
  kind: "match",
  searchTerm: "#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!|#SPILL!|#CALC!",
  options: { useRegex: true, maxResults: 100 },
  summary: "final formula error scan",
});

await fs.mkdir("work/full_table_sheets_preview", { recursive: true });
for (const table of metadata.tables) {
  const rows = allFieldRows.filter((row) => row[0] === table.TABLE_NAME);
  const preview = await workbook.render({
    sheetName: table.TABLE_NAME,
    range: `A1:D${rows.length + 1}`,
    scale: 1.2,
    format: "png",
  });
  await fs.writeFile(`work/full_table_sheets_preview/${table.TABLE_NAME}.png`, new Uint8Array(await preview.arrayBuffer()));
}

const output = await SpreadsheetFile.exportXlsx(workbook);
await output.save(outputPath);
console.log(errors.ndjson);
console.log(JSON.stringify({ output: outputPath, sheets: metadata.tables.length }));
