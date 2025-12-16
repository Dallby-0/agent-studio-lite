#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
工作流API测试脚本
"""

import requests
import json
import time

BASE_URL = "http://localhost:8080"
USERNAME = "user"
PASSWORD = "123"

class WorkflowAPITester:
    def __init__(self):
        self.base_url = BASE_URL
        self.token = None
        self.session = requests.Session()
    
    def login(self):
        """登录获取token"""
        print("正在登录...")
        url = f"{self.base_url}/api/user/login"
        data = {
            "username": USERNAME,
            "password": PASSWORD
        }
        
        try:
            response = self.session.post(url, json=data)
            if response.status_code == 200:
                result = response.json()
                if result["code"] == 200:
                    self.token = result["data"]["token"]
                    self.session.headers.update({
                        "Authorization": f"Bearer {self.token}"
                    })
                    print("登录成功！")
                    return True
                else:
                    print(f"登录失败：{result['msg']}")
            else:
                print(f"登录请求失败，状态码：{response.status_code}")
                print(f"响应内容：{response.text}")
        except Exception as e:
            print(f"登录异常：{e}")
        
        return False
    
    def create_workflow(self):
        """创建工作流"""
        print("\n正在创建工作流...")
        url = f"{self.base_url}/api/state-workflows"
        
        # 工作流定义
        workflow_data = {
            "name": f"测试工作流_{int(time.time())}",
            "description": "状态机工作流测试",
            "version": "1.0.0",
            "status": 1,
            "globalVariables": [
                {"name": "counter", "type": "integer", "initialValue": "0"},
                {"name": "message", "type": "string", "initialValue": "hello"}
            ],
            "nodes": [
                {
                    "nodeKey": "start",
                    "name": "开始节点",
                    "type": "start",
                    "configJson": "{\"inputs\": [{\"variableName\": \"counter\", \"initialValue\": 0}]}",
                    "positionX": 100,
                    "positionY": 200
                },
                {
                    "nodeKey": "math_op",
                    "name": "数值运算",
                    "type": "math_operation",
                    "configJson": "{\"operation\": \"add\", \"leftOperand\": \"counter\", \"rightOperand\": \"1\", \"outputVariable\": \"counter\"}",
                    "positionX": 300,
                    "positionY": 200
                },
                {
                    "nodeKey": "branch",
                    "name": "基础分支",
                    "type": "basic_branch",
                    "configJson": "{\"conditions\": [{\"expression\": \"counter < 3\", \"targetNodeId\": \"math_op\"}, {\"expression\": \"default\", \"targetNodeId\": \"end\"}]}",
                    "positionX": 500,
                    "positionY": 200
                },
                {
                    "nodeKey": "end",
                    "name": "结束节点",
                    "type": "end",
                    "configJson": "{\"outputs\": [{\"variableName\": \"final_count\", \"sourceVariable\": \"counter\"}]}",
                    "positionX": 700,
                    "positionY": 200
                }
            ],
            "transitions": [
                {
                    "fromNodeKey": "start",
                    "toNodeKey": "math_op",
                    "conditionExpression": "true"
                },
                {
                    "fromNodeKey": "math_op",
                    "toNodeKey": "branch",
                    "conditionExpression": "true"
                },
                {
                    "fromNodeKey": "branch",
                    "toNodeKey": "math_op",
                    "conditionExpression": "counter < 3"
                },
                {
                    "fromNodeKey": "branch",
                    "toNodeKey": "end",
                    "conditionExpression": "default"
                }
            ]
        }
        
        try:
            response = self.session.post(url, json=workflow_data)
            if response.status_code == 201:
                result = response.json()
                if result["code"] == 200:
                    workflow_id = result["data"]["id"]
                    print(f"工作流创建成功！ID：{workflow_id}")
                    return workflow_id
                else:
                    print(f"工作流创建失败：{result['msg']}")
            else:
                print(f"工作流创建请求失败，状态码：{response.status_code}")
                print(f"响应内容：{response.text}")
        except Exception as e:
            print(f"工作流创建异常：{e}")
        
        return None
    
    def get_workflow(self, workflow_id):
        """获取工作流详情"""
        print(f"\n正在获取工作流详情...")
        url = f"{self.base_url}/api/state-workflows/{workflow_id}"
        
        try:
            response = self.session.get(url)
            if response.status_code == 200:
                result = response.json()
                if result["code"] == 200:
                    print(f"工作流详情获取成功！")
                    print(f"工作流名称：{result['data']['name']}")
                    print(f"节点数量：{len(result['data']['nodes'])}")
                    print(f"转换数量：{len(result['data']['transitions'])}")
                    print(f"全局变量：{len(result['data']['globalVariables'])}")
                    return True
                else:
                    print(f"获取工作流详情失败：{result['msg']}")
            else:
                print(f"获取工作流详情请求失败，状态码：{response.status_code}")
                print(f"响应内容：{response.text}")
        except Exception as e:
            print(f"获取工作流详情异常：{e}")
        
        return False
    
    def execute_workflow(self, workflow_id):
        """执行工作流"""
        print(f"\n正在执行工作流...")
        url = f"{self.base_url}/api/state-workflows/{workflow_id}/execute"
        input_params = {
            "counter": 0,
            "message": "test"
        }
        
        try:
            response = self.session.post(url, json=input_params)
            if response.status_code == 200:
                result = response.json()
                if result["code"] == 200:
                    instance_id = result["data"]["id"]
                    print(f"工作流执行成功！实例ID：{instance_id}")
                    return instance_id
                else:
                    print(f"工作流执行失败：{result['msg']}")
            else:
                print(f"工作流执行请求失败，状态码：{response.status_code}")
                print(f"响应内容：{response.text}")
        except Exception as e:
            print(f"工作流执行异常：{e}")
        
        return None
    
    def get_instance(self, instance_id):
        """获取工作流实例"""
        print(f"\n正在获取工作流实例...")
        url = f"{self.base_url}/api/state-workflows/instances/{instance_id}"
        
        try:
            response = self.session.get(url)
            if response.status_code == 200:
                result = response.json()
                if result["code"] == 200:
                    instance = result["data"]
                    print(f"实例状态：{instance['status']}")
                    print(f"当前节点：{instance['currentNodeKey']}")
                    print(f"全局变量：{instance['globalVariables']}")
                    return instance
                else:
                    print(f"获取实例失败：{result['msg']}")
            else:
                print(f"获取实例请求失败，状态码：{response.status_code}")
                print(f"响应内容：{response.text}")
        except Exception as e:
            print(f"获取实例异常：{e}")
        
        return None
    
    def get_instance_logs(self, instance_id):
        """获取实例执行日志"""
        print(f"\n正在获取实例执行日志...")
        url = f"{self.base_url}/api/state-workflows/instances/{instance_id}/logs"
        
        try:
            response = self.session.get(url)
            if response.status_code == 200:
                result = response.json()
                if result["code"] == 200:
                    logs = result["data"]
                    print(f"执行日志数量：{len(logs)}")
                    for i, log in enumerate(logs):
                        print(f"日志 {i+1}: 节点={log['nodeKey']}, 状态={log['status']}, 耗时={log['executionTime']}ms")
                    return True
                else:
                    print(f"获取日志失败：{result['msg']}")
            else:
                print(f"获取日志请求失败，状态码：{response.status_code}")
                print(f"响应内容：{response.text}")
        except Exception as e:
            print(f"获取日志异常：{e}")
        
        return False
    
    def run_all_tests(self):
        """运行所有测试"""
        print("开始测试工作流API...")
        
        # 1. 登录
        if not self.login():
            return False
        
        # 2. 创建工作流
        workflow_id = self.create_workflow()
        if not workflow_id:
            return False
        
        # 3. 获取工作流详情
        if not self.get_workflow(workflow_id):
            return False
        
        # 4. 执行工作流
        instance_id = self.execute_workflow(workflow_id)
        if not instance_id:
            return False
        
        # 5. 获取实例状态
        instance = self.get_instance(instance_id)
        if not instance:
            return False
        
        # 6. 获取执行日志
        if not self.get_instance_logs(instance_id):
            return False
        
        print("\n所有测试完成！")
        return True

if __name__ == "__main__":
    tester = WorkflowAPITester()
    tester.run_all_tests()
