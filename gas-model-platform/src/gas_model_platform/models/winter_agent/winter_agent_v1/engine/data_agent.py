
from __future__ import annotations
from pathlib import Path
from typing import Dict, List, Tuple
import numpy as np
import pandas as pd

ALIASES = {
    'date': ['date','日期','时间','ds'],
    'gas_sales': ['gas_sales','gas_sale','销量','天然气销量','用气量','y'],
    'avg_temp': ['avg_temp','平均温度','平均气温','temp'],
    'max_temp': ['max_temp','最高温度','最高气温','tempmax'],
    'min_temp': ['min_temp','最低温度','最低气温','tempmin'],
    'HDD': ['HDD','hdd','采暖度日'],
    'extreme_cold_days': ['extreme_cold_days','ColdDays','极端低温天数','cold_days'],
}
WEATHER_COLS = ['avg_temp','max_temp','min_temp','HDD','extreme_cold_days']

def _rename_columns(df: pd.DataFrame) -> pd.DataFrame:
    rename = {}
    normalized = {str(c).strip().lower(): c for c in df.columns}
    for std, aliases in ALIASES.items():
        for a in aliases:
            key = str(a).strip().lower()
            if key in normalized:
                rename[normalized[key]] = std
                break
    return df.rename(columns=rename)

def _slot_key(d: pd.Timestamp) -> Tuple[int,int]:
    td = 1 if d.day <= 10 else (2 if d.day <= 20 else 3)
    return int(d.month), td

def clean_data(data: pd.DataFrame, drop_suspicious_tail: bool = True):
    """清洗业务项目传入的数据，不读取文件也不访问数据库。"""
    df = data.copy()
    df = _rename_columns(df)
    required = {'date','gas_sales'}
    if not required.issubset(df.columns):
        raise ValueError(f'缺少必要字段 {sorted(required-set(df.columns))}，实际字段={list(df.columns)}')
    logs: List[Dict[str, object]] = []
    df['date'] = pd.to_datetime(df['date'], errors='coerce')
    df['gas_sales'] = pd.to_numeric(df['gas_sales'], errors='coerce')
    for c in WEATHER_COLS:
        if c not in df.columns:
            df[c] = np.nan
            logs.append({'type':'missing_weather_column','detail':f'{c} 不存在，后续使用可用字段/历史气候态'})
        df[c] = pd.to_numeric(df[c], errors='coerce')
    before = len(df)
    df = df.dropna(subset=['date','gas_sales']).sort_values('date')
    if len(df) < before:
        logs.append({'type':'drop_invalid_rows','detail':f'删除 {before-len(df)} 行无效日期或销量'})
    # 同日期重复取均值
    if df['date'].duplicated().any():
        ndup = int(df['date'].duplicated().sum())
        agg = {'gas_sales':'mean', **{c:'mean' for c in WEATHER_COLS}}
        df = df.groupby('date', as_index=False).agg(agg)
        logs.append({'type':'deduplicate','detail':f'合并 {ndup} 条重复日期'})
    # 规范为旬起始日 1/11/21
    original = df['date'].copy()
    day = np.where(df['date'].dt.day <= 10, 1, np.where(df['date'].dt.day <= 20, 11, 21))
    df['date'] = pd.to_datetime(dict(year=df.date.dt.year, month=df.date.dt.month, day=day))
    if not original.equals(df['date']):
        logs.append({'type':'normalize_tenday','detail':'日期已规范到每月1日/11日/21日'})
    # 气象插值；HDD可由平均温度兜底
    for c in WEATHER_COLS:
        df[c] = df[c].interpolate(limit_direction='both').ffill().bfill()
    if df['HDD'].isna().all() and not df['avg_temp'].isna().all():
        df['HDD'] = (18.0-df['avg_temp']).clip(lower=0) * 10
    # 删除明显不完整的末尾旬数据（仅尾部，避免大面积“清洗”真实波动）
    if drop_suspicious_tail and len(df) >= 120:
        for _ in range(2):
            if len(df) < 120: break
            idx = df.index[-1]
            d = df.loc[idx,'date']
            key = _slot_key(d)
            hist = df.iloc[:-1].copy()
            hist['_key'] = hist['date'].map(_slot_key)
            same = hist.loc[hist['_key']==key,'gas_sales'].tail(6)
            local = hist['gas_sales'].tail(6)
            ref = float(np.nanmedian(same)) if len(same)>=3 else float(np.nanmedian(local))
            val = float(df.loc[idx,'gas_sales'])
            ratio = val/ref if ref > 0 else 1.0
            if ratio < 0.35 or ratio > 3.0:
                logs.append({'type':'drop_suspicious_tail','detail':f'删除尾部异常旬 {d.date()}，销量={val:.3f}，历史同旬比={ratio:.3f}'})
                df = df.iloc[:-1].copy()
            else:
                break
    df = df.reset_index(drop=True)
    # 完整性信息
    gaps = []
    expected = generate_tenday_dates(df['date'].min(), None, end=df['date'].max())
    missing = sorted(set(expected)-set(df['date']))
    if missing:
        logs.append({'type':'missing_tendays','detail':f'缺失旬点 {len(missing)} 个；模型将按现有顺序使用滞后特征'})
        gaps = [str(x.date()) for x in missing]
    summary = {
        'rows':int(len(df)), 'start':str(df.date.min().date()), 'end':str(df.date.max().date()),
        'missing_tendays':len(gaps), 'target_min':float(df.gas_sales.min()),
        'target_max':float(df.gas_sales.max()), 'target_mean':float(df.gas_sales.mean())
    }
    return df, pd.DataFrame(logs), summary

