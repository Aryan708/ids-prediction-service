"""FastAPI service that serves the Random Forest intrusion-detection model.

Internal service: the Spring Boot API calls it; end users talk to the Java API.
"""
from __future__ import annotations

import math
import os
from contextlib import asynccontextmanager

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

MODEL_PATH = os.getenv("MODEL_PATH", os.path.join(os.path.dirname(__file__), "..", "models", "model.joblib"))
MAX_BATCH = 500

state: dict = {}


def load_bundle(path: str) -> dict:
    if not os.path.exists(path):
        raise RuntimeError(
            f"Model file not found at {path}. Train one first: "
            "python train_model.py --data-dir <CICIDS2017 csv folder>  (or --synthetic for a demo model)"
        )
    return joblib.load(path)


@asynccontextmanager
async def lifespan(app: FastAPI):
    state.update(load_bundle(MODEL_PATH))
    yield
    state.clear()


app = FastAPI(title="IDS Model Service", version="1.0.0", lifespan=lifespan)


class PredictRequest(BaseModel):
    instances: list[dict[str, float]] = Field(..., min_length=1, max_length=MAX_BATCH)


class Prediction(BaseModel):
    label: str  # "ATTACK" or "NORMAL"
    attack_probability: float


class PredictResponse(BaseModel):
    model_version: str
    predictions: list[Prediction]


@app.get("/health")
def health() -> dict:
    return {"status": "UP", "model_version": state.get("model_version")}


@app.get("/metadata")
def metadata() -> dict:
    return {
        "model_version": state["model_version"],
        "feature_names": state["feature_names"],
        "threshold": state["threshold"],
        "metrics": state.get("metrics", {}),
    }


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest) -> PredictResponse:
    names: list[str] = state["feature_names"]
    rows = []
    for i, inst in enumerate(req.instances):
        missing = [n for n in names if n not in inst]
        if missing:
            raise HTTPException(422, f"instance {i}: missing features {missing[:5]}{'...' if len(missing) > 5 else ''}")
        row = [inst[n] for n in names]
        if not all(math.isfinite(v) for v in row):
            raise HTTPException(422, f"instance {i}: features must be finite numbers")
        rows.append(row)

    proba = state["model"].predict_proba(np.asarray(rows, dtype=float))[:, 1]
    threshold = state["threshold"]
    return PredictResponse(
        model_version=state["model_version"],
        predictions=[
            Prediction(label="ATTACK" if p >= threshold else "NORMAL", attack_probability=round(float(p), 6))
            for p in proba
        ],
    )
