# 合同条款风险识别与合规审查系统

## 项目简介

本系统是一个基于AI的合同条款风险识别与合规审查平台，提供完整的合同全生命周期管理功能，包括文档解析、风险识别、合规检查、合同对比和审批流程管理。

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.5
- **数据库**: PostgreSQL 16
- **ORM**: Spring Data JPA / Hibernate
- **文档处理**: Apache PDFBox, Apache POI
- **OCR**: Tesseract OCR (支持中英文)
- **构建工具**: Maven 3.9
- **Java版本**: JDK 17

### 前端
- **框架**: Streamlit 1.32.2
- **Python版本**: Python 3.11
- **HTTP客户端**: Requests

### 部署
- **容器化**: Docker
- **编排**: Docker Compose

## 功能特性

### 1. 文档处理
- ✅ 支持PDF和Word格式上传
- ✅ PDF文本提取，扫描件自动OCR识别
- ✅ 智能合同结构解析（当事人信息、权利义务、违约责任等）
- ✅ 条款自动分割与标签分类

### 2. 风险识别引擎
- ✅ 21条内置风险规则
- ✅ 正则+关键词组合匹配
- ✅ NLP辅助模糊风险识别
- ✅ 风险等级：高/中/低三级

### 3. 覆盖风险类型
- 🔴 无限连带责任
- 🔴 单方任意解除权
- 🔴 自动续约不通知
- 🔴 竞业限制超期
- 🟡 知识产权归属不明
- 🟡 违约金比例过高（>30%）
- 🟡 管辖地偏向对方
- 🟡 付款条件不对等
- 🟡 保密义务单方约束
- 🟢 不可抗力范围过窄
- ... 及更多NLP模糊识别

### 4. 合规检查
- ✅ 多类型合规模板（采购/租赁/劳动等）
- ✅ 必备条款清单检查
- ✅ 禁止条款清单检查
- ✅ 金额范围约束检查

### 5. 风险报告
- ✅ 综合风险评分（0-100）
- ✅ 详细风险说明+原文引用
- ✅ 修改建议（3种以上参考表述）
- ✅ 章节风险分布热力图

### 6. 合同对比
- ✅ 条款级Diff对比
- ✅ 新增/删除/修改条款标注
- ✅ 新风险自动识别

### 7. 审批流程
- ✅ 状态流转：待审→审查中→通过/退回
- ✅ 48小时自动升级上级审批
- ✅ 审批意见记录

## 评分规则

- 🔴 高风险：每条扣20分
- 🟡 中风险：每条扣10分
- 🟢 低风险：每条扣5分
- ❌ 缺失必备条款：扣30分/条
- 🚫 出现禁止条款：扣50分/条
- ⚠️ 低于60分：建议拒签

## 快速开始

### 环境要求
- Docker Engine 20.10+
- Docker Compose 2.0+

### 使用Docker Compose启动

1. **克隆项目**
```bash
git clone <repository-url>
cd contract-risk
```

2. **配置环境变量**
```bash
cp .env.example .env
# 根据需要修改.env文件
```

3. **启动所有服务**
```bash
docker-compose up -d
```

4. **访问服务**
- 前端界面: http://localhost:8501
- 后端API: http://localhost:8080/api
- 数据库: localhost:5432

### 手动启动（开发环境）

#### 启动数据库
```bash
docker run -d \
  --name postgres \
  -e POSTGRES_DB=contract_risk \
  -e POSTGRES_USER=contract_user \
  -e POSTGRES_PASSWORD=contract_password \
  -p 5432:5432 \
  postgres:16-alpine
```

#### 安装OCR依赖（Mac）
```bash
brew install tesseract tesseract-lang poppler
```

#### 启动后端
```bash
cd backend
mvn clean package -DskipTests
java -jar target/contract-risk-1.0.0.jar
```

#### 启动前端
```bash
cd frontend
pip install -r requirements.txt
streamlit run app.py
```

## API接口文档

### 合同管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/contracts/upload` | 上传合同文件 |
| GET | `/api/contracts` | 获取合同列表 |
| GET | `/api/contracts/{id}` | 获取合同详情 |
| GET | `/api/contracts/{id}/clauses` | 获取合同条款 |
| DELETE | `/api/contracts/{id}` | 删除合同 |

