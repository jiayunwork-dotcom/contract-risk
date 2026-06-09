import streamlit as st
import requests
import os
from dotenv import load_dotenv
import pandas as pd
import json
import time

load_dotenv()

API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8080/api")

def resolve_api_base_url():
    candidates = [API_BASE_URL]
    if API_BASE_URL != "http://localhost:8080/api":
        candidates.append("http://localhost:8080/api")
    for url in candidates:
        try:
            resp = requests.get(f"{url}/contracts", timeout=3)
            if resp.status_code < 500:
                return url
        except requests.exceptions.RequestException:
            continue
    return API_BASE_URL

API_BASE_URL = resolve_api_base_url()

st.set_page_config(
    page_title="合同条款风险识别与合规审查系统",
    page_icon="📋",
    layout="wide",
    initial_sidebar_state="expanded"
)

st.markdown("""
<style>
    .main-header {
        font-size: 2rem;
        font-weight: bold;
        color: #1f618d;
        margin-bottom: 1rem;
    }
    .sub-header {
        font-size: 1.2rem;
        font-weight: bold;
        color: #2e86c1;
        margin-top: 1rem;
        margin-bottom: 0.5rem;
    }
    .risk-high {
        color: #e74c3c;
        font-weight: bold;
    }
    .risk-medium {
        color: #f39c12;
        font-weight: bold;
    }
    .risk-low {
        color: #27ae60;
        font-weight: bold;
    }
    .score-box {
        font-size: 2.5rem;
        font-weight: bold;
        text-align: center;
        padding: 1rem;
        border-radius: 10px;
        margin: 1rem 0;
    }
    .score-good {
        background-color: #d4efdf;
        color: #1e8449;
    }
    .score-warning {
        background-color: #fef9e7;
        color: #d4ac0d;
    }
    .score-danger {
        background-color: #fadbd8;
        color: #c0392b;
    }
    .card {
        padding: 1rem;
        border-radius: 8px;
        background-color: #f8f9f9;
        margin: 0.5rem 0;
        border-left: 4px solid #2e86c1;
    }
    .card-high {
        border-left-color: #e74c3c;
    }
    .card-medium {
        border-left-color: #f39c12;
    }
    .card-low {
        border-left-color: #27ae60;
    }
</style>
""", unsafe_allow_html=True)

def api_call(method, endpoint, **kwargs):
    url = f"{API_BASE_URL}{endpoint}"
    try:
        response = requests.request(method, url, timeout=60, **kwargs)
        response.raise_for_status()
        return response.json()
    except requests.exceptions.ConnectionError:
        st.error(f"无法连接到后端服务({API_BASE_URL})，请确认后端服务已启动。若通过Docker运行，请检查容器网络配置。")
        return None
    except requests.exceptions.Timeout:
        st.error("请求超时，请稍后重试。")
        return None
    except requests.exceptions.RequestException as e:
        st.error(f"API请求失败: {str(e)}")
        return None

def get_risk_color(level):
    return {
        "HIGH": "#e74c3c",
        "MEDIUM": "#f39c12",
        "LOW": "#27ae60"
    }.get(level, "#7f8c8d")

def get_risk_class(level):
    return {
        "HIGH": "risk-high",
        "MEDIUM": "risk-medium",
        "LOW": "risk-low"
    }.get(level, "")

def get_score_class(score):
    if score >= 80:
        return "score-good"
    elif score >= 60:
        return "score-warning"
    else:
        return "score-danger"

def main():
    st.sidebar.title("📋 合同风险审查系统")
    st.sidebar.markdown("---")

    page = st.sidebar.radio(
        "功能导航",
        ["🏠 首页", "📤 合同上传", "🔍 风险分析", "✅ 合规检查", "⚖️ 合同对比", "📝 审批管理", "⚙️ 规则管理"]
    )

    st.sidebar.markdown("---")
    st.sidebar.info("💡 支持PDF、DOC、DOCX格式的合同文件")

    if page == "🏠 首页":
        show_home_page()
    elif page == "📤 合同上传":
        show_upload_page()
    elif page == "🔍 风险分析":
        show_risk_analysis_page()
    elif page == "✅ 合规检查":
        show_compliance_page()
    elif page == "⚖️ 合同对比":
        show_comparison_page()
    elif page == "📝 审批管理":
        show_approval_page()
    elif page == "⚙️ 规则管理":
        show_rules_page()

