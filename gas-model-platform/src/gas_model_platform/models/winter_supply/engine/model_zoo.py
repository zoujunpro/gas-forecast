
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, Dict, List
import numpy as np
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer
from sklearn.preprocessing import StandardScaler, RobustScaler
from sklearn.compose import TransformedTargetRegressor
from sklearn.linear_model import LinearRegression,Ridge,Lasso,ElasticNet,HuberRegressor,BayesianRidge
from sklearn.ensemble import RandomForestRegressor,ExtraTreesRegressor,GradientBoostingRegressor,HistGradientBoostingRegressor,AdaBoostRegressor,BaggingRegressor
from sklearn.svm import LinearSVR
from sklearn.cross_decomposition import PLSRegression
from sklearn.tree import DecisionTreeRegressor

@dataclass
class ModelSpec:
    name: str
    kind: str  # baseline/ml/stat
    tunable: bool=False
    log_target: bool=False

BASELINES=['SeasonalNaive36','SeasonalMean2Y','SeasonalMean3Y','SeasonalTrend','RecentMean3','RecentMean6']
STAT_MODELS=['SARIMAX','ETS','Theta','UnobservedComponents']
ML_MODELS=[
 'LinearRegression','Ridge','Ridge_Strong','Lasso','ElasticNet','Huber','BayesianRidge','PLS2',
 'RandomForest','RandomForest_Shallow','ExtraTrees','ExtraTrees_Shallow',
 'GradientBoosting','GradientBoosting_LowLR','AdaBoost','DecisionTree','SVR_Linear',
 'LightGBM','XGBoost','CatBoost'
]

def all_specs(profile='full') -> List[ModelSpec]:
    names=BASELINES+ML_MODELS+STAT_MODELS
    if profile=='smoke':
        names=['SeasonalNaive36','SeasonalMean3Y','Ridge','ExtraTrees','LightGBM','XGBoost']
    return [ModelSpec(n,'baseline' if n in BASELINES else ('stat' if n in STAT_MODELS else 'ml'),
                      tunable=n in {'Ridge','ElasticNet','RandomForest','ExtraTrees','GradientBoosting',
                                    'LightGBM','XGBoost','CatBoost'},
                      log_target=n.endswith('_Log')) for n in names]

def _pipe(estimator, scale=False, robust=False):
    steps=[('imputer',SimpleImputer(strategy='median'))]
    if scale: steps.append(('scaler',RobustScaler() if robust else StandardScaler()))
    steps.append(('model',estimator))
    return Pipeline(steps)

def build_estimator(name: str, params: Dict[str,Any] | None=None, seed=42):
    p=dict(params or {})
    if name=='LinearRegression': est=_pipe(LinearRegression(),scale=True)
    elif name in {'Ridge','Ridge_Strong'}: est=_pipe(Ridge(alpha=p.get('alpha',100.0 if name=='Ridge_Strong' else 10.0)),scale=True)
    elif name=='Lasso': est=_pipe(Lasso(alpha=p.get('alpha',1.0),max_iter=20000),scale=True)
    elif name in {'ElasticNet','ElasticNet_L1'}: est=_pipe(ElasticNet(alpha=p.get('alpha',1.0),l1_ratio=p.get('l1_ratio',0.85 if name=='ElasticNet_L1' else 0.5),max_iter=20000),scale=True)
    elif name=='Huber': est=_pipe(HuberRegressor(epsilon=p.get('epsilon',1.35),alpha=p.get('alpha',0.0001),max_iter=1000),scale=True,robust=True)
    elif name=='BayesianRidge': est=_pipe(BayesianRidge(),scale=True)
    elif name=='PLS2': est=_pipe(PLSRegression(n_components=2,max_iter=1000),scale=True)
    elif name in {'RandomForest','RandomForest_Shallow'}:
        est=_pipe(RandomForestRegressor(n_estimators=p.get('n_estimators',160),max_depth=p.get('max_depth',6 if name=='RandomForest_Shallow' else None),
            min_samples_leaf=p.get('min_samples_leaf',4 if name=='RandomForest_Shallow' else 2),max_features=p.get('max_features',0.8),random_state=seed,n_jobs=1))
    elif name in {'ExtraTrees','ExtraTrees_Shallow'}:
        est=_pipe(ExtraTreesRegressor(n_estimators=p.get('n_estimators',160),max_depth=p.get('max_depth',7 if name=='ExtraTrees_Shallow' else None),
            min_samples_leaf=p.get('min_samples_leaf',4 if name=='ExtraTrees_Shallow' else 2),max_features=p.get('max_features',0.9),random_state=seed,n_jobs=1))
    elif name in {'GradientBoosting','GradientBoosting_LowLR'}:
        est=_pipe(GradientBoostingRegressor(n_estimators=p.get('n_estimators',180 if name=='GradientBoosting_LowLR' else 100),learning_rate=p.get('learning_rate',0.015 if name=='GradientBoosting_LowLR' else 0.03),
            max_depth=p.get('max_depth',2),min_samples_leaf=p.get('min_samples_leaf',3),subsample=p.get('subsample',0.9),random_state=seed,loss='huber'))
    elif name=='AdaBoost': est=_pipe(AdaBoostRegressor(n_estimators=120,learning_rate=0.04,random_state=seed,loss='square'))
    elif name=='BaggingTree': est=_pipe(BaggingRegressor(estimator=DecisionTreeRegressor(max_depth=5,min_samples_leaf=3,random_state=seed),n_estimators=80,random_state=seed,n_jobs=1))
    elif name=='SVR_Linear': est=_pipe(LinearSVR(C=1.0,epsilon=0.01,random_state=seed,max_iter=20000),scale=True,robust=True)
    elif name=='DecisionTree': est=_pipe(DecisionTreeRegressor(max_depth=6,min_samples_leaf=3,random_state=seed))
    elif name=='LightGBM':
        from lightgbm import LGBMRegressor
        est=_pipe(LGBMRegressor(n_estimators=p.get('n_estimators',280),learning_rate=p.get('learning_rate',0.03),
            num_leaves=p.get('num_leaves',15),max_depth=p.get('max_depth',-1),min_child_samples=p.get('min_child_samples',15),
            subsample=p.get('subsample',0.9),colsample_bytree=p.get('colsample_bytree',0.9),reg_alpha=p.get('reg_alpha',0.1),
            reg_lambda=p.get('reg_lambda',1.0),random_state=seed,n_jobs=1,verbosity=-1))
    elif name=='XGBoost':
        from xgboost import XGBRegressor
        est=_pipe(XGBRegressor(n_estimators=p.get('n_estimators',300),learning_rate=p.get('learning_rate',0.03),
            max_depth=p.get('max_depth',4),min_child_weight=p.get('min_child_weight',3),subsample=p.get('subsample',0.9),
            colsample_bytree=p.get('colsample_bytree',0.9),reg_alpha=p.get('reg_alpha',0.1),reg_lambda=p.get('reg_lambda',1.0),
            objective='reg:squarederror',random_state=seed,n_jobs=1,tree_method='hist'))
    elif name=='CatBoost':
        from catboost import CatBoostRegressor
        est=CatBoostRegressor(iterations=p.get('iterations',300),learning_rate=p.get('learning_rate',0.03),
            depth=p.get('depth',6),l2_leaf_reg=p.get('l2_leaf_reg',5.0),random_seed=seed,verbose=False,allow_writing_files=False,thread_count=1)
    else: raise KeyError(name)
    if name.endswith('_Log'):
        est=TransformedTargetRegressor(regressor=est,func=np.log1p,inverse_func=np.expm1,check_inverse=False)
    return est