### 风险分析
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/risk/analyze/{contractId}` | 执行风险分析 |
| GET | `/api/risk/report/{contractId}` | 获取风险报告 |
| GET | `/api/risk/items/{contractId}` | 获取风险项列表 |
| GET | `/api/risk/rules` | 获取所有风险规则 |

### 合规检查
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/compliance/check/{contractId}` | 执行合规检查 |
| GET | `/api/compliance/templates` | 获取合规模板列表 |
| POST | `/api/compliance/templates` | 创建合规模板 |

### 合同对比
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/comparison/{id1}/{id2}` | 对比两份合同 |

### 审批管理
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/approval/submit/{contractId}` | 提交审批 |
| POST | `/api/approval/process/{workflowId}` | 处理审批 |
| GET | `/api/approval/workflow/{id}` | 获取审批流程 |
| GET | `/api/approval/records/{workflowId}` | 获取审批记录 |
| GET | `/api/approval/stats` | 获取审批统计 |

## 项目结构

```
contract-risk/
├── backend/                          # 后端Spring Boot项目
│   ├── src/
│   │   └── main/
│   │       ├── java/com/contractrisk/
│   │       │   ├── ContractRiskApplication.java    # 主启动类
│   │       │   ├── config/           # 配置类
│   │       │   ├── controller/       # REST控制器
│   │       │   ├── service/          # 业务服务层
│   │       │   ├── engine/           # 风险引擎
│   │       │   ├── repository/       # 数据访问层
│   │       │   ├── entity/           # 数据实体
│   │       │   ├── dto/              # 数据传输对象
│   │       │   └── util/             # 工具类
│   │       └── resources/
│   │           └── application.yml   # 应用配置
│   ├── pom.xml                       # Maven配置
│   └── Dockerfile                    # 后端Dockerfile
├── frontend/                         # 前端Streamlit项目
│   ├── app.py                        # 主应用
│   ├── requirements.txt              # Python依赖
│   └── Dockerfile                    # 前端Dockerfile
├── docker-compose.yml                # Docker Compose配置
├── .env.example                      # 环境变量示例
└── README.md                         # 项目说明
```

## 核心数据模型

### Contract（合同）
- id, title, contractType, partyA, partyB
- totalAmount, startDate, endDate
- content, filePath, createdBy, createdAt

### ContractClause（合同条款）
- id, clauseNumber, clauseTitle, clauseContent
- sectionType (所属章节), riskCount, hasHighRisk

### RiskRule（风险规则）
- id, ruleName, ruleCategory, riskLevel
- matchPattern, keywords, matchMode
- riskDescription, suggestion, alternativePhrases

### RiskItem（风险项）
- id, clauseId, ruleId, riskLevel
- originalText, matchedText, fromNlp
- riskDescription, suggestion

### ComplianceTemplate（合规模板）
- id, name, contractType, description
- requiredClauses (必备条款JSON)
- forbiddenClauses (禁止条款JSON)
- minAmount, maxAmount

### RiskReport（风险报告）
- id, contractId, riskScore, totalRiskCount
- high/medium/lowRiskCount, summary, recommendation
- sectionRiskDistribution (JSON), recommendedReject

### ApprovalWorkflow（审批流程）
- id, contractId, status, submitter, currentApprover
- escalatedApprover, escalationTime, createdAt, processedAt

## 开发说明

### 添加新的风险规则
1. 在`RiskRuleInitializer.java`中添加新规则
2. 或通过API: `POST /api/risk/rules`

### 添加新的合规模板
1. 在`ComplianceTemplateInitializer.java`中添加模板
2. 或通过API: `POST /api/compliance/templates`

## 注意事项

1. **OCR性能**: 扫描件OCR识别可能需要较长时间，建议后台异步处理
2. **文件大小**: 默认最大支持50MB文件上传
3. **中文支持**: 已内置中英文OCR语言包
4. **数据备份**: 请定期备份PostgreSQL数据卷

## 常见问题

**Q: 如何修改风险评分规则？**
A: 修改`application.yml`中的`contract.risk`配置项，或环境变量。

**Q: 如何添加新的风险规则？**
A: 可以通过API动态添加，或在`RiskRuleInitializer`中预置。

**Q: 支持哪些合同类型？**
A: 内置8种合同类型，可通过`ContractType`枚举扩展。

## License

MIT License
