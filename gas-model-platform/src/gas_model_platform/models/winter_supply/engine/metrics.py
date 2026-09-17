
from __future__ import annotations
import numpy as np
from typing import Dict

def calculate_metrics(y_true, y_pred) -> Dict[str, float]:
    y = np.asarray(y_true, dtype=float)
    p = np.asarray(y_pred, dtype=float)
    mask = np.isfinite(y) & np.isfinite(p)
    y, p = y[mask], p[mask]
    if len(y) == 0:
        return {k: float('nan') for k in ['MAPE','WMAPE','sMAPE','RMSE','MAE','R2']}
    err = p - y
    denom = np.maximum(np.abs(y), 1e-8)
    mape = np.mean(np.abs(err) / denom) * 100
    wmape = np.sum(np.abs(err)) / max(np.sum(np.abs(y)), 1e-8) * 100
    smape = np.mean(2 * np.abs(err) / np.maximum(np.abs(y)+np.abs(p), 1e-8)) * 100
    rmse = np.sqrt(np.mean(err**2))
    mae = np.mean(np.abs(err))
    ss_res = np.sum(err**2)
    ss_tot = np.sum((y-y.mean())**2)
    r2 = 1 - ss_res/ss_tot if ss_tot > 0 else float('nan')
    return {'MAPE':float(mape),'WMAPE':float(wmape),'sMAPE':float(smape),
            'RMSE':float(rmse),'MAE':float(mae),'R2':float(r2)}