def show_home_page():
    st.markdown('<p class="main-header">📋 合同条款风险识别与合规审查系统</p>', unsafe_allow_html=True)

    try:
        health = requests.get(f"{API_BASE_URL}/contracts", timeout=5)
        if health.status_code < 500:
            st.sidebar.success("🟢 后端服务已连接")
        else:
            st.sidebar.error("🔴 后端服务异常")
    except:
        st.sidebar.error(f"🔴 后端服务不可达({API_BASE_URL})")

    col1, col2, col3, col4 = st.columns(4)

    try:
        contracts = api_call("GET", "/contracts")
        if contracts and contracts.get("success"):
            total_contracts = len(contracts.get("data", []))
        else:
            total_contracts = 0
    except:
        total_contracts = 0

    try:
        approval_stats = api_call("GET", "/approval/stats")
        if approval_stats and approval_stats.get("success"):
            data = approval_stats.get("data", {})
            pending = data.get("pending", 0) + data.get("underReview", 0)
            approved = data.get("approved", 0)
            rejected = data.get("rejected", 0)
        else:
            pending = approved = rejected = 0
    except:
        pending = approved = rejected = 0

    with col1:
        st.metric("📄 合同总数", total_contracts)
    with col2:
        st.metric("⏳ 待审批", pending)
    with col3:
        st.metric("✅ 已通过", approved)
    with col4:
        st.metric("❌ 已拒绝", rejected)

    st.markdown("---")

    col1, col2 = st.columns(2)

    with col1:
        st.markdown('<p class="sub-header">✨ 系统功能</p>', unsafe_allow_html=True)
        st.markdown("""
        - **📄 文档处理**: 支持PDF、Word格式解析，扫描件OCR识别
        - **🏗️ 结构解析**: 自动识别合同章节结构，智能条款分割
        - **🔍 风险识别**: 21条内置规则引擎 + NLP辅助识别
        - **✅ 合规检查**: 多类型合规模板，必备/禁止条款检查
        - **⚖️ 合同对比**: 条款级Diff对比，新风险自动识别
        - **📝 审批管理**: 多级审批流程，48小时自动升级
        """)

    with col2:
        st.markdown('<p class="sub-header">🎯 覆盖风险类型</p>', unsafe_allow_html=True)
        st.markdown("""
        - 🔴 **无限连带责任**
        - 🔴 **单方任意解除权**
        - 🔴 **自动续约不通知**
        - 🔴 **竞业限制超期**
        - 🟡 **知识产权归属不明**
        - 🟡 **违约金比例过高**
        - 🟡 **管辖地偏向对方**
        - 🟡 **付款条件不对等**
        - 🟡 **保密义务单方约束**
        - 🟢 **不可抗力范围过窄**
        - ... 及更多模糊风险NLP识别
        """)

    st.markdown("---")
    st.markdown('<p class="sub-header">📊 风险评分规则</p>', unsafe_allow_html=True)

    col1, col2, col3 = st.columns(3)
    with col1:
        st.info("🔴 高风险: 每条扣20分")
    with col2:
        st.warning("🟡 中风险: 每条扣10分")
    with col3:
        st.success("🟢 低风险: 每条扣5分")

    st.info("💡 缺失必备条款扣30分，出现禁止条款扣50分。低于60分建议拒签。")

