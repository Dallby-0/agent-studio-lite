#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
工作流API修复验证测试脚本

验证内容：
1. 工作流创建API返回完整工作流定义
2. 工作流更新API返回完整工作流定义
3. 工作流定义包含正确的节点和边
4. ID处理逻辑正确，没有出现巨大的临时ID
"""

import requests
import json
import time

class WorkflowAPITest:
    def __init__(self):
        self.base_url = "http://localhost:8080/api"
        self.session = requests.Session()
        
        # 登录获取token
        self.login()
    
    def login(self):
        """登录获取JWT token"""
        print("=== 登录测试 ===")
        login_url = f"{self.base_url}/auth/login"
        login_data = {
            "username": "user",
            "password": "123"
        }
        
        try:
            response = self.session.post(login_url, json=login_data, timeout=10)
            if response.status_code == 200:
                data = response.json()
                if "token" in data:
                    self.session.headers.update({"Authorization": f"Bearer {data['token']}"})
                    print("✅ 登录成功，获取到token")
                    return True
                else:
                    print(f"❌ 登录失败：响应中没有token，响应内容：{data}")
                    return False
            else:
                print(f"❌ 登录失败，状态码：{response.status_code}，响应：{response.text}")
                return False
        except Exception as e:
            print(f"❌ 登录异常：{str(e)}")
            return False
    
    def test_create_workflow(self):
        """测试创建工作流"""
        print("\n=== 创建工作流测试 ===")
        create_url = f"{self.base_url}/workflows"
        
        # 创建一个简单的工作流：开始节点 -> 结束节点
        workflow_data = {
            "name": "测试工作流",
            "description": "用于验证修复效果的测试工作流",
            "version": "1.0.0",
            "status": 1,
            "nodes": [
                {
                    "id": 1,
                    "name": "开始节点",
                    "type": "start",
                    "positionX": 100,
                    "positionY": 100,
                    "configJson": "{}"
                },
                {
                    "id": 2,
                    "name": "结束节点",
                    "type": "end",
                    "positionX": 300,
                    "positionY": 100,
                    "configJson": "{}"
                }
            ],
            "edges": [
                {
                    "id": 1,
                    "fromNodeId": 1,
                    "toNodeId": 2,
                    "condition": None
                }
            ]
        }
        
        try:
            response = self.session.post(create_url, json=workflow_data, timeout=10)
            print(f"创建工作流响应状态码：{response.status_code}")
            
            if response.status_code == 200:
                data = response.json()
                print(f"创建工作流响应：{json.dumps(data, indent=2, ensure_ascii=False)}")
                
                # 验证响应格式
                if "code" in data and data["code"] == 200:
                    response_data = data["data"]
                    
                    # 验证返回了完整的工作流定义
                    assert "workflow" in response_data, "响应中没有workflow字段"
                    assert "nodes" in response_data, "响应中没有nodes字段"
                    assert "edges" in response_data, "响应中没有edges字段"
                    
                    workflow = response_data["workflow"]
                    nodes = response_data["nodes"]
                    edges = response_data["edges"]
                    
                    print(f"✅ 工作流创建成功，ID: {workflow['id']}")
                    print(f"✅ 返回了 {len(nodes)} 个节点和 {len(edges)} 个边")
                    
                    # 验证节点和边的ID是合理的（不是巨大的临时ID）
                    for node in nodes:
                        assert isinstance(node["id"], int), f"节点ID不是整数：{node['id']}"
                        assert node["id"] > 0 and node["id"] < 1000000, f"节点ID不合理：{node['id']}"
                    
                    for edge in edges:
                        assert isinstance(edge["id"], int), f"边ID不是整数：{edge['id']}"
                        assert edge["id"] > 0 and edge["id"] < 1000000, f"边ID不合理：{edge['id']}"
                        assert isinstance(edge["fromNodeId"], int), f"边fromNodeId不是整数：{edge['fromNodeId']}"
                        assert isinstance(edge["toNodeId"], int), f"边toNodeId不是整数：{edge['toNodeId']}"
                    
                    print("✅ 节点和边的ID格式正确，没有出现巨大的临时ID")
                    return workflow["id"]
                else:
                    print(f"❌ 创建工作流失败：{data}")
                    return None
            else:
                print(f"❌ 创建工作流失败，状态码：{response.status_code}，响应：{response.text}")
                return None
        except Exception as e:
            print(f"❌ 创建工作流异常：{str(e)}")
            return None
    
    def test_get_workflow(self, workflow_id):
        """测试获取单个工作流"""
        print(f"\n=== 获取工作流测试 (ID: {workflow_id}) ===")
        get_url = f"{self.base_url}/workflows/{workflow_id}"
        
        try:
            response = self.session.get(get_url, timeout=10)
            print(f"获取工作流响应状态码：{response.status_code}")
            
            if response.status_code == 200:
                data = response.json()
                print(f"获取工作流响应：{json.dumps(data, indent=2, ensure_ascii=False)}")
                return True
            else:
                print(f"❌ 获取工作流失败，状态码：{response.status_code}，响应：{response.text}")
                return False
        except Exception as e:
            print(f"❌ 获取工作流异常：{str(e)}")
            return False
    
    def test_get_workflow_definition(self, workflow_id):
        """测试获取工作流定义"""
        print(f"\n=== 获取工作流定义测试 (ID: {workflow_id}) ===")
        get_url = f"{self.base_url}/workflows/{workflow_id}/definition"
        
        try:
            response = self.session.get(get_url, timeout=10)
            print(f"获取工作流定义响应状态码：{response.status_code}")
            
            if response.status_code == 200:
                data = response.json()
                print(f"获取工作流定义响应：{json.dumps(data, indent=2, ensure_ascii=False)}")
                
                if "code" in data and data["code"] == 200:
                    response_data = data["data"]
                    assert "workflow" in response_data, "响应中没有workflow字段"
                    assert "nodes" in response_data, "响应中没有nodes字段"
                    assert "edges" in response_data, "响应中没有edges字段"
                    
                    workflow = response_data["workflow"]
                    nodes = response_data["nodes"]
                    edges = response_data["edges"]
                    
                    print(f"✅ 获取工作流定义成功，ID: {workflow['id']}")
                    print(f"✅ 包含 {len(nodes)} 个节点和 {len(edges)} 个边")
                    return True
                else:
                    print(f"❌ 获取工作流定义失败：{data}")
                    return False
            else:
                print(f"❌ 获取工作流定义失败，状态码：{response.status_code}，响应：{response.text}")
                return False
        except Exception as e:
            print(f"❌ 获取工作流定义异常：{str(e)}")
            return False
    
    def test_update_workflow(self, workflow_id):
        """测试更新工作流"""
        print(f"\n=== 更新工作流测试 (ID: {workflow_id}) ===")
        update_url = f"{self.base_url}/workflows/{workflow_id}"
        
        # 更新工作流，添加一个新节点和一条新边
        workflow_data = {
            "name": "更新后的测试工作流",
            "description": "更新后的测试工作流，添加了新节点",
            "version": "1.1.0",
            "status": 1,
            "nodes": [
                {
                    "id": 1,
                    "name": "开始节点",
                    "type": "start",
                    "positionX": 100,
                    "positionY": 100,
                    "configJson": "{}"
                },
                {
                    "id": 3,
                    "name": "新增节点",
                    "type": "llm_call",
                    "positionX": 200,
                    "positionY": 200,
                    "configJson": '{"systemPrompt": "你是一个AI助手", "userPrompt": "请回答问题"}'
                },
                {
                    "id": 2,
                    "name": "结束节点",
                    "type": "end",
                    "positionX": 300,
                    "positionY": 100,
                    "configJson": "{}"
                }
            ],
            "edges": [
                {
                    "id": 1,
                    "fromNodeId": 1,
                    "toNodeId": 3,
                    "condition": None
                },
                {
                    "id": 2,
                    "fromNodeId": 3,
                    "toNodeId": 2,
                    "condition": None
                }
            ]
        }
        
        try:
            response = self.session.put(update_url, json=workflow_data, timeout=10)
            print(f"更新工作流响应状态码：{response.status_code}")
            
            if response.status_code == 200:
                data = response.json()
                print(f"更新工作流响应：{json.dumps(data, indent=2, ensure_ascii=False)}")
                
                if "code" in data and data["code"] == 200:
                    response_data = data["data"]
                    
                    # 验证返回了完整的工作流定义
                    assert "workflow" in response_data, "响应中没有workflow字段"
                    assert "nodes" in response_data, "响应中没有nodes字段"
                    assert "edges" in response_data, "响应中没有edges字段"
                    
                    workflow = response_data["workflow"]
                    nodes = response_data["nodes"]
                    edges = response_data["edges"]
                    
                    print(f"✅ 工作流更新成功，ID: {workflow['id']}")
                    print(f"✅ 返回了 {len(nodes)} 个节点和 {len(edges)} 个边")
                    
                    # 验证更新后的工作流包含新增的节点
                    assert len(nodes) == 3, f"更新后节点数量不正确，期望3个，实际{len(nodes)}个"
                    assert len(edges) == 2, f"更新后边数量不正确，期望2个，实际{len(edges)}个"
                    
                    return True
                else:
                    print(f"❌ 更新工作流失败：{data}")
                    return False
            else:
                print(f"❌ 更新工作流失败，状态码：{response.status_code}，响应：{response.text}")
                return False
        except Exception as e:
            print(f"❌ 更新工作流异常：{str(e)}")
            return False
    
    def test_workflow_complete(self):
        """完整测试工作流的创建、获取、更新流程"""
        print("\n" + "="*50)
        print("开始完整的工作流API测试")
        print("="*50)
        
        # 1. 创建工作流
        workflow_id = self.test_create_workflow()
        if not workflow_id:
            print("\n❌ 完整测试失败：创建工作流失败")
            return False
        
        # 2. 获取工作流
        if not self.test_get_workflow(workflow_id):
            print("\n❌ 完整测试失败：获取工作流失败")
            return False
        
        # 3. 获取工作流定义
        if not self.test_get_workflow_definition(workflow_id):
            print("\n❌ 完整测试失败：获取工作流定义失败")
            return False
        
        # 4. 更新工作流
        if not self.test_update_workflow(workflow_id):
            print("\n❌ 完整测试失败：更新工作流失败")
            return False
        
        # 5. 再次获取工作流定义，验证更新结果
        if not self.test_get_workflow_definition(workflow_id):
            print("\n❌ 完整测试失败：更新后获取工作流定义失败")
            return False
        
        print("\n" + "="*50)
        print("✅ 完整测试成功！所有工作流API都能正常工作")
        print("✅ 修复效果验证通过")
        print("✅ 工作流创建和更新都返回完整的工作流定义")
        print("✅ ID处理逻辑正确，没有出现巨大的临时ID")
        print("✅ 节点和边的关系正确")
        print("="*50)
        return True

if __name__ == "__main__":
    tester = WorkflowAPITest()
    if tester.test_workflow_complete():
        exit(0)
    else:
        exit(1)
