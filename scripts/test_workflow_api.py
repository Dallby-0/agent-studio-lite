#!/usr/bin/env python3
"""
后端工作流API测试脚本
测试工作流相关的所有API接口
"""

import requests
import json
import time
import sys
from datetime import datetime

class WorkflowAPITest:
    def __init__(self, base_url, username="user", password="123"):
        self.base_url = base_url
        self.username = username
        self.password = password
        self.token = None
        self.session = requests.Session()
        self.workflow_id = None
        self.workflow_instance_id = None
        self.setup_logging()
    
    def setup_logging(self):
        """设置日志格式"""
        self.log_file = f"workflow_api_test_{datetime.now().strftime('%Y%m%d_%H%M%S')}.log"
        print(f"日志文件: {self.log_file}")
    
    def log(self, message):
        """记录日志"""
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        log_entry = f"[{timestamp}] {message}"
        print(log_entry)
        with open(self.log_file, "a", encoding="utf-8") as f:
            f.write(log_entry + "\n")
    
    def login(self):
        """登录获取JWT令牌"""
        self.log("开始登录...")
        login_url = f"{self.base_url}/api/user/login"
        payload = {
            "username": self.username,
            "password": self.password
        }
        try:
            response = self.session.post(login_url, json=payload, timeout=10)
            if response.status_code == 200:
                data = response.json()
                # 从data字段中获取token
                self.token = data.get("data", {}).get("token")
                if self.token:
                    self.session.headers.update({"Authorization": f"Bearer {self.token}"})
                    self.log("登录成功，获取到令牌")
                    return True
                else:
                    self.log(f"登录失败: 未返回令牌，响应: {response.text}")
                    return False
            else:
                self.log(f"登录失败: 状态码 {response.status_code}，响应: {response.text}")
                return False
        except Exception as e:
            self.log(f"登录异常: {str(e)}")
            return False
    
    def test_get_all_workflows(self):
        """测试获取所有工作流"""
        self.log("测试获取所有工作流...")
        url = f"{self.base_url}/api/workflows"
        try:
            response = self.session.get(url, timeout=10)
            self.log(f"获取所有工作流响应状态码: {response.status_code}")
            self.log(f"获取所有工作流响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            self.log(f"获取所有工作流异常: {str(e)}")
            return False
    
    def test_create_workflow(self):
        """测试创建工作流"""
        self.log("测试创建工作流...")
        url = f"{self.base_url}/api/workflows"
        payload = {
            "name": f"测试工作流_{int(time.time())}",
            "description": "这是一个测试工作流",
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
                    "configJson": "{\"systemPrompt\": \"你是一个AI助手\", \"userPrompt\": \"请回答：${question}\", \"outputVar\": \"answer\"}"
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
        try:
            response = self.session.post(url, json=payload, timeout=10)
            self.log(f"创建工作流响应状态码: {response.status_code}")
            self.log(f"创建工作流响应: {response.text}")
            if response.status_code == 200:
                data = response.json()
                if isinstance(data, dict) and data.get("code") == 200:
                    workflow_data = data.get("data")
                    if workflow_data:
                        self.workflow_id = workflow_data.get("id")
                        self.log(f"创建工作流成功，工作流ID: {self.workflow_id}")
                        return True
            return False
        except Exception as e:
            self.log(f"创建工作流异常: {str(e)}")
            return False
    
    def test_get_workflow(self):
        """测试获取单个工作流"""
        if not self.workflow_id:
            self.log("跳过获取单个工作流测试: 没有有效的工作流ID")
            return False
        
        self.log(f"测试获取单个工作流，ID: {self.workflow_id}...")
        url = f"{self.base_url}/api/workflows/{self.workflow_id}"
        try:
            response = self.session.get(url, timeout=10)
            self.log(f"获取单个工作流响应状态码: {response.status_code}")
            self.log(f"获取单个工作流响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            self.log(f"获取单个工作流异常: {str(e)}")
            return False
    
    def test_get_workflow_definition(self):
        """测试获取工作流定义"""
        if not self.workflow_id:
            self.log("跳过获取工作流定义测试: 没有有效的工作流ID")
            return False
        
        self.log(f"测试获取工作流定义，ID: {self.workflow_id}...")
        url = f"{self.base_url}/api/workflows/{self.workflow_id}/definition"
        try:
            response = self.session.get(url, timeout=10)
            self.log(f"获取工作流定义响应状态码: {response.status_code}")
            self.log(f"获取工作流定义响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            self.log(f"获取工作流定义异常: {str(e)}")
            return False
    
    def test_start_workflow(self):
        """测试启动工作流"""
        if not self.workflow_id:
            self.log("跳过启动工作流测试: 没有有效的工作流ID")
            return False
        
        self.log(f"测试启动工作流，ID: {self.workflow_id}...")
        url = f"{self.base_url}/api/workflow-instances/start/{self.workflow_id}"
        payload = {
            "question": "测试问题"
        }
        try:
            response = self.session.post(url, json=payload, timeout=10)
            self.log(f"启动工作流响应状态码: {response.status_code}")
            self.log(f"启动工作流响应: {response.text}")
            if response.status_code == 200:
                data = response.json()
                if isinstance(data, dict) and data.get("code") == 200:
                    instance_data = data.get("data")
                    if instance_data:
                        self.workflow_instance_id = instance_data.get("id")
                        self.log(f"启动工作流成功，实例ID: {self.workflow_instance_id}")
                        return True
            return False
        except Exception as e:
            self.log(f"启动工作流异常: {str(e)}")
            return False
    
    def test_get_workflow_instances(self):
        """测试获取所有工作流实例"""
        self.log("测试获取所有工作流实例...")
        url = f"{self.base_url}/api/workflow-instances"
        try:
            response = self.session.get(url, timeout=10)
            self.log(f"获取所有工作流实例响应状态码: {response.status_code}")
            self.log(f"获取所有工作流实例响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            self.log(f"获取所有工作流实例异常: {str(e)}")
            return False
    
    def test_get_instance_logs(self):
        """测试获取工作流实例日志"""
        if not self.workflow_instance_id:
            self.log("跳过获取工作流实例日志测试: 没有有效的工作流实例ID")
            return False
        
        self.log(f"测试获取工作流实例日志，实例ID: {self.workflow_instance_id}...")
        url = f"{self.base_url}/api/workflow-instances/{self.workflow_instance_id}/logs"
        try:
            response = self.session.get(url, timeout=10)
            self.log(f"获取工作流实例日志响应状态码: {response.status_code}")
            self.log(f"获取工作流实例日志响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            self.log(f"获取工作流实例日志异常: {str(e)}")
            return False
    
    def test_delete_workflow(self):
        """测试删除工作流"""
        if not self.workflow_id:
            self.log("跳过删除工作流测试: 没有有效的工作流ID")
            return False
        
        self.log(f"测试删除工作流，ID: {self.workflow_id}...")
        url = f"{self.base_url}/api/workflows/{self.workflow_id}"
        try:
            response = self.session.delete(url, timeout=10)
            self.log(f"删除工作流响应状态码: {response.status_code}")
            self.log(f"删除工作流响应: {response.text}")
            return response.status_code == 200
        except Exception as e:
            self.log(f"删除工作流异常: {str(e)}")
            return False
    
    def run_all_tests(self):
        """运行所有测试用例"""
        self.log("开始运行所有测试用例...")
        results = []
        
        # 登录
        if not self.login():
            self.log("登录失败，无法继续测试")
            return False
        
        # 测试用例列表
        test_cases = [
            ("获取所有工作流", self.test_get_all_workflows),
            ("创建工作流", self.test_create_workflow),
            ("获取单个工作流", self.test_get_workflow),
            ("获取工作流定义", self.test_get_workflow_definition),
            ("启动工作流", self.test_start_workflow),
            ("获取所有工作流实例", self.test_get_workflow_instances),
            ("获取工作流实例日志", self.test_get_instance_logs),
            ("删除工作流", self.test_delete_workflow),
        ]
        
        # 执行测试用例
        for test_name, test_func in test_cases:
            self.log(f"\n=== 开始测试: {test_name} ===")
            result = test_func()
            results.append((test_name, result))
            self.log(f"=== 测试完成: {test_name}，结果: {'通过' if result else '失败'} ===")
        
        # 汇总结果
        self.log("\n=== 测试结果汇总 ===")
        passed = sum(1 for _, result in results if result)
        total = len(results)
        self.log(f"通过测试: {passed}/{total}")
        
        for test_name, result in results:
            status = "通过" if result else "失败"
            self.log(f"{test_name}: {status}")
        
        return passed == total

if __name__ == "__main__":
    # 配置测试参数
    BASE_URL = "http://localhost:8080"
    USERNAME = "user"
    PASSWORD = "123"
    
    # 解析命令行参数
    if len(sys.argv) > 1:
        BASE_URL = sys.argv[1]
    if len(sys.argv) > 2:
        USERNAME = sys.argv[2]
    if len(sys.argv) > 3:
        PASSWORD = sys.argv[3]
    
    # 创建测试实例
    test = WorkflowAPITest(BASE_URL, USERNAME, PASSWORD)
    
    # 运行所有测试
    success = test.run_all_tests()
    
    # 退出脚本
    sys.exit(0 if success else 1)