def show_upload_page():
    st.markdown('<p class="main-header">📤 合同上传</p>', unsafe_allow_html=True)

    col1, col2 = st.columns([2, 1])

    with col1:
        uploaded_file = st.file_uploader(
            "选择合同文件",
            type=["pdf", "doc", "docx"],
            help="支持PDF、DOC、DOCX格式，最大50MB"
        )

        contract_type = st.selectbox(
            "合同类型",
            ["PURCHASE", "LEASE", "LABOR", "SALES", "SERVICE", "COOPERATION", "CONFIDENTIALITY", "OTHER"],
            format_func=lambda x: {
                "PURCHASE": "采购合同",
                "LEASE": "租赁合同",
                "LABOR": "劳动合同",
                "SALES": "销售合同",
                "SERVICE": "服务合同",
                "COOPERATION": "合作合同",
                "CONFIDENTIALITY": "保密协议",
                "OTHER": "其他合同"
            }.get(x, x)
        )

        created_by = st.text_input("上传人", value="demo_user")

        if uploaded_file is not None:
            st.info(f"已选择文件: {uploaded_file.name} ({uploaded_file.size / 1024:.1f} KB)")

            if st.button("🚀 上传并解析合同", type="primary"):
                with st.spinner("正在解析合同..."):
                    files = {"file": (uploaded_file.name, uploaded_file.getvalue(), uploaded_file.type)}
                    data = {
                        "contractType": contract_type,
                        "createdBy": created_by
                    }
                    result = api_call("POST", "/contracts/upload", files=files, data=data)

                    if result and result.get("success"):
                        contract_data = result.get("data", {})
                        st.success(f"✅ 合同上传成功！文档ID: {contract_data.get('id')}")

                        st.markdown('<p class="sub-header">📋 解析结果</p>', unsafe_allow_html=True)

                        col1, col2, col3 = st.columns(3)
                        with col1:
                            st.write("**标题:**", contract_data.get("title"))
                            st.write("**类型:**", contract_data.get("contractType"))
                        with col2:
                            st.write("**甲方:**", contract_data.get("partyA", "未识别"))
                            st.write("**乙方:**", contract_data.get("partyB", "未识别"))
                        with col3:
                            st.write("**金额:**", contract_data.get("totalAmount", "未识别"))
                            st.write("**条款数:**", contract_data.get("clauseCount", 0))

                        st.session_state["last_contract_id"] = contract_data.get("id")

                        if st.button("🔍 立即进行风险分析", type="secondary"):
                            st.session_state["page"] = "risk"
                            st.rerun()
                    else:
                        st.error("上传失败: " + (result.get("message") if result else "未知错误"))

    with col2:
        st.markdown('<p class="sub-header">📚 最近上传</p>', unsafe_allow_html=True)
        contracts = api_call("GET", "/contracts")
        if contracts and contracts.get("success"):
            contract_list = contracts.get("data", [])[:5]
            for c in contract_list:
                with st.expander(f"📄 {c.get('title')[:30]}..."):
                    st.write(f"ID: {c.get('id')}")
                    st.write(f"类型: {c.get('contractType')}")
                    st.write(f"创建时间: {c.get('createdAt')[:10] if c.get('createdAt') else ''}")
                    if st.button(f"查看详情 #{c.get('id')}", key=f"view_{c.get('id')}"):
                        st.session_state["selected_contract_id"] = c.get("id")

