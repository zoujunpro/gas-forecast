
from __future__ import annotations
from typing import List, Tuple
import numpy as np
import pandas as pd
from sklearn.ensemble import ExtraTreesRegressor
from sklearn.feature_selection import mutual_info_regression

LAGS = [1,2,3,6,12,18,24,35,36,37,48,72,108]
ROLLS = [3,6,12,18,36]
WEATHER = ['avg_temp','max_temp','min_temp','HDD','extreme_cold_days']
CNY = {
  2016:'2016-02-08',2017:'2017-01-28',2018:'2018-02-16',2019:'2019-02-05',
  2020:'2020-01-25',2021:'2021-02-12',2022:'2022-02-01',2023:'2023-01-22',
  2024:'2024-02-10',2025:'2025-01-29',2026:'2026-02-17',2027:'2027-02-06',
  2028:'2028-01-26',2029:'2029-02-13',2030:'2030-02-03'
}

def _time_features(d: pd.Timestamp):
    td = 1 if d.day<=10 else (2 if d.day<=20 else 3)
    slot=(d.month-1)*3+(td-1)
    cny=pd.Timestamp(CNY.get(d.year, f'{d.year}-02-10'))
    dist=(d-cny).days
    return {
        'year':d.year, 'month':d.month, 'tenday':td, 'slot_of_year':slot,
        'month_sin':np.sin(2*np.pi*d.month/12), 'month_cos':np.cos(2*np.pi*d.month/12),
        'slot_sin':np.sin(2*np.pi*slot/36), 'slot_cos':np.cos(2*np.pi*slot/36),
        'heating_season':int(d.month in [11,12,1,2,3]),
        'winter_peak':int(d.month in [12,1,2]), 'year_trend':d.year-2016,
        'cny_tenday':int(abs(dist)<=10), 'pre_cny':int(-30<=dist<-10),
        'post_cny':int(10<dist<=30), 'days_to_cny_abs':min(abs(dist),120),
    }

def build_supervised(df: pd.DataFrame) -> Tuple[pd.DataFrame,pd.Series]:
    x=df.copy().sort_values('date').reset_index(drop=True)
    for k in ['year','month','tenday','slot_of_year','month_sin','month_cos','slot_sin','slot_cos',
              'heating_season','winter_peak','year_trend','cny_tenday','pre_cny','post_cny','days_to_cny_abs']:
        x[k]=[ _time_features(d)[k] for d in x.date]
    x['temp_range']=x['max_temp']-x['min_temp']
    x['HDD_sq']=x['HDD']**2
    x['avg_temp_sq']=x['avg_temp']**2
    x['HDD_change']=x['HDD'].diff()
    x['avg_temp_change']=x['avg_temp'].diff()
    for lag in LAGS:
        x[f'lag_{lag}']=x['gas_sales'].shift(lag)
    shifted=x['gas_sales'].shift(1)
    for w in ROLLS:
        x[f'roll_mean_{w}']=shifted.rolling(w,min_periods=max(2,w//2)).mean()
        x[f'roll_std_{w}']=shifted.rolling(w,min_periods=max(2,w//2)).std()
        x[f'roll_min_{w}']=shifted.rolling(w,min_periods=max(2,w//2)).min()
        x[f'roll_max_{w}']=shifted.rolling(w,min_periods=max(2,w//2)).max()
    x['yoy_diff']=x['lag_36']-x['lag_72']
    x['yoy_ratio']=x['lag_36']/x['lag_72'].replace(0,np.nan)
    x['seasonal_mean_2y']=x[['lag_36','lag_72']].mean(axis=1)
    x['seasonal_mean_3y']=x[['lag_36','lag_72','lag_108']].mean(axis=1)
    x['HDD_x_lag36']=x['HDD']*x['lag_36']
    x['cold_x_lag36']=x['extreme_cold_days']*x['lag_36']
    x['HDD_x_heating']=x['HDD']*x['heating_season']
    x['temp_x_heating']=x['avg_temp']*x['heating_season']
    feature_cols=[c for c in x.columns if c not in ['date','gas_sales']]
    valid=x['lag_36'].notna()
    return x.loc[valid,feature_cols].reset_index(drop=True), x.loc[valid,'gas_sales'].reset_index(drop=True)

def make_feature_row(date, exog_row, y_history: List[float], exog_history: List[dict]):
    d=pd.Timestamp(date)
    row=_time_features(d)
    for c in WEATHER:
        row[c]=float(exog_row.get(c,np.nan))
    row['temp_range']=row['max_temp']-row['min_temp']
    row['HDD_sq']=row['HDD']**2
    row['avg_temp_sq']=row['avg_temp']**2
    prev=exog_history[-1] if exog_history else {}
    row['HDD_change']=row['HDD']-float(prev.get('HDD',row['HDD']))
    row['avg_temp_change']=row['avg_temp']-float(prev.get('avg_temp',row['avg_temp']))
    for lag in LAGS:
        row[f'lag_{lag}']=float(y_history[-lag]) if len(y_history)>=lag else np.nan
    arr=np.asarray(y_history,dtype=float)
    for w in ROLLS:
        a=arr[-w:] if len(arr)>=1 else np.array([np.nan])
        row[f'roll_mean_{w}']=float(np.nanmean(a))
        row[f'roll_std_{w}']=float(np.nanstd(a))
        row[f'roll_min_{w}']=float(np.nanmin(a))
        row[f'roll_max_{w}']=float(np.nanmax(a))
    row['yoy_diff']=row['lag_36']-row['lag_72'] if np.isfinite(row['lag_72']) else np.nan
    row['yoy_ratio']=row['lag_36']/row['lag_72'] if np.isfinite(row['lag_72']) and row['lag_72']!=0 else np.nan
    vals2=[row['lag_36'],row['lag_72']]
    vals3=[row['lag_36'],row['lag_72'],row['lag_108']]
    row['seasonal_mean_2y']=float(np.nanmean(vals2))
    row['seasonal_mean_3y']=float(np.nanmean(vals3))
    row['HDD_x_lag36']=row['HDD']*row['lag_36']
    row['cold_x_lag36']=row['extreme_cold_days']*row['lag_36']
    row['HDD_x_heating']=row['HDD']*row['heating_season']
    row['temp_x_heating']=row['avg_temp']*row['heating_season']
    return row

def rank_features(train_df: pd.DataFrame, seed=42):
    X,y=build_supervised(train_df)
    Xn=X.replace([np.inf,-np.inf],np.nan)
    Xfill=Xn.fillna(Xn.median(numeric_only=True)).fillna(0)
    model=ExtraTreesRegressor(n_estimators=220,random_state=seed,n_jobs=1,min_samples_leaf=2)
    model.fit(Xfill,y)
    tree=pd.Series(model.feature_importances_,index=X.columns)
    try:
        mi=pd.Series(mutual_info_regression(Xfill,y,random_state=seed),index=X.columns)
    except Exception:
        mi=pd.Series(0.0,index=X.columns)
    def norm(s):
        return (s-s.min())/(s.max()-s.min()+1e-12)
    score=0.7*norm(tree)+0.3*norm(mi)
    out=pd.DataFrame({'feature':X.columns,'score':score,'tree_importance':tree,'mutual_info':mi})
    return out.sort_values('score',ascending=False).reset_index(drop=True)
