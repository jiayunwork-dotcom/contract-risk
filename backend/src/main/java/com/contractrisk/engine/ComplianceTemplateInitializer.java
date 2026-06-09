package com.contractrisk.engine;

import com.contractrisk.entity.ComplianceTemplate;
import com.contractrisk.entity.enums.ContractType;
import com.contractrisk.repository.ComplianceTemplateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class ComplianceTemplateInitializer implements CommandLineRunner {

    private final ComplianceTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        if (templateRepository.count() > 0) {
            log.info("合规模板已存在，跳过初始化");
            return;
        }

        log.info("开始初始化合规模板...");

        initPurchaseTemplate();
        initLeaseTemplate();
        initLaborTemplate();
        initSalesTemplate();
        initServiceTemplate();

        log.info("合规模板初始化完成，共{}条", templateRepository.count());
    }

    private void initPurchaseTemplate() throws JsonProcessingException {
        List<String> requiredClauses = Arrays.asList(
                "当事人信息条款",
                "标的物描述条款",
                "价格与付款条款",
                "交付条款",
                "验收条款",
                "质量保证条款",
                "违约责任条款",
                "争议解决条款",
                "合同生效条款"
        );

        List<String> forbiddenClauses = Arrays.asList(
            "无限连带责任",
            "单方任意解除权",
            "自动续约且不通知"
        );

        ComplianceTemplate template = new ComplianceTemplate();
        template.setName("采购合同标准模板");
        template.setContractType(ContractType.PURCHASE);
        template.setDescription("适用于货物采购类合同的合规审查标准");
        template.setRequiredClauses(objectMapper.writeValueAsString(requiredClauses));
        template.setForbiddenClauses(objectMapper.writeValueAsString(forbiddenClauses));
        template.setMinAmount(1000.0);
        template.setMaxAmount(10000000.0);
        template.setEnabled(true);

        templateRepository.save(template);
    }

    private void initLeaseTemplate() throws JsonProcessingException {
        List<String> requiredClauses = Arrays.asList(
                "当事人信息条款",
                "租赁物描述条款",
                "租赁期限条款",
                "租金及支付条款",
                "押金条款",
                "维修责任条款",
                "违约责任条款",
                "争议解决条款",
                "合同生效条款"
        );

        List<String> forbiddenClauses = Arrays.asList(
            "无限连带责任",
            "自动续约且不通知"
        );

        ComplianceTemplate template = new ComplianceTemplate();
        template.setName("租赁合同标准模板");
        template.setContractType(ContractType.LEASE);
        template.setDescription("适用于房屋、设备租赁类合同的合规审查标准");
        template.setRequiredClauses(objectMapper.writeValueAsString(requiredClauses));
        template.setForbiddenClauses(objectMapper.writeValueAsString(forbiddenClauses));
        template.setMinAmount(500.0);
        template.setMaxAmount(5000000.0);
        template.setEnabled(true);

        templateRepository.save(template);
    }

    private void initLaborTemplate() throws JsonProcessingException {
        List<String> requiredClauses = Arrays.asList(
                "用人单位信息",
                "劳动者信息",
                "劳动合同期限",
                "工作内容和工作地点",
                "工作时间和休息休假",
                "劳动报酬条款",
                "社会保险条款",
                "劳动保护条款",
                "劳动合同解除条款",
                "违约责任条款",
                "争议解决条款"
        );

        List<String> forbiddenClauses = Arrays.asList(
            "竞业限制超过2年",
            "违约金超过法定标准",
            "免除用人单位法定责任"
        );

        ComplianceTemplate template = new ComplianceTemplate();
        template.setName("劳动合同标准模板");
        template.setContractType(ContractType.LABOR);
        template.setDescription("适用于劳动合同的合规审查标准，符合《劳动合同法》要求");
        template.setRequiredClauses(objectMapper.writeValueAsString(requiredClauses));
        template.setForbiddenClauses(objectMapper.writeValueAsString(forbiddenClauses));
        template.setMinAmount(0.0);
        template.setMaxAmount(null);
        template.setEnabled(true);

        templateRepository.save(template);
    }

    private void initSalesTemplate() throws JsonProcessingException {
        List<String> requiredClauses = Arrays.asList(
                "当事人信息条款",
                "产品描述条款",
                "价格条款",
                "付款方式条款",
                "交货条款",
                "验收条款",
                "质量保证条款",
                "售后服务条款",
                "违约责任条款",
                "争议解决条款",
                "合同生效条款"
        );

        List<String> forbiddenClauses = Arrays.asList(
            "无限连带责任",
            "单方任意解除权"
        );

        ComplianceTemplate template = new ComplianceTemplate();
        template.setName("销售合同标准模板");
        template.setContractType(ContractType.SALES);
        template.setDescription("适用于产品销售类合同的合规审查标准");
        template.setRequiredClauses(objectMapper.writeValueAsString(requiredClauses));
        template.setForbiddenClauses(objectMapper.writeValueAsString(forbiddenClauses));
        template.setMinAmount(1000.0);
        template.setMaxAmount(50000000.0);
        template.setEnabled(true);

        templateRepository.save(template);
    }

    private void initServiceTemplate() throws JsonProcessingException {
        List<String> requiredClauses = Arrays.asList(
                "当事人信息条款",
                "服务内容条款",
                "服务期限条款",
                "服务质量标准",
                "服务费用条款",
                "付款方式条款",
                "双方权利义务",
                "违约责任条款",
                "保密条款",
                "争议解决条款",
                "合同生效条款"
        );

        List<String> forbiddenClauses = Arrays.asList(
            "无限连带责任",
            "单方任意解除权",
            "自动续约且不通知"
        );

        ComplianceTemplate template = new ComplianceTemplate();
        template.setName("服务合同标准模板");
        template.setContractType(ContractType.SERVICE);
        template.setDescription("适用于各类服务类合同的合规审查标准");
        template.setRequiredClauses(objectMapper.writeValueAsString(requiredClauses));
        template.setForbiddenClauses(objectMapper.writeValueAsString(forbiddenClauses));
        template.setMinAmount(1000.0);
        template.setMaxAmount(20000000.0);
        template.setEnabled(true);

        templateRepository.save(template);
    }
}
