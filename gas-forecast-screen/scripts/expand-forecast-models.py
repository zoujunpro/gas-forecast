"""Generate UI-only model comparisons from the repository algorithm catalog.
No models are trained or invoked by this script.
"""
import ast,json,math
from pathlib import Path
base=Path(__file__).resolve().parents[1]
root=base.parent/'gas-model-engine/src/gas_model_engine/algorithms'
catalog=[]
for family in ['baseline','statistical','linear','tree','composite']:
 for path in sorted((root/family).glob('*.py')):
  if path.name.startswith('_'):continue
  tree=ast.parse(path.read_text())
  for cls in [n for n in tree.body if isinstance(n,ast.ClassDef)]:
   code=name=None
   for stmt in cls.body:
    if not isinstance(stmt,ast.Assign):continue
    if isinstance(stmt.value,ast.Call) and getattr(stmt.value.func,'id','')=='AlgorithmMetadata':
     args=stmt.value.args
     if len(args)>1 and all(isinstance(a,ast.Constant) for a in args[:2]):code,name=[a.value for a in args[:2]]
    for target in stmt.targets:
     if isinstance(target,ast.Tuple) and [getattr(e,'id','') for e in target.elts]==['code','name']:
      code,name=ast.literal_eval(stmt.value)
   if code and name:catalog.append({'code':code,'name':name,'family':family})
assert len({m['code'] for m in catalog})==len(catalog)
selected_codes=['XGBOOST_V1', 'LIGHTGBM_V1', 'CATBOOST_V1', 'RANDOM_FOREST_V1', 'EXTRA_TREES_V1', 'GRADIENT_BOOSTING_V1', 'ADABOOST_V1', 'BAGGING_TREE_V1', 'ARIMA_V1', 'SARIMAX_V1', 'PROPHET_V1', 'EXPONENTIAL_SMOOTHING_V1']
catalog=[m for m in catalog if m['code'] in selected_codes]
(base/'public/model-catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
p=base/'public/forecast-details.json';records=json.loads(p.read_text())
for r in records:
 for row in r['backtest']:
  for key in list(row):
   if key not in ['label','actual']:del row[key]
 anchor=[row['value'] for row in r['rows']];models=[]
 for i,m in enumerate(catalog):
  amplitude=.016+(i%11)*.0075
  for k,row in enumerate(r['backtest']):row[m['name']]=round(row['actual']*(1+amplitude*math.sin(k*.8+i*.7+.4)),2)
  errors=[row[m['name']]-row['actual'] for row in r['backtest']]
  models.append({**m,'mae':round(sum(abs(e) for e in errors)/len(errors),3),'rmse':round(math.sqrt(sum(e*e for e in errors)/len(errors)),3),'wape':round(sum(abs(e) for e in errors)/sum(row['actual'] for row in r['backtest'])*100,2),'forecast':[round(v*(1+amplitude*math.sin(k*.6+i*.8)),2) for k,v in enumerate(anchor)]})
 models.sort(key=lambda m:m['wape']);r['models']=models;r['selectedModel']=models[0]['name']
 for k,row in enumerate(r['rows']):row['value']=models[0]['forecast'][k];row['lower']=round(row['value']*.91,2);row['upper']=round(row['value']*1.09,2)
 r['method']=f'全部{len(models)}项注册算法的界面模拟对比；每项使用相同12期模拟回测样本。组合算法也仅模拟结果，不代表已训练或已配置成员。'
 r['notes']=[n for n in r['notes'] if '完整算法' not in n]+['完整算法目录取自本地源码；可执行性与场景适配尚未验证，曲线不代表真实运行结果。']
p.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n')
print('已写入',len(catalog),'项算法及3类场景模拟结果')
