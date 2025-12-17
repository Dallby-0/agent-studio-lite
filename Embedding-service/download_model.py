#!/usr/bin/env python3
"""
下载 BGE small zh v1.5 模型的完整文件
"""
import os
from sentence_transformers import SentenceTransformer

MODEL_NAME = "BAAI/bge-small-zh-v1.5"
LOCAL_MODEL_PATH = "./model/bge-small-zh-v1.5"

print(f"开始下载模型: {MODEL_NAME}")
print(f"保存路径: {os.path.abspath(LOCAL_MODEL_PATH)}")

# 确保目录存在
os.makedirs(LOCAL_MODEL_PATH, exist_ok=True)

try:
    # 下载模型（会自动保存到本地缓存，然后我们可以复制到目标目录）
    print("正在从 Hugging Face 下载模型文件...")
    model = SentenceTransformer(MODEL_NAME, cache_folder="./model")
    
    # 获取模型路径并复制文件
    # sentence-transformers 会将模型保存到缓存目录
    # 我们需要使用 save 方法将模型保存到指定路径
    print(f"将模型保存到: {LOCAL_MODEL_PATH}")
    model.save(LOCAL_MODEL_PATH)
    
    print("模型下载完成！")
    print(f"模型已保存到: {os.path.abspath(LOCAL_MODEL_PATH)}")
    
except Exception as e:
    print(f"下载失败: {e}")
    print("\n如果网络连接有问题，您可以：")
    print("1. 使用代理或 VPN")
    print("2. 使用 Hugging Face CLI 工具下载:")
    print(f"   huggingface-cli download {MODEL_NAME} --local-dir {LOCAL_MODEL_PATH}")
    raise

