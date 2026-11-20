import csv,io,json,zipfile,sys,hashlib
from pathlib import Path
base=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]) if len(sys.argv)>1 else Path('/Users/zoujun/工作/石油/知识图谱/客户销量图谱数据_CSV方案.zip')
nodes=[];edges=[]
with zipfile.ZipFile(source) as z:
 for filename in z.namelist():
  if not filename.endswith('.csv') or filename.startswith('__MACOSX'):continue
  rows=list(csv.DictReader(io.StringIO(z.read(filename).decode('utf-8-sig'))))
  if filename=='relationships.csv':
   edges=[{'id':f'relation-{index}','source':r[':START_ID'],'target':r[':END_ID'],'type':r[':TYPE'],'properties':{k:v for k,v in r.items() if not k.startswith(':')}} for index,r in enumerate(rows)]
  else:
   nodes.extend({'id':r['id:ID'],'name':r['名称'],'type':r[':LABEL'].split(';')[0],'labels':r[':LABEL'].split(';'),'properties':{k:v for k,v in r.items() if k not in ['id:ID','名称',':LABEL']},'sourceFile':filename,'sourceRow':index+2} for index,r in enumerate(rows))
output={'source':source.name,'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'period':'2025-01 至 2025-11（11月非完整月）','notice':'附件脱敏数据；保留原始属性与关系，不额外推断关联或因果。','nodes':nodes,'edges':edges}
(base/'public/customer-graph.json').write_text(json.dumps(output,ensure_ascii=False,separators=(',',':'))+'\n')
print(f'已导入 {len(nodes)} 节点，{len(edges)} 关系')
