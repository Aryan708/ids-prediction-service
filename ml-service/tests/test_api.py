import os
import subprocess
import sys

import pytest
from fastapi.testclient import TestClient

HERE = os.path.dirname(__file__)
MODEL = os.path.join(HERE, "demo_model.joblib")


@pytest.fixture(scope="session")
def client():
    if not os.path.exists(MODEL):
        subprocess.run([sys.executable, "train_model.py", "--synthetic", "--out", MODEL],
                       cwd=os.path.join(HERE, ".."), check=True)
    os.environ["MODEL_PATH"] = MODEL
    import importlib
    import app.main as main
    importlib.reload(main)
    with TestClient(main.app) as c:
        yield c


def flow(**overrides):
    from app.features import FEATURE_NAMES
    f = {n: 10.0 for n in FEATURE_NAMES}
    f.update(overrides)
    return f


def test_health(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["model_version"] == "demo-synthetic"


def test_metadata_lists_78_features(client):
    body = client.get("/metadata").json()
    assert len(body["feature_names"]) == 78
    assert 0 < body["threshold"] < 1


def test_predict_returns_one_result_per_instance(client):
    r = client.post("/predict", json={"instances": [flow(), flow(**{"SYN Flag Count": 1, "Flow Duration": 5})]})
    assert r.status_code == 200
    preds = r.json()["predictions"]
    assert len(preds) == 2
    for p in preds:
        assert p["label"] in {"ATTACK", "NORMAL"}
        assert 0.0 <= p["attack_probability"] <= 1.0


def test_label_matches_threshold(client):
    threshold = client.get("/metadata").json()["threshold"]
    p = client.post("/predict", json={"instances": [flow()]}).json()["predictions"][0]
    assert (p["label"] == "ATTACK") == (p["attack_probability"] >= threshold)


def test_missing_feature_is_rejected(client):
    f = flow()
    del f["Flow Duration"]
    r = client.post("/predict", json={"instances": [f]})
    assert r.status_code == 422
    assert "Flow Duration" in r.json()["detail"]


def test_empty_batch_is_rejected(client):
    assert client.post("/predict", json={"instances": []}).status_code == 422
