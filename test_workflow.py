import requests
import json

# 后端服务地址
BASE_URL = "http://localhost:8080/api"

# 登录信息
USERNAME = "user"
PASSWORD = "123"

class WorkflowTester:
    def __init__(self):
        self.token = None
        self.headers = {}
        self.workflow_id = None
        self.instance_id = None
    
    def login(self):
        """登录获取JWT令牌"""
        print("正在登录...")
        login_url = f"{BASE_URL}/user/login"
        login_data = {
            "username": USERNAME,
            "password": PASSWORD
        }
        
        try:
            response = requests.post(login_url, json=login_data)
            print(f"登录请求状态码: {response.status_code}")
            print(f"登录请求响应: {response.text}")
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                self.token = result.get("data", {}).get("token")
                self.headers = {
                    "Authorization": f"Bearer {self.token}"
                }
                print(f"登录成功！令牌: {self.token[:20]}...")
                return True
            else:
                print(f"登录失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"登录请求出错: {e}")
            return False
    
    def test_get_workflows(self):
        """测试获取所有工作流"""
        print("\n测试获取所有工作流...")
        workflows_url = f"{BASE_URL}/state-workflows"
        
        try:
            response = requests.get(workflows_url, headers=self.headers)
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                workflows = result.get("data", [])
                print(f"成功获取 {len(workflows)} 个工作流")
                for workflow in workflows:
                    print(f"  - {workflow.get('name')} (ID: {workflow.get('id')})")
                    self.workflow_id = workflow.get('id')  # 保存第一个工作流ID用于后续测试
                return True
            else:
                print(f"获取工作流失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"获取工作流请求出错: {e}")
            return False
    
    def test_get_workflow_detail(self):
        """测试获取单个工作流详情"""
        if not self.workflow_id:
            print("\n跳过获取工作流详情测试：没有可用的工作流ID")
            return False
        
        print(f"\n测试获取工作流详情 (ID: {self.workflow_id})...")
        workflow_url = f"{BASE_URL}/state-workflows/{self.workflow_id}"
        
        try:
            response = requests.get(workflow_url, headers=self.headers)
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                workflow = result.get("data")
                print(f"成功获取工作流详情：")
                print(f"  名称: {workflow.get('name')}")
                print(f"  描述: {workflow.get('description')}")
                print(f"  版本: {workflow.get('version')}")
                print(f"  状态: {'启用' if workflow.get('status') == 1 else '禁用'}")
                print(f"  包含JSON定义: {bool(workflow.get('jsonDefinition'))}")
                return True
            else:
                print(f"获取工作流详情失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"获取工作流详情请求出错: {e}")
            return False
    
    def test_execute_workflow(self):
        """测试执行工作流"""
        if not self.workflow_id:
            print("\n跳过执行工作流测试：没有可用的工作流ID")
            return False
        
        print(f"\n测试执行工作流 (ID: {self.workflow_id})...")
        execute_url = f"{BASE_URL}/state-workflows/{self.workflow_id}/execute"
        input_params = {
            "test_param": "test_value"
        }
        
        try:
            response = requests.post(execute_url, json=input_params, headers=self.headers)
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                instance = result.get("data")
                self.instance_id = instance.get("id")
                print(f"成功执行工作流，实例ID: {self.instance_id}")
                print(f"  实例状态: {instance.get('status')}")
                print(f"  当前节点: {instance.get('currentNodeKey')}")
                return True
            else:
                print(f"执行工作流失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"执行工作流请求出错: {e}")
            return False
    
    def test_get_instances(self):
        """测试获取工作流实例"""
        print("\n测试获取所有工作流实例...")
        instances_url = f"{BASE_URL}/state-workflows/instances"
        
        try:
            response = requests.get(instances_url, headers=self.headers)
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                instances = result.get("data", [])
                print(f"成功获取 {len(instances)} 个工作流实例")
                for instance in instances:
                    print(f"  - ID: {instance.get('id')}, 状态: {instance.get('status')}, 工作流ID: {instance.get('workflowId')}")
                return True
            else:
                print(f"获取工作流实例失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"获取工作流实例请求出错: {e}")
            return False
    
    def test_get_instance_logs(self):
        """测试获取实例日志"""
        if not self.instance_id:
            print("\n跳过获取实例日志测试：没有可用的实例ID")
            return False
        
        print(f"\n测试获取实例日志 (ID: {self.instance_id})...")
        logs_url = f"{BASE_URL}/state-workflows/instances/{self.instance_id}/logs"
        
        try:
            response = requests.get(logs_url, headers=self.headers)
            response.raise_for_status()
            result = response.json()
            
            if result.get("code") == 200:
                logs = result.get("data", [])
                print(f"成功获取 {len(logs)} 条实例日志")
                for log in logs:
                    print(f"  - 节点: {log.get('nodeKey')}, 类型: {log.get('nodeType')}, 状态: {log.get('status')}, 耗时: {log.get('executionTime')}ms")
                return True
            else:
                print(f"获取实例日志失败: {result.get('message')}")
                return False
        except Exception as e:
            print(f"获取实例日志请求出错: {e}")
            return False
    
    def run_all_tests(self):
        """运行所有测试"""
        print("=" * 60)
        print("开始测试状态机工作流API")
        print("=" * 60)
        
        # 登录
        if not self.login():
            print("\n测试失败：登录失败")
            return
        
        # 运行所有测试
        test_results = []
        test_results.append("获取所有工作流: " + ("通过" if self.test_get_workflows() else "失败"))
        test_results.append("获取工作流详情: " + ("通过" if self.test_get_workflow_detail() else "失败"))
        test_results.append("执行工作流: " + ("通过" if self.test_execute_workflow() else "失败"))
        test_results.append("获取工作流实例: " + ("通过" if self.test_get_instances() else "失败"))
        test_results.append("获取实例日志: " + ("通过" if self.test_get_instance_logs() else "失败"))
        
        # 输出测试结果总结
        print("\n" + "=" * 60)
        print("测试结果总结")
        print("=" * 60)
        for result in test_results:
            print(f"  {result}")
        
        # 计算通过率
        passed = sum(1 for r in test_results if "通过" in r)
        total = len(test_results)
        print(f"\n通过率: {passed}/{total} ({passed/total*100:.1f}%)")

if __name__ == "__main__":
    tester = WorkflowTester()
    tester.run_all_tests()