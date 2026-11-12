import fs from "node:fs/promises";
import { FileBlob, SpreadsheetFile } from "@oai/artifact-tool";

const input = await FileBlob.load("/Users/zoujun/Documents/元数据采集_模版.xlsx");
const workbook = await SpreadsheetFile.importXlsx(input);
await fs.mkdir("work/template_preview", { recursive: true });
for (const name of ["表信息", "表字段信息"]) {
  const image = await workbook.render({ sheetName: name, autoCrop: "all", scale: 2, format: "png" });
  await fs.writeFile(`work/template_preview/${name}.png`, new Uint8Array(await image.arrayBuffer()));
}
