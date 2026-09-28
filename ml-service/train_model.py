"""Train the Random Forest intrusion-detection model and save it for serving.

Two modes:

  Real model (reproduces the original CICIDS2017 notebook):
      python train_model.py --data-dir "C:/path/to/CICIDS2017/csvs"

  Demo model (synthetic data, only for trying the stack / CI; NOT a real detector):
      python train_model.py --synthetic

Output is a joblib bundle: {"model", "feature_names", "model_version", "threshold", "metrics"}.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
from datetime import datetime, timezone

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, precision_recall_fscore_support
from sklearn.model_selection import train_test_split

from app.features import FEATURE_NAMES

DEFAULT_OUT = os.path.join(os.path.dirname(__file__), "models", "model.joblib")


def load_cicids(data_dir: str) -> tuple[pd.DataFrame, pd.Series]:
    files = sorted(glob.glob(os.path.join(data_dir, "*.csv")))
    if not files:
        raise SystemExit(f"No CSV files found in {data_dir}")
    df = pd.concat((pd.read_csv(f, low_memory=False) for f in files), ignore_index=True)
    df.columns = df.columns.str.strip()  # raw CICIDS headers have leading spaces
    print(f"Merged {len(files)} CSVs -> {df.shape}")

    # Binary target, same as the notebook: BENIGN -> 0 (Normal), everything else -> 1 (Attack)
    y = (df["Label"].str.strip() != "BENIGN").astype(int)
    X = df[FEATURE_NAMES].apply(pd.to_numeric, errors="coerce")

    X = X.replace([np.inf, -np.inf], np.nan).dropna()
    y = y.loc[X.index]
    return X, y


def make_synthetic(n: int = 20_000, seed: int = 42) -> tuple[pd.DataFrame, pd.Series]:
    """Synthetic flows where 'attacks' are short, bursty, SYN-heavy flows. Demo only."""
    rng = np.random.default_rng(seed)
    y = (rng.random(n) < 0.2).astype(int)
    X = pd.DataFrame(rng.lognormal(mean=3, sigma=1.5, size=(n, len(FEATURE_NAMES))), columns=FEATURE_NAMES)
    attack = y == 1
    X.loc[attack, "Flow Duration"] = rng.integers(1, 200, attack.sum())
    X.loc[attack, "SYN Flag Count"] = 1
    X.loc[~attack, "SYN Flag Count"] = 0
    X.loc[attack, "Flow Packets/s"] *= 50
    X.loc[attack, "Destination Port"] = rng.choice([22, 80, 443, 8080], attack.sum())
    return X, pd.Series(y)


def train(X: pd.DataFrame, y: pd.Series, subset: float) -> tuple[RandomForestClassifier, dict]:
    if subset < 1.0:
        X, _, y, _ = train_test_split(X, y, train_size=subset, stratify=y, random_state=42)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, stratify=y, random_state=42)

    model = RandomForestClassifier(n_estimators=100, random_state=42, n_jobs=-1)
    model.fit(X_train.values, y_train.values)

    y_pred = model.predict(X_test.values)
    precision, recall, f1, _ = precision_recall_fscore_support(y_test, y_pred, average="binary", pos_label=1, zero_division=0)
    metrics = {
        "accuracy": round(float(accuracy_score(y_test, y_pred)), 6),
        "attack_precision": round(float(precision), 6),
        "attack_recall": round(float(recall), 6),
        "attack_f1": round(float(f1), 6),
        "confusion_matrix": confusion_matrix(y_test, y_pred).tolist(),
        "train_rows": int(len(X_train)),
        "test_rows": int(len(X_test)),
    }
    return model, metrics


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--data-dir", help="Folder with the CICIDS2017 CSV files")
    src.add_argument("--synthetic", action="store_true", help="Train a DEMO model on synthetic data")
    p.add_argument("--subset", type=float, default=0.2, help="Fraction of data to use (notebook used 0.2)")
    p.add_argument("--threshold", type=float, default=0.5, help="Attack probability threshold")
    p.add_argument("--out", default=DEFAULT_OUT)
    args = p.parse_args()

    if args.synthetic:
        X, y = make_synthetic()
        version, subset = "demo-synthetic", 1.0
    else:
        X, y = load_cicids(args.data_dir)
        version, subset = "rf-cicids2017-" + datetime.now(timezone.utc).strftime("%Y%m%d"), args.subset

    model, metrics = train(X, y, subset)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    joblib.dump(
        {"model": model, "feature_names": FEATURE_NAMES, "model_version": version,
         "threshold": args.threshold, "metrics": metrics},
        args.out, compress=3,
    )
    print(f"Saved {version} -> {args.out}")
    print(json.dumps(metrics, indent=2))


if __name__ == "__main__":
    main()
