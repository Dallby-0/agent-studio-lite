#!/usr/bin/env python3
"""
测试工作流边创建和保存的调试脚本
"""

import requests
import json
import time

# 配置
BASE_URL = "http://localhost:8080"
USERNAME = "user"
PASSWORD = "123"

def test_workflow_edge_creation():
    """测试工作流边创建和保存"""
    print("=== 开始测试工作流边创建和保存 ===")
    
    # 1. 登录获取令牌
    print("1. 登录获取令牌...")
    login_url = f"{BASE_URL}/api/user/login"
    login_payload = {
        "username": USERNAME,
        "password": PASSWORD
    }
    
    response = requests.post(login_url, json=login_payload)
    if response.status_code != 200:
        print(f"登录失败: {response.status_code} - {response.text}")
        return False
    
    token = response.json()["data"]["token"]
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    print("登录成功，获取到令牌")
    
    # 2. 创建工作流（包含边）
    print("\n2. 创建工作流（包含边）...")
    create_workflow_url = f"{BASE_URL}/api/workflows"
    
    # 包含边的工作流数据
    workflow_data = {
        "name": f"测试工作流_边调试_{int(time.time())}",
        "description": "测试边创建和保存",
        "version": "1.0.0",
        "nodes": [
            {
                "id": 1,
                "name": "开始节点",
                "type": "start",
                "positionX": 100,
                "positionY": 200,
                "configJson": "{}"
            },
            {
                "id": 2,
                "name": "大模型调用",
                "type": "llm_call",
                "positionX": 300,
                "positionY": 200,
                "configJson": '{"systemPrompt": "你是一个AI助手", "userPrompt": "请回答：${question}", "outputVar": "answer"}'
            },
            {
                "id": 3,
                "name": "结束节点",
                "type": "end",
                "positionX": 500,
                "positionY": 200,
                "configJson": "{}"
            }
        ],
        "edges": [
            {
                "id": 1,
                "fromNodeId": 1,
                "toNodeId": 2
            },
            {
                "id": 2,
                "fromNodeId": 2,
                "toNodeId": 3
            }
        ]
    }
    
    response = requests.post(create_workflow_url, json=workflow_data, headers=headers)
    print(f"创建工作流响应状态码: {response.status_code}")
    print(f"创建工作流响应: {json.dumps(response.json(), indent=2, ensure_ascii=False)}")
    
    if response.status_code != 200:
        print("创建工作流失败")
        return False
    
    workflow_id = response.json()["data"]["id"]
    print(f"创建工作流成功，ID: {workflow_id}")
    
    # 3. 获取工作流定义，检查边是否保存成功
    print(f"\n3. 获取工作流定义，检查边是否保存成功...")
    get_definition_url = f"{BASE_URL}/api/workflows/{workflow_id}/definition"
    response = requests.get(get_definition_url, headers=headers)
    print(f"获取工作流定义响应状态码: {response.status_code}")
    
    if response.status_code != 200:
        print(f"获取工作流定义失败: {response.text}")
        return False
    
    definition_data = response.json()["data"]
    print(f"工作流定义中的边数量: {len(definition_data.get('edges', []))}")
    print(f"边数据: {json.dumps(definition_data.get('edges', []), indent=2, ensure_ascii=False)}")
    
    # 4. 清理：删除测试工作流
    print(f"\n4. 清理：删除测试工作流...")
    delete_url = f"{BASE_URL}/api/workflows/{workflow_id}"
    response = requests.delete(delete_url, headers=headers)
    print(f"删除工作流响应状态码: {response.status_code}")
    
    if response.status_code == 200:
        print("测试完成，工作流已删除")
    else:
        print(f"删除工作流失败: {response.text}")
    
    return True

if __name__ == "__main__":
    test_workflow_edge_creation()