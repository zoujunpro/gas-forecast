
from __future__ import annotations
from typing import Dict, List
import json
import numpy as np
import pandas as pd
from .model_zoo import ModelSpec,suggest_params
from .backtest import evaluate_candidate

def select_feature_set(df, tuning_folds, ranking, candidates, seed=42):
    proxy=[ModelSpec('ExtraTrees','ml'),ModelSpec('Ridge','ml')]
    rows=[]
    all_features=ranking.feature.tolist()
    for n in candidates:
        cols=all_features if int(n)>=len(all_features) else all_features[:int(n)]
        scores=[]
        for spec in proxy:
            _,_,met,_=evaluate_candidate(spec,df,tuning_folds,cols,seed=seed,quiet=True)
            if np.isfinite(met['MAPE']): scores.append(met['MAPE'])
        rows.append({'top_n':'all' if int(n)>=len(all_features) else int(n),'feature_count':len(cols),
                     'proxy_MAPE':float(np.mean(scores)) if scores else np.inf,'features':'|'.join(cols)})
    table=pd.DataFrame(rows).sort_values('proxy_MAPE')
    best=table.iloc[0]
    return best['features'].split('|'),table

def tune_with_optuna(spec,df,tuning_folds,feature_cols,n_trials,seed=42):
    try:
        import optuna
        optuna.logging.set_verbosity(optuna.logging.WARNING)
    except Exception:
        return {},pd.DataFrame(), 'Optuna未安装，使用默认参数',pd.DataFrame()
    all_issues=[]
    def objective(trial):
        params=suggest_params(trial,spec.name)
        _,_,met,issues=evaluate_candidate(spec,df,tuning_folds,feature_cols,params=params,seed=seed,quiet=True)
        if not issues.empty:
            all_issues.append(issues.assign(stage='tuning'))
        score=met['MAPE']
        return float(score) if np.isfinite(score) else 1e9
    study=optuna.create_study(direction='minimize',sampler=optuna.samplers.TPESampler(seed=seed))
    study.optimize(objective,n_trials=int(n_trials),show_progress_bar=False,catch=(Exception,))
    trials=study.trials_dataframe()
    issues=pd.concat(all_issues,ignore_index=True) if all_issues else pd.DataFrame()
    return dict(study.best_params),trials,f'best MAPE={study.best_value:.4f}%',issues