def suggest_params(trial, name):
    if name=='Ridge': return {'alpha':trial.suggest_float('alpha',1e-3,1e4,log=True)}
    if name=='ElasticNet': return {'alpha':trial.suggest_float('alpha',1e-4,100,log=True),'l1_ratio':trial.suggest_float('l1_ratio',0.05,0.95)}
    if name in {'RandomForest','ExtraTrees'}: return {
        'n_estimators':trial.suggest_int('n_estimators',100,450,step=50),'max_depth':trial.suggest_int('max_depth',4,18),
        'min_samples_leaf':trial.suggest_int('min_samples_leaf',1,8),'max_features':trial.suggest_float('max_features',0.45,1.0)}
    if name=='GradientBoosting': return {'n_estimators':trial.suggest_int('n_estimators',80,350,step=30),
        'learning_rate':trial.suggest_float('learning_rate',0.005,0.12,log=True),'max_depth':trial.suggest_int('max_depth',1,5),
        'min_samples_leaf':trial.suggest_int('min_samples_leaf',2,10),'subsample':trial.suggest_float('subsample',0.65,1.0)}
    if name=='LightGBM': return {'n_estimators':trial.suggest_int('n_estimators',150,600,step=50),
        'learning_rate':trial.suggest_float('learning_rate',0.005,0.12,log=True),'num_leaves':trial.suggest_int('num_leaves',7,63),
        'min_child_samples':trial.suggest_int('min_child_samples',5,35),'subsample':trial.suggest_float('subsample',0.65,1.0),
        'colsample_bytree':trial.suggest_float('colsample_bytree',0.55,1.0),'reg_alpha':trial.suggest_float('reg_alpha',1e-4,5,log=True),
        'reg_lambda':trial.suggest_float('reg_lambda',1e-4,20,log=True)}
    if name=='XGBoost': return {'n_estimators':trial.suggest_int('n_estimators',150,600,step=50),
        'learning_rate':trial.suggest_float('learning_rate',0.005,0.12,log=True),'max_depth':trial.suggest_int('max_depth',2,8),
        'min_child_weight':trial.suggest_float('min_child_weight',1,20),'subsample':trial.suggest_float('subsample',0.65,1.0),
        'colsample_bytree':trial.suggest_float('colsample_bytree',0.55,1.0),'reg_alpha':trial.suggest_float('reg_alpha',1e-4,5,log=True),
        'reg_lambda':trial.suggest_float('reg_lambda',1e-4,20,log=True)}
    if name=='CatBoost': return {'iterations':trial.suggest_int('iterations',150,600,step=50),
        'learning_rate':trial.suggest_float('learning_rate',0.005,0.12,log=True),'depth':trial.suggest_int('depth',4,9),
        'l2_leaf_reg':trial.suggest_float('l2_leaf_reg',0.5,30,log=True)}
    return {}