def show_risk_analysis_page():
    st.markdown('<p class="main-header">🔍 风险分析</p>', unsafe_allow_html=True)

    contract_id = st.number_input(
        "输入合同ID",
        min_value=1,
        value=st.session_state.get("last_contract_id", 1),
        step=1
    )

    col1, col2 = st.columns([1, 4])

    with col1:
        if st.button("🔍 开始风险分析", type="primary"):
            with st.spinner("正在分析风险..."):
                result = api_call("POST", f"/risk/analyze/{contract_id}")
                if result and result.get("success"):
                    st.success("分析完成！")
                    st.rerun()

        if st.button("📊 查看已有报告"):
            pass

    report = api_call("GET", f"/risk/report/{contract_id}")

    if report and report.get("success"):
        report_data = report.get("data", {})
        risk_score = report_data.get("riskScore", 100)
        if isinstance(risk_score, (int, float)):
            risk_score = int(risk_score)
        else:
            risk_score = 100

        score_class = get_score_class(risk_score)

        col1, col2, col3 = st.columns([1, 2, 1])
        with col2:
            st.markdown(
                f'<div class="score-box {score_class}">风险评分: {risk_score}/100</div>',
                unsafe_allow_html=True
            )

            if report_data.get("recommendedReject"):
                st.error("⚠️ 建议拒签")
            elif risk_score < 80:
                st.warning("⚠️ 谨慎签署")
            else:
                st.success("✅ 建议签署")

        col1, col2, col3, col4 = st.columns(4)
        with col1:
            st.metric("🔴 高风险", report_data.get("highRiskCount", 0))
        with col2:
            st.metric("🟡 中风险", report_data.get("mediumRiskCount", 0))
        with col3:
            st.metric("🟢 低风险", report_data.get("lowRiskCount", 0))
        with col4:
            st.metric("📊 总计", report_data.get("totalRiskCount", 0))

        st.markdown("---")

        col1, col2 = st.columns([3, 2])

        with col1:
            st.markdown('<p class="sub-header">📝 风险详情</p>', unsafe_allow_html=True)

            risk_items = api_call("GET", f"/risk/items/{contract_id}")
            if risk_items and risk_items.get("success"):
                items = risk_items.get("data", [])

                level_filter = st.multiselect(
                    "筛选风险等级",
                    ["HIGH", "MEDIUM", "LOW"],
                    default=["HIGH", "MEDIUM", "LOW"],
                    format_func=lambda x: {"HIGH": "🔴 高", "MEDIUM": "🟡 中", "LOW": "🟢 低"}.get(x, x)
                )

                for item in items:
                    level = item.get("riskLevel")
                    if level not in level_filter:
                        continue

                    card_class = f"card card-{level.lower()}"
                    risk_class = get_risk_class(level)
                    level_label = {"HIGH": "🔴 高风险", "MEDIUM": "🟡 中风险", "LOW": "🟢 低风险"}.get(level, level)

                    with st.expander(
                        f"{level_label}: {item.get('ruleName')} | 条款: {item.get('clause', {}).get('clauseNumber', 'N/A')}"
                    ):
                        st.markdown(f'<div class="{card_class}">', unsafe_allow_html=True)
                        st.write("**风险说明:**", item.get("riskDescription"))
                        st.write("**原文引用:**")
                        st.info(item.get("originalText"))
                        if item.get("matchedText"):
                            st.write("**匹配内容:**")
                            st.error(item.get("matchedText"))
                        st.write("**修改建议:**", item.get("suggestion"))

                        if item.get("alternativePhrases"):
                            with st.expander("💡 参考替代表述"):
                                for phrase in item.get("alternativePhrases", "").split("\n"):
                                    if phrase.strip():
                                        st.success(phrase.strip())

                        if item.get("fromNlp"):
                            st.caption("🤖 由NLP辅助识别")

                        st.markdown('</div>', unsafe_allow_html=True)

        with col2:
            st.markdown('<p class="sub-header">📊 分析摘要</p>', unsafe_allow_html=True)
            st.info(report_data.get("summary", "暂无摘要"))
            st.warning(report_data.get("recommendation", "暂无建议"))

            if report_data.get("complianceStatus"):
                status = report_data.get("complianceStatus")
                status_text = {
                    "COMPLIANT": "✅ 合规",
                    "NON_COMPLIANT": "⚠️ 不合规",
                    "SERIOUS_NON_COMPLIANT": "❌ 严重不合规"
                }.get(status, status)
                st.metric("合规状态", status_text)

            if report_data.get("missingRequiredCount", 0) > 0:
                st.error(f"缺失必备条款: {report_data.get('missingRequiredCount')}条")
            if report_data.get("forbiddenClauseCount", 0) > 0:
                st.error(f"存在禁止条款: {report_data.get('forbiddenClauseCount')}条")
            if report_data.get("amountViolationCount", 0) > 0:
                st.warning(f"金额违规: {report_data.get('amountViolationCount')}条")

            if report_data.get("sectionRiskDistribution"):
                try:
                    dist = json.loads(report_data.get("sectionRiskDistribution"))
                    if dist:
                        st.markdown('<p class="sub-header">🔥 章节风险分布</p>', unsafe_allow_html=True)
                        section_map = {
                            "PARTIES_INFO": "当事人信息",
                            "DEFINITIONS": "定义条款",
                            "RIGHTS_AND_OBLIGATIONS": "权利义务",
                            "BREACH_LIABILITY": "违约责任",
                            "DISPUTE_RESOLUTION": "争议解决",
                            "CONFIDENTIALITY": "保密条款",
                            "INTELLECTUAL_PROPERTY": "知识产权",
                            "PAYMENT_TERMS": "付款条款",
                            "TERM_AND_TERMINATION": "期限与终止",
                            "FORCE_MAJEURE": "不可抗力",
                            "MISCELLANEOUS": "其他条款",
                            "SUPPLEMENTARY": "附则"
                        }
                        for section, count in dist.items():
                            label = section_map.get(section, section)
                            intensity = min(count * 20, 100)
                            color = f"rgba(231, 76, 60, {intensity/100})"
                            st.markdown(
                                f'<div style="padding: 8px; margin: 4px 0; '
                                f'background-color: {color}; color: white; '
                                f'border-radius: 4px;">{label}: {count}个风险</div>',
                                unsafe_allow_html=True
                            )
                except:
                    pass

    else:
        st.info("暂无风险报告，请先进行风险分析")

