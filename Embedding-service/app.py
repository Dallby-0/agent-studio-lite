from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import uvicorn
import os

app = FastAPI()

MODEL_NAME = "BAAI/bge-small-zh-v1.5"
# 优先使用本地模型路径，如果不存在或不完整则从 Hugging Face Hub 加载
LOCAL_MODEL_PATH = "./model/bge-small-zh-v1.5"

def is_model_complete(model_path):
    """检查模型目录是否包含必需的权重文件"""
    if not os.path.exists(model_path) or not os.path.isdir(model_path):
        return False
    # 检查是否存在模型权重文件
    required_files = ["pytorch_model.bin", "model.safetensors"]
    for file in required_files:
        if os.path.exists(os.path.join(model_path, file)):
            return True
    return False

# 尝试使用本地模型，如果不完整则使用 Hugging Face Hub
if is_model_complete(LOCAL_MODEL_PATH):
    print(f"使用本地模型: {LOCAL_MODEL_PATH}")
    model = SentenceTransformer(LOCAL_MODEL_PATH)
else:
    print(f"本地模型不完整，从 Hugging Face Hub 加载: {MODEL_NAME}")
    try:
        model = SentenceTransformer(MODEL_NAME)
    except Exception as e:
        print(f"从 Hugging Face Hub 加载模型失败: {e}")
        print(f"请确保本地模型目录 {LOCAL_MODEL_PATH} 包含完整的模型文件，或者网络连接正常")
        raise


class EmbeddingRequest(BaseModel):
    input: list[str]


@app.get("/health")
async def health():
    return {"status": "ok", "model": MODEL_NAME}


@app.post("/embeddings")
async def create_embeddings(req: EmbeddingRequest):
    # normalize_embeddings=True 方便后续用余弦相似度
    vectors = model.encode(req.input, normalize_embeddings=True)
    data = [
        {"index": idx, "embedding": vec.tolist()}
        for idx, vec in enumerate(vectors)
    ]
    return {"data": data, "model": MODEL_NAME}


if __name__ == "__main__":
    uvicorn.run("app:app", host="0.0.0.0", port=9000)

