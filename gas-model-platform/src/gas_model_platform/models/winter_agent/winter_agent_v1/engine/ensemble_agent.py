
from __future__ import annotations
from typing import Dict
import numpy as np
import pandas as pd
from scipy.optimize import nnls
from .metrics import calculate_metrics

def _wide(preds: Dict[str,pd.DataFrame]):
    frames=[]
    for name,d in preds.items():
        if d is None or d.empty: continue
        x=d[['season','date','actual','prediction']].rename(columns={'prediction':name})
        frames.append(x)
    if not frames: return pd.DataFrame()
    out=frames[0]
    for x in frames[1:]: out=out.merge(x,on=['season','date','actual'],how='inner')
    return out

def build_ensembles(tune_preds,eval_preds,tune_metrics,top_k=5):
    ranking=sorted([(n,m['MAPE']) for n,m in tune_metrics.items() if np.isfinite(m['MAPE'])],key=lambda x:x[1])
    top=[n for n,_ in ranking[:max(top_k,5)]]
    tw=_wide({n:tune_preds[n] for n in top if n in tune_preds})
    ew=_wide({n:eval_preds[n] for n in top if n in eval_preds})
    if tw.empty or ew.empty: return {},pd.DataFrame(),{}
    available=[n for n in top if n in tw.columns and n in ew.columns]
    definitions={}
    for k in [2,3,5]:
        cols=available[:min(k,len(available))]
        if len(cols)>=2:
            definitions[f'Ensemble_EqualTop{k}']={c:1/len(cols) for c in cols}
            inv=np.array([1/max(tune_metrics[c]['MAPE'],1e-6) for c in cols]); inv=inv/inv.sum()
            definitions[f'Ensemble_InverseTop{k}']=dict(zip(cols,inv))
    cols=available[:min(5,len(available))]
    if len(cols)>=2:
        A=tw[cols].values; y=tw.actual.values
        w,_=nnls(A,y)
        if w.sum()<=0: w=np.ones(len(cols))
        w=w/w.sum(); definitions['Ensemble_NNLS_Top5']=dict(zip(cols,w))
        definitions['Ensemble_MedianTop5']={c:np.nan for c in cols}
    preds={}; rows=[]; weights={}
    for name,wmap in definitions.items():
        cols=list(wmap)
        d=ew[['season','date','actual']].copy()
        if name=='Ensemble_MedianTop5': d['prediction']=ew[cols].median(axis=1)
        else:
            d['prediction']=sum(ew[c]*float(wmap[c]) for c in cols)
        d['model']=name; preds[name]=d[['model','season','date','actual','prediction']]
        met=calculate_metrics(d.actual,d.prediction)
        rows.append({'model':name,'type':'ensemble',**met,'constituents':'|'.join(cols)})
        weights[name]={c:(None if np.isnan(v) else float(v)) for c,v in wmap.items()}
    return preds,pd.DataFrame(rows),weights