def show_compliance_page():
    st.markdown('<p class="main-header">✅ 合规检查</p>', unsafe_allow_html=True)

    tab1, tab2 = st.tabs(["📋 合规检查", "📑 模板管理"])

    with tab1:
        contract_id = st.number_input("输入合同ID", min_value=1, value=1, step=1)

        if st.button("🔍 开始合规检查", type="primary"):
            with st.spinner("正在进行合规检查..."):
                result = api_call("POST", f"/compliance/check/{contract_id}")
                if result and result.get("success"):
                    st.session_state["compliance_result"] = result.get("data")

        if "compliance_result" in st.session_state:
            data = st.session_state["compliance_result"]

            col1, col2, col3 = st.columns(3)
            with col1:
                compliant = data.get("compliant", True)
                if compliant:
                    st.success("✅ 合规")
                else:
                    st.error("❌ 不合规")
            with col2:
                st.metric("使用模板", data.get("templateName", "无"))
            with col3:
                st.metric("罚分", data.get("penaltyScore", 0))

            st.markdown("---")

            if data.get("missingRequired"):
                st.error(f"❌ 缺失必备条款 ({len(data.get('missingRequired'))}条)")
                for clause in data.get("missingRequired"):
                    st.warning(f"⚠️ {clause}")

            if data.get("forbiddenClauses"):
                st.error(f"❌ 存在禁止条款 ({len(data.get('forbiddenClauses'))}条)")
                for item in data.get("forbiddenClauses"):
                    with st.expander(f"🚫 {item.get('forbiddenTerm')} | 条款{item.get('clauseNumber')}"):
                        st.write("**条款标题:**", item.get("clauseTitle"))
                        st.write("**内容:**", item.get("content"))

            if data.get("amountViolations"):
                st.warning(f"⚠️ 金额违规 ({len(data.get('amountViolations'))}条)")
                for item in data.get("amountViolations"):
                    st.write(f"- {item.get('message')}: {item.get('amount')}")

            if not data.get("missingRequired") and not data.get("forbiddenClauses") and not data.get("amountViolations"):
                st.success("🎉 恭喜！该合同通过合规检查")

    with tab2:
        st.markdown('<p class="sub-header">📑 合规模板列表</p>', unsafe_allow_html=True)
        templates = api_call("GET", "/compliance/templates")
        if templates and templates.get("success"):
            for t in templates.get("data", []):
                with st.expander(f"📋 {t.get('name')} ({t.get('contractType')})"):
                    st.write("**描述:**", t.get("description"))
                    st.write("**必备条款:**", t.get("requiredClauses"))
                    st.write("**禁止条款:**", t.get("forbiddenClauses"))
                    if t.get("minAmount") is not None:
                        st.write("**金额范围:**", f"{t.get('minAmount')} - {t.get('maxAmount')}")

