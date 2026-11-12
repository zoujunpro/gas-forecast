import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
const input = await FileBlob.load("outputs/元数据采集_模版_已填写.xlsx");
const workbook = await SpreadsheetFile.importXlsx(input);
const sheets = await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 10000 });
const fields = await workbook.inspect({ kind: "table", range: "submissions!A1:A11", tableMaxRows: 20, tableMaxCols: 2, maxChars: 5000 });
console.log(sheets.ndjson);
console.log(fields.ndjson);
