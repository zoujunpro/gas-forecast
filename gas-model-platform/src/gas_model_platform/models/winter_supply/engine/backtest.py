
from __future__ import annotations
import logging
from typing import Dict, List
import warnings

import pandas as pd
from .data_agent import generate_tenday_dates
from .forecasting import forecast_candidate
from .issues import localize_issue_message
from .metrics import calculate_metrics

logger = logging.getLogger(__name__)

def winter_dates(start_year: int):
    return pd.to_datetime(generate_tenday_dates(pd.Timestamp(start_year,11,1),15))

def make_folds(df, n_seasons=6, min_train_rows=108):
    years=[]
    date_set=set(df.date)
    for y in range(int(df.date.min().year),int(df.date.max().year)+1):
        dates=winter_dates(y)
        if all(d in date_set for d in dates):
            train_n=int((df.date<dates[0]).sum())
            if train_n>=min_train_rows: years.append(y)
    years=years[-n_seasons:]
    return [{'season':f'{y}-{y+1}','start_year':y,'dates':winter_dates(y)} for y in years]

def evaluate_candidate(spec, df, folds, feature_cols, params=None, seed=42, quiet=False):
    rows=[]; fold_metrics=[]; issues=[]
    for fold in folds:
        train=df[df.date<fold['dates'][0]].copy()
        test=df[df.date.isin(fold['dates'])].copy().sort_values('date')
        if len(test)!=15:
            issues.append({
                'severity': 'error',
                'stage': 'backtest',
                'model_name': spec.name,
                'season': fold['season'],
                'category': 'IncompleteFold',
                'message': f'测试集只有{len(test)}旬',
            })
            continue
        try:
            with warnings.catch_warnings(record=True) as caught:
                warnings.simplefilter('always')
                pred=forecast_candidate(spec,train,test.drop(columns=['gas_sales']),feature_cols,params=params,seed=seed)
            issues.extend({
                'severity': 'warning',
                'stage': 'backtest',
                'model_name': spec.name,
                'season': fold['season'],
                'category': item.category.__name__,
                'message': localize_issue_message(
                    item.category.__name__, str(item.message)
                ),
            } for item in caught)
            for item in caught:
                localized = localize_issue_message(
                    item.category.__name__, str(item.message)
                )
                logger.warning(
                    "model training warning stage=backtest model=%s season=%s category=%s message=%s",
                    spec.name,
                    fold['season'],
                    item.category.__name__,
                    localized,
                )
            met=calculate_metrics(test.gas_sales.values,pred)
            fold_metrics.append({'model':spec.name,'season':fold['season'],**met})
            for d,y,p in zip(test.date,test.gas_sales,pred):
                rows.append({'model':spec.name,'season':fold['season'],'date':d,'actual':float(y),'prediction':float(p)})
        except Exception as e:
            issues.append({
                'severity': 'error',
                'stage': 'backtest',
                'model_name': spec.name,
                'season': fold['season'],
                'category': type(e).__name__,
                'message': str(e),
            })
            if not quiet: print(f'    [跳过] {spec.name} {fold["season"]}: {e}')
    pred_df=pd.DataFrame(rows)
    overall=calculate_metrics(pred_df.actual,pred_df.prediction) if not pred_df.empty else calculate_metrics([],[])
    return pred_df,pd.DataFrame(fold_metrics),overall,pd.DataFrame(issues)
