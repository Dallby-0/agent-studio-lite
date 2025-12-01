from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer
import uvicorn

app = FastAPI()

MODEL_NAME = "BAAI/bge-small-zh-v1.5"
model = SentenceTransformer(MODEL_NAME)


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