def show_comparison_page():
    st.markdown('<p class="main-header">⚖️ 合同对比</p>', unsafe_allow_html=True)

    col1, col2 = st.columns(2)

    with col1:
        contract_id1 = st.number_input("原合同ID", min_value=1, value=1, step=1)
    with col2:
        contract_id2 = st.number_input("新合同ID", min_value=1, value=2, step=1)

    if st.button("🔍 开始对比", type="primary"):
        with st.spinner("正在对比两份合同..."):
            result = api_call("POST", f"/comparison/{contract_id1}/{contract_id2}")
            if result and result.get("success"):
                st.session_state["comparison_result"] = result.get("data")
                st.success("对比完成！")

    if "comparison_result" in st.session_state:
        data = st.session_state["comparison_result"]

        summary = data.get("summary", {})
        col1, col2, col3, col4 = st.columns(4)
        with col1:
            st.metric("📝 差异条款", summary.get("totalDifferences", 0))
        with col2:
            st.metric("➕ 新增条款", summary.get("addedClauses", 0))
        with col3:
            st.metric("➖ 删除条款", summary.get("removedClauses", 0))
        with col4:
            st.metric("✏️ 修改条款", summary.get("modifiedClauses", 0))

        if summary.get("highRiskChange", 0) > 0:
            st.error(f"⚠️ 新增高风险条款: {summary.get('highRiskChange')}条")

        if data.get("newRisksCount", 0) > 0:
            st.markdown('<p class="sub-header">⚠️ 新引入风险</p>', unsafe_allow_html=True)
            for risk in data.get("newRisksIntroduced", []):
                level = risk.get("riskLevel")
                card_class = f"card card-{level.lower()}"
                with st.expander(
                    f"{risk.get('introducedByChange')}: {risk.get('ruleName')} "
                    f"({'🔴' if level == 'HIGH' else '🟡' if level == 'MEDIUM' else '🟢'})"
                ):
                    st.markdown(f'<div class="{card_class}">', unsafe_allow_html=True)
                    st.write("**风险说明:**", risk.get("riskDescription"))
                    st.write("**匹配内容:**", risk.get("matchedText"))
                    st.write("**修改建议:**", risk.get("suggestion"))
                    st.markdown('</div>', unsafe_allow_html=True)

        st.markdown('<p class="sub-header">📋 条款对比详情</p>', unsafe_allow_html=True)

        change_filter = st.multiselect(
            "筛选变更类型",
            ["ADDED", "REMOVED", "MODIFIED"],
            default=["ADDED", "REMOVED", "MODIFIED"],
            format_func=lambda x: {"ADDED": "➕ 新增", "REMOVED": "➖ 删除", "MODIFIED": "✏️ 修改"}.get(x, x)
        )

        for diff in data.get("clauseDiffs", []):
            change_type = diff.get("changeType")
            if change_type not in change_filter:
                continue

            icon = {"ADDED": "➕", "REMOVED": "➖", "MODIFIED": "✏️"}.get(change_type, "•")
            color = {"ADDED": "green", "REMOVED": "red", "MODIFIED": "orange"}.get(change_type, "blue")

            with st.expander(
                f'{icon} <span style="color:{color};">[{change_type}]</span> '
                f'{diff.get("clauseNumber")} {diff.get("clauseTitle")}',
                expanded=diff.get("hasRiskIncrease", False)
            ):
                if diff.get("hasRiskIncrease"):
                    st.error("⚠️ 此变更引入了新风险！")

                if change_type == "ADDED":
                    st.success("**新增内容:**")
                    st.write(diff.get("newContent"))
                elif change_type == "REMOVED":
                    st.error("**删除内容:**")
                    st.write(diff.get("oldContent"))
                else:
                    col1, col2 = st.columns(2)
                    with col1:
                        st.warning("**原内容:**")
                        st.write(diff.get("oldContent"))
                        st.caption(f"风险数: {diff.get('oldRiskCount')}, 高风险: {diff.get('oldIsHighRisk')}")
                    with col2:
                        st.info("**新内容:**")
                        st.write(diff.get("newContent"))
                        st.caption(f"风险数: {diff.get('newRiskCount')}, 高风险: {diff.get('newIsHighRisk')}")
                        st.caption(f"相似度: {diff.get('similarity', 0):.2%}")

