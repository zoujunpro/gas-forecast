import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
const input = await FileBlob.load("outputs/元数据采集_模版_含分表明细.xlsx");
const workbook = await SpreadsheetFile.importXlsx(input);
console.log((await workbook.inspect({ kind: "sheet", include: "id,name", maxChars: 8000 })).ndjson);
console.log((await workbook.inspect({ kind: "table", range: "submissions!A1:D11", tableMaxRows: 20, tableMaxCols: 4, maxChars: 8000 })).ndjson);