def load_and_clean(path: Path, drop_suspicious_tail: bool = True):
    """文件调用兼容入口；基础包正式接入建议直接使用 clean_data。"""
    if not path.exists():
        raise FileNotFoundError(f'找不到数据文件: {path}')
    df = pd.read_excel(path) if path.suffix.lower() in {'.xlsx','.xls'} else pd.read_csv(path)
    return clean_data(df, drop_suspicious_tail)

def prepare_future_data(data: pd.DataFrame) -> pd.DataFrame:
    """校验并规范业务项目传入的未来旬天气数据。"""
    future = _rename_columns(data.copy())
    if 'date' not in future.columns:
        raise ValueError('未来数据缺少必要字段 date')
    future['date'] = pd.to_datetime(future['date'], errors='coerce')
    if future['date'].isna().any():
        raise ValueError('未来数据中存在无效日期')
    for column in WEATHER_COLS:
        if column not in future.columns:
            future[column] = np.nan
        future[column] = pd.to_numeric(future[column], errors='coerce')
        future[column] = future[column].interpolate(limit_direction='both').ffill().bfill()
    if future['HDD'].isna().any() and not future['avg_temp'].isna().all():
        future['HDD'] = future['HDD'].fillna(
            (18.0-future['avg_temp']).clip(lower=0)*10
        )
    return future.sort_values('date').reset_index(drop=True)

def generate_tenday_dates(start, periods: int | None, end=None):
    d = pd.Timestamp(start).normalize()
    if d.day not in (1,11,21):
        d = pd.Timestamp(d.year,d.month,1 if d.day<=10 else (11 if d.day<=20 else 21))
    out=[]
    while True:
        if end is not None and d > pd.Timestamp(end): break
        if periods is not None and len(out)>=periods: break
        out.append(d)
        if d.day==1: d=pd.Timestamp(d.year,d.month,11)
        elif d.day==11: d=pd.Timestamp(d.year,d.month,21)
        else:
            d = pd.Timestamp(d.year+1,1,1) if d.month==12 else pd.Timestamp(d.year,d.month+1,1)
    return out

def infer_forecast_start(last_date: pd.Timestamp) -> pd.Timestamp:
    y = int(last_date.year)
    return pd.Timestamp(y if last_date < pd.Timestamp(y,11,1) else y+1, 11, 1)

def build_future_weather(history: pd.DataFrame, dates, province: str, future_weather_dir: Path, recent_years: int = 5):
    candidates = [future_weather_dir/f'{province}_未来气象.xlsx', future_weather_dir/f'{province}_future_weather.xlsx',
                  future_weather_dir/f'{province}_未来气象.csv']
    override = next((p for p in candidates if p.exists()), None)
    if override:
        fut = pd.read_excel(override) if override.suffix.lower().startswith('.xls') else pd.read_csv(override)
        fut = _rename_columns(fut)
        fut['date'] = pd.to_datetime(fut['date'])
        fut = pd.DataFrame({'date':pd.to_datetime(dates)}).merge(fut, on='date', how='left')
        source='用户提供未来气象'
    else:
        h = history.copy()
        h['year']=h.date.dt.year
        h['month']=h.date.dt.month
        h['tenday']=np.where(h.date.dt.day<=10,1,np.where(h.date.dt.day<=20,2,3))
        cutoff=max(int(h.year.max())-recent_years+1, int(h.year.min()))
        recent=h[h.year>=cutoff]
        rows=[]
        for d in pd.to_datetime(dates):
            td=1 if d.day<=10 else (2 if d.day<=20 else 3)
            same=recent[(recent.month==d.month)&(recent.tenday==td)]
            if same.empty: same=h[(h.month==d.month)&(h.tenday==td)]
            row={'date':d}
            for c in WEATHER_COLS:
                row[c]=float(same[c].median()) if c in same and same[c].notna().any() else float(h[c].median())
            rows.append(row)
        fut=pd.DataFrame(rows)
        source=f'近{recent_years}年同月同旬气候态中位数'
    for c in WEATHER_COLS:
        if c not in fut: fut[c]=np.nan
        fut[c]=pd.to_numeric(fut[c],errors='coerce').interpolate(limit_direction='both').ffill().bfill()
    if fut['HDD'].isna().any():
        fut['HDD']=fut['HDD'].fillna((18.0-fut['avg_temp']).clip(lower=0)*10)
    fut['weather_source']=source
    return fut