def show_approval_page():
    st.markdown('<p class="main-header">📝 审批管理</p>', unsafe_allow_html=True)

    tab1, tab2, tab3 = st.tabs(["📋 提交审批", "✅ 处理审批", "📊 审批统计"])

    with tab1:
        contract_id = st.number_input("合同ID", min_value=1, value=1, step=1)
        submitter = st.text_input("提交人", value="demo_user")
        approver = st.text_input("审批人", value="manager")

        if st.button("📤 提交审批", type="primary"):
            payload = {
                "submitter": submitter,
                "currentApprover": approver
            }
            result = api_call("POST", f"/approval/submit/{contract_id}", json=payload)
            if result and result.get("success"):
                st.success("✅ 提交审批成功！")
                workflow = result.get("data", {})
                st.write("审批流程ID:", workflow.get("id"))
                st.write("当前状态:", workflow.get("status"))

    with tab2:
        workflow_id = st.number_input("审批流程ID", min_value=1, value=1, step=1)

        if st.button("🔍 查看审批流程"):
            result = api_call("GET", f"/approval/workflow/{workflow_id}")
            if result and result.get("success"):
                wf = result.get("data", {})
                st.info(f"状态: {wf.get('status')} | 当前审批人: {wf.get('currentApprover')}")

        action = st.selectbox(
            "审批动作",
            ["APPROVED", "REJECTED", "NEEDS_MODIFICATION"],
            format_func=lambda x: {
                "APPROVED": "✅ 同意",
                "REJECTED": "❌ 拒绝",
                "NEEDS_MODIFICATION": "📝 需修改"
            }.get(x, x)
        )
        approver = st.text_input("处理人", value="manager")
        comments = st.text_area("审批意见")

        if st.button("💾 提交审批意见", type="primary"):
            payload = {
                "action": action,
                "approver": approver,
                "comments": comments
            }
            result = api_call("POST", f"/approval/process/{workflow_id}", json=payload)
            if result and result.get("success"):
                st.success("✅ 审批意见已提交！")

        st.markdown("---")
        st.markdown('<p class="sub-header">📜 审批记录</p>', unsafe_allow_html=True)
        records = api_call("GET", f"/approval/records/{workflow_id}")
        if records and records.get("success"):
            for r in records.get("data", []):
                action_icon = {
                    "APPROVED": "✅", "REJECTED": "❌",
                    "NEEDS_MODIFICATION": "📝", "PENDING": "⏳",
                    "ESCALATED": "⬆️", "UNDER_REVIEW": "🔍"
                }.get(r.get("action"), "•")
                st.write(
                    f"{action_icon} **{r.get('action')}** - "
                    f"{r.get('approver')} | {r.get('createdAt')[:19]}"
                )
                if r.get("comments"):
                    st.caption(f"💬 {r.get('comments')}")

    with tab3:
        stats = api_call("GET", "/approval/stats")
        if stats and stats.get("success"):
            data = stats.get("data", {})
            col1, col2, col3 = st.columns(3)
            with col1:
                st.metric("⏳ 待处理", data.get("pending", 0) + data.get("underReview", 0))
            with col2:
                st.metric("✅ 已通过", data.get("approved", 0))
            with col3:
                st.metric("❌ 已拒绝", data.get("rejected", 0))

            col4, col5 = st.columns(2)
            with col4:
                st.metric("📝 需修改", data.get("needsModification", 0))
            with col5:
                st.metric("⬆️ 已升级", data.get("escalated", 0))

def show_rules_page():
    st.markdown('<p class="main-header">⚙️ 风险规则管理</p>', unsafe_allow_html=True)

    rules = api_call("GET", "/risk/rules")
    if rules and rules.get("success"):
        rules_data = rules.get("data", [])

        st.markdown(f"**共 {len(rules_data)} 条启用的风险规则**")

        level_filter = st.multiselect(
            "筛选风险等级",
            ["HIGH", "MEDIUM", "LOW"],
            default=["HIGH", "MEDIUM", "LOW"],
            format_func=lambda x: {"HIGH": "🔴 高", "MEDIUM": "🟡 中", "LOW": "🟢 低"}.get(x, x)
        )

        categories = api_call("GET", "/risk/stats/categories")
        if categories and categories.get("success"):
            cat_filter = st.multiselect(
                "筛选分类",
                categories.get("data", []),
                default=categories.get("data", [])
            )
        else:
            cat_filter = []

        for rule in rules_data:
            level = rule.get("riskLevel")
            if level not in level_filter:
                continue
            if cat_filter and rule.get("ruleCategory") not in cat_filter:
                continue

            level_icon = {"HIGH": "🔴", "MEDIUM": "🟡", "LOW": "🟢"}.get(level, "•")
            with st.expander(f"{level_icon} [{level}] {rule.get('ruleName')}"):
                col1, col2 = st.columns(2)
                with col1:
                    st.write("**分类:**", rule.get("ruleCategory"))
                    st.write("**匹配模式:**", rule.get("matchPattern"))
                with col2:
                    st.write("**关键词:**", rule.get("keywords"))
                    st.write("**匹配模式:**", rule.get("matchMode"))

                st.write("**风险说明:**", rule.get("riskDescription"))
                st.write("**修改建议:**", rule.get("suggestion"))

                if rule.get("alternativePhrases"):
                    with st.expander("💡 参考替代表述"):
                        for phrase in rule.get("alternativePhrases", "").split("\n"):
                            if phrase.strip():
                                st.success(phrase.strip())

if __name__ == "__main__":
    main()
