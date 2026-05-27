from pathlib import Path
import shutil

import onnx
from transformers import XLMRobertaTokenizer
from onnxruntime_extensions import gen_processing_models

MODEL_ID = "intfloat/multilingual-e5-small"

LOCAL_BACKBONE_MODEL_PATH = Path(
    r"C:\Users\AKubyshenko\Downloads\model_qint8_avx512_vnni.onnx"
)

OUT_DIR = Path(r"C:\Users\AKubyshenko\Downloads\android_e5_onnx")
OUT_DIR.mkdir(exist_ok=True)

MAX_LENGTH = 512

TOKENIZER_ONNX_PATH = OUT_DIR / "e5_tokenizer.onnx"
BACKBONE_ONNX_PATH = OUT_DIR / "e5_backbone.onnx"

tokenizer = XLMRobertaTokenizer.from_pretrained(MODEL_ID)

print("Tokenizer class:", tokenizer.__class__.__name__)

pre_model, _ = gen_processing_models(
    tokenizer,
    pre_kwargs={
        "padding": "max_length",
        "truncation": True,
        "max_length": MAX_LENGTH,
        "return_token_type_ids": False,
    },
)

with open(TOKENIZER_ONNX_PATH, "wb") as f:
    f.write(pre_model.SerializeToString())

print("Saved tokenizer:", TOKENIZER_ONNX_PATH)

if not LOCAL_BACKBONE_MODEL_PATH.exists():
    raise FileNotFoundError(f"Model file not found: {LOCAL_BACKBONE_MODEL_PATH}")

shutil.copyfile(LOCAL_BACKBONE_MODEL_PATH, BACKBONE_ONNX_PATH)

print("Copied backbone from:", LOCAL_BACKBONE_MODEL_PATH)
print("Saved backbone as:", BACKBONE_ONNX_PATH)

tok = onnx.load(TOKENIZER_ONNX_PATH)
back = onnx.load(BACKBONE_ONNX_PATH)

print("\nTokenizer inputs:")
for i in tok.graph.input:
    print(" ", i.name)

print("\nTokenizer outputs:")
for o in tok.graph.output:
    print(" ", o.name)

print("\nBackbone inputs:")
for i in back.graph.input:
    print(" ", i.name)

print("\nBackbone outputs:")
for o in back.graph.output:
    print(" ", o.name)