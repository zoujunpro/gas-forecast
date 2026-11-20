from pathlib import Path
import json,calendar
b=Path(__file__).resolve().parents[1]/'public';source=json.loads((b/'province-sales.json').read_text());d=json.loads((b/'dashboard.json').read_text());year=source['currentYear'];rows=source['provinces'];roundv=lambda v:round(v,4)
def months(p,y):return next(r['months'] for r in p['years'] if r['year']==y)
def total(p,y):return sum(m['volume']*(.6 if y<year and m['month']==11 else 1) for m in months(p,y) if m['month']<=11)
def growth(v,old):return round((v/old-1)*100,1) if old else 0
current=sum(total(p,year) for p in rows);previous=sum(total(p,year-1) for p in rows)
monthly=[];ten=[];daily=[]
for month in range(1,12):
 v=sum(next(m['volume'] for m in months(p,year) if m['month']==month) for p in rows);old=sum(next(m['volume'] for m in months(p,year-1) if m['month']==month) for p in rows);days=18 if month==11 else calendar.monthrange(year,month)[1];old_days=calendar.monthrange(year-1,month)[1]
 monthly.append({'label':f'{month}月'+('（截至18日）' if month==11 else ''),'volume':roundv(v),'growth':growth(v,old*(18/30 if month==11 else 1))})
 for k,start in enumerate([1,11,21]):
  count=min(10 if k<2 else days-20,max(0,days-start+1))
  if count>0:ten.append({'label':f'{month}月'+['上旬','中旬','下旬'][k]+('（截至18日）' if month==11 and k==1 else ''),'volume':roundv(v*count/days),'growth':growth(v/days,old/old_days)})
 if month==11:
  for day in range(1,19):daily.append({'label':f'11/{day:02d}','volume':roundv(v/days),'growth':growth(v/days,old/old_days)})
d['salesSeries']={'monthly':monthly,'tenDay':ten,'daily':daily};d['sales']=[m['volume'] for m in monthly];d['growth']=[m['growth'] for m in monthly];d['totalSales']=roundv(current);d['salesScope']='全部34个省级地区 · 2026累计至11-18';d['metrics'][1]=['▥','本年累计天然气销量',f'{current:.2f}','亿方',f'同比 {growth(current,previous):+.1f}% ↑'];d['provinces']=[[p['name'],f'{total(p,year):.3f}',f'{growth(total(p,year),total(p,year-1)):.1f}'] for p in sorted(rows,key=lambda p:total(p,year),reverse=True)[:5]];d['salesAggregationNote']='来源 province-sales.json；日度、旬度按月度模拟销量均摊；同比按相同截止日期比较；行业按JSON比例分摊。';d['industryVolumes']=[{'name':i[0],'volume':roundv(current*i[1]/100)} for i in d['industries']];(b/'dashboard.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
