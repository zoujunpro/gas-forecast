"""用江苏城燃全部客户构造公共需求指数，并预测指定客户。"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.font_manager import FontProperties
import numpy as np
import pandas as pd

from gas_model_platform.models.short_agent import city_gas_v3_engine as engine


PROPHET_PARAMS = {
    "seasonality_mode": "additive",
    "changepoint_prior_scale": 0.05,
    "seasonality_prior_scale": 10.0,
    "holidays_prior_scale": 20.0,
}


def build_common(panel: pd.DataFrame, scales: pd.Series) -> tuple[pd.DataFrame, list[str]]:
    data = panel[panel["customer_code"].isin(scales.index)].copy()
    data["sales_index"] = data["gas_sales"] / data["customer_code"].map(scales)
    common = data.groupby("ds", as_index=False).agg(
        y=("sales_index", "median"),
        tempmax=("tempmax", "mean"), tempmin=("tempmin", "mean"), temp=("temp", "mean"),
        customer_count=("customer_code", "nunique"),
    )
    weather = ["tempmax", "tempmin", "temp"]
    common[weather] = common[weather].interpolate(limit_direction="both").ffill().bfill()
    common, engineered = engine.add_engineered_features(common, weather)
    regs = [*weather, *engineered]
    common["ds"] -= pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    return common, regs


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input_csv")
    parser.add_argument("--customer-code", default="CUS000070")
    parser.add_argument("--horizon", type=int, default=50)
    parser.add_argument("--output-dir", default="outputs/jiangsu-city-gas-shared")
    args = parser.parse_args()

    raw = pd.read_csv(args.input_csv, sep="\t")
    raw["ds"] = pd.to_datetime(raw["stat_date"])
    raw["gas_sales"] = pd.to_numeric(raw["gas_sales"], errors="coerce")
    raw = raw.dropna(subset=["ds", "customer_code", "gas_sales"])
    raw = raw.groupby(["ds", "customer_code", "customer_name"], as_index=False).agg(
        gas_sales=("gas_sales", "sum"),
        tempmax=("tempmax", "mean"), tempmin=("tempmin", "mean"), temp=("temp", "mean"),
    )

    scales = raw.groupby("customer_code")["gas_sales"].median().replace(0, np.nan).dropna()
    common, regs = build_common(raw, scales)

    result, _, _ = engine.run_single_forecast_v3(
        common[["ds", "y", *regs]], regs, args.horizon, "D", PROPHET_PARAMS, ar_order=4
    )
    future = result[result["y"].isna()].copy()
    future["forecast_date"] = future["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)

    target = raw[raw["customer_code"] == args.customer_code].sort_values("ds")
    if target.empty:
        raise ValueError(f"客户不存在或没有销量数据：{args.customer_code}")
    target_name = str(target["customer_name"].iloc[-1])
    target_scale = float(scales.loc[args.customer_code])
    future["prediction"] = (future["yhat"] * target_scale).clip(lower=0)
    future["lower"] = (future["yhat_lower"] * target_scale).clip(lower=0)
    future["upper"] = (future["yhat_upper"] * target_scale).clip(lower=0)

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    csv_path = output_dir / f"{args.customer_code}_future_{args.horizon}.csv"
    future[["forecast_date", "prediction", "lower", "upper"]].to_csv(csv_path, index=False)

    history = target.tail(90)
    fig, ax = plt.subplots(figsize=(14, 6))
    font = FontProperties(fname="/System/Library/Fonts/Hiragino Sans GB.ttc")
    ax.plot(history["ds"], history["gas_sales"], label="历史销量", color="#5470c6")
    ax.plot(future["forecast_date"], future["prediction"], label="共享模型预测", color="#ee6666")
    ax.fill_between(future["forecast_date"], future["lower"], future["upper"], color="#ee6666", alpha=0.15)
    ax.axvline(target["ds"].max(), color="#999", linestyle="--")
    ax.set_title(f"江苏城燃共享模型 - {target_name}（{args.customer_code}）", fontproperties=font)
    ax.legend(prop=font)
    ax.grid(alpha=0.2)
    fig.autofmt_xdate()
    fig.tight_layout()
    png_path = output_dir / f"{args.customer_code}_future_{args.horizon}.png"
    fig.savefig(png_path, dpi=160)

    # 严格留出最后 horizon 天：尺度与公共趋势都只使用截止日前的数据。
    cutoff = raw["ds"].max() - pd.Timedelta(days=args.horizon)
    train_raw = raw[raw["ds"] <= cutoff]
    backtest_scales = train_raw.groupby("customer_code")["gas_sales"].median().replace(0, np.nan).dropna()
    backtest_common, backtest_regs = build_common(raw, backtest_scales)
    shifted_cutoff = cutoff - pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    backtest_train = backtest_common[backtest_common["ds"] <= shifted_cutoff]
    backtest_result, _, _ = engine.run_single_forecast_v3(
        backtest_train[["ds", "y", *backtest_regs]], backtest_regs,
        args.horizon, "D", PROPHET_PARAMS, ar_order=4,
    )
    backtest = backtest_result[backtest_result["y"].isna()].copy()
    backtest["stat_date"] = backtest["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    backtest_scale = float(backtest_scales.loc[args.customer_code])
    backtest["predicted"] = (backtest["yhat"] * backtest_scale).clip(lower=0)
    actual = target[target["ds"] > cutoff][["ds", "gas_sales"]].rename(
        columns={"ds": "stat_date", "gas_sales": "actual"}
    )
    backtest = backtest.merge(actual, on="stat_date", how="inner")
    backtest["error"] = backtest["predicted"] - backtest["actual"]
    rmse = float(np.sqrt(np.mean(np.square(backtest["error"]))))
    nonzero = backtest["actual"] != 0
    mape = float(np.mean(np.abs(backtest.loc[nonzero, "error"] / backtest.loc[nonzero, "actual"])) * 100)
    backtest_csv = output_dir / f"{args.customer_code}_backtest_{args.horizon}.csv"
    backtest[["stat_date", "actual", "predicted", "error"]].to_csv(backtest_csv, index=False)

    fig_bt, ax_bt = plt.subplots(figsize=(14, 6))
    ax_bt.plot(backtest["stat_date"], backtest["actual"], label="实际销量", color="#5470c6", marker="o", markersize=3)
    ax_bt.plot(backtest["stat_date"], backtest["predicted"], label="回测预测", color="#ee6666", marker="o", markersize=3)
    ax_bt.set_title(
        f"江苏城燃全客户共享模型回测 - {target_name}（RMSE={rmse:.2f}，MAPE={mape:.2f}%）",
        fontproperties=font,
    )
    ax_bt.legend(prop=font)
    ax_bt.grid(alpha=0.2)
    fig_bt.autofmt_xdate()
    fig_bt.tight_layout()
    backtest_png = output_dir / f"{args.customer_code}_backtest_{args.horizon}.png"
    fig_bt.savefig(backtest_png, dpi=160)

    # 江苏省城燃总量滚动回测：每个日期汇总全部客户销量，展示整体模型本身的效果。
    province = raw.groupby("ds", as_index=False).agg(
        y=("gas_sales", "sum"),
        tempmax=("tempmax", "mean"), tempmin=("tempmin", "mean"), temp=("temp", "mean"),
        customer_count=("customer_code", "nunique"),
    )
    province_weather = ["tempmax", "tempmin", "temp"]
    province[province_weather] = province[province_weather].interpolate(limit_direction="both").ffill().bfill()
    province, province_engineered = engine.add_engineered_features(province, province_weather)
    province_regs = [*province_weather, *province_engineered]
    province["ds"] -= pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    province_eval_end = province["ds"].max()
    province_eval_start = province_eval_end - pd.Timedelta(days=364)
    province_backtest, province_rmse, province_mape = engine.rolling_backtest(
        df_all=province[["ds", "y", *province_regs]], regs=province_regs,
        horizon=args.horizon, freq="D", min_train_days=90, stride=args.horizon,
        eval_start=province_eval_start, eval_end=province_eval_end,
        prophet_params=PROPHET_PARAMS, ar_order=4, version="v3",
    )
    province_backtest["stat_date"] = province_backtest["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    province_backtest_csv = output_dir / "jiangsu_all_customers_backtest.csv"
    province_backtest[["stat_date", "y", "yhat", "yhat_lower", "yhat_upper"]].rename(
        columns={"y": "actual", "yhat": "predicted", "yhat_lower": "lower", "yhat_upper": "upper"}
    ).to_csv(province_backtest_csv, index=False)
    fig_province, ax_province = plt.subplots(figsize=(16, 7))
    ax_province.plot(province_backtest["stat_date"], province_backtest["y"], label="江苏城燃全部客户实际总量", color="#5470c6", linewidth=1.8)
    ax_province.plot(province_backtest["stat_date"], province_backtest["yhat"], label="滚动回测预测总量", color="#ee6666", linewidth=1.8)
    ax_province.fill_between(
        province_backtest["stat_date"], province_backtest["yhat_lower"], province_backtest["yhat_upper"],
        color="#ee6666", alpha=0.12,
    )
    ax_province.set_title(
        f"江苏城燃全部客户总量滚动回测（RMSE={province_rmse:.2f}，MAPE={province_mape:.2f}%）",
        fontproperties=font,
    )
    ax_province.legend(prop=font)
    ax_province.grid(alpha=0.2)
    fig_province.autofmt_xdate()
    fig_province.tight_layout()
    province_backtest_png = output_dir / "jiangsu_all_customers_backtest.png"
    fig_province.savefig(province_backtest_png, dpi=160)

    # 严格使用省级总量模型预测，再按训练期最近365天客户销量占比拆分到目标客户。
    province_future_result, _, _ = engine.run_single_forecast_v3(
        province[["ds", "y", *province_regs]], province_regs,
        args.horizon, "D", PROPHET_PARAMS, ar_order=4,
    )
    province_future = province_future_result[province_future_result["y"].isna()].copy()
    province_future["forecast_date"] = province_future["ds"] + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
    latest = raw["ds"].max()
    share_start = latest - pd.Timedelta(days=364)
    share_rows = raw[raw["ds"].between(share_start, latest)]
    target_share = float(
        share_rows.loc[share_rows["customer_code"] == args.customer_code, "gas_sales"].sum()
        / share_rows["gas_sales"].sum()
    )
    province_future["province_prediction"] = province_future["yhat"].clip(lower=0)
    province_future["prediction"] = province_future["province_prediction"] * target_share
    province_allocated_csv = output_dir / f"{args.customer_code}_province_total_allocated_future_{args.horizon}.csv"
    province_future[["forecast_date", "province_prediction", "prediction"]].to_csv(province_allocated_csv, index=False)

    # 每个滚动回测折使用各自训练截止日前365天占比，避免使用未来客户数据。
    def historical_share(shifted_cutoff: pd.Timestamp) -> float:
        original_cutoff = shifted_cutoff + pd.Timedelta(days=engine.DS_SHIFT_DAYS)
        begin = original_cutoff - pd.Timedelta(days=364)
        rows = raw[raw["ds"].between(begin, original_cutoff)]
        total = rows["gas_sales"].sum()
        customer = rows.loc[rows["customer_code"] == args.customer_code, "gas_sales"].sum()
        return float(customer / total) if total else 0.0

    province_backtest["target_share"] = province_backtest["cutoff"].map(historical_share)
    province_backtest["customer_predicted"] = province_backtest["yhat"].clip(lower=0) * province_backtest["target_share"]
    target_actual = target[["ds", "gas_sales"]].rename(columns={"ds": "stat_date", "gas_sales": "customer_actual"})
    allocated_backtest = province_backtest.merge(target_actual, on="stat_date", how="inner")
    allocated_backtest["error"] = allocated_backtest["customer_predicted"] - allocated_backtest["customer_actual"]
    allocated_rmse = float(np.sqrt(np.mean(np.square(allocated_backtest["error"]))))
    allocated_nonzero = allocated_backtest["customer_actual"] != 0
    allocated_mape = float(np.mean(np.abs(
        allocated_backtest.loc[allocated_nonzero, "error"] / allocated_backtest.loc[allocated_nonzero, "customer_actual"]
    )) * 100)
    allocated_backtest_csv = output_dir / f"{args.customer_code}_province_total_allocated_backtest.csv"
    allocated_backtest[["stat_date", "customer_actual", "customer_predicted", "target_share", "error"]].to_csv(allocated_backtest_csv, index=False)

    fig_allocated, ax_allocated = plt.subplots(figsize=(16, 7))
    recent_history = target.tail(90)
    ax_allocated.plot(recent_history["ds"], recent_history["gas_sales"], label="无锡华润历史销量", color="#5470c6")
    ax_allocated.plot(province_future["forecast_date"], province_future["prediction"], label="省级总量模型拆分预测", color="#ee6666")
    ax_allocated.axvline(latest, color="#999", linestyle="--")
    ax_allocated.set_title(
        f"江苏城燃省级总量模型预测 - {target_name}（历史占比={target_share:.2%}）",
        fontproperties=font,
    )
    ax_allocated.legend(prop=font)
    ax_allocated.grid(alpha=0.2)
    fig_allocated.autofmt_xdate()
    fig_allocated.tight_layout()
    province_allocated_png = output_dir / f"{args.customer_code}_province_total_allocated_future_{args.horizon}.png"
    fig_allocated.savefig(province_allocated_png, dpi=160)

    print(f"eligible_customers={len(scales)}")
    print(f"history_start={raw['ds'].min().date()}")
    print(f"history_end={raw['ds'].max().date()}")
    print(f"target={target_name}({args.customer_code})")
    print(f"target_scale={target_scale:.6f}")
    print(f"forecast_min={future['prediction'].min():.6f}")
    print(f"forecast_max={future['prediction'].max():.6f}")
    print(f"forecast_mean={future['prediction'].mean():.6f}")
    print(f"backtest_rmse={rmse:.6f}")
    print(f"backtest_mape={mape:.6f}")
    print(f"backtest_csv={backtest_csv.resolve()}")
    print(f"backtest_png={backtest_png.resolve()}")
    print(f"province_backtest_rmse={province_rmse:.6f}")
    print(f"province_backtest_mape={province_mape:.6f}")
    print(f"province_backtest_csv={province_backtest_csv.resolve()}")
    print(f"province_backtest_png={province_backtest_png.resolve()}")
    print(f"target_share={target_share:.8f}")
    print(f"allocated_backtest_rmse={allocated_rmse:.6f}")
    print(f"allocated_backtest_mape={allocated_mape:.6f}")
    print(f"province_allocated_csv={province_allocated_csv.resolve()}")
    print(f"province_allocated_png={province_allocated_png.resolve()}")
    print(f"allocated_backtest_csv={allocated_backtest_csv.resolve()}")
    print(f"csv={csv_path.resolve()}")
    print(f"png={png_path.resolve()}")


if __name__ == "__main__":
    main()
