import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";
const input = await FileBlob.load("/Users/zoujun/Documents/元数据采集_模版.xlsx");
const workbook = await SpreadsheetFile.importXlsx(input);
for (const topic of ["table.range"]) {
  const help = workbook.help(topic, { include: "index,examples,notes", maxChars: 5000 });
  console.log(topic, help.ndjson);
}
