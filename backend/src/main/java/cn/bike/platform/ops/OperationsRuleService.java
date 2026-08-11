package cn.bike.platform.ops;

import cn.bike.platform.admin.AdminModels.UserRole;
import cn.bike.platform.common.ConflictException;
import cn.bike.platform.common.NotFoundException;
import cn.bike.platform.ops.OperationsModels.TaskRule;
import cn.bike.platform.ops.OperationsModels.TaskRuleRequest;
import cn.bike.platform.ops.OperationsModels.TriggerType;
import cn.bike.platform.security.DataPermissionService;
import cn.bike.platform.security.PlatformPrincipal;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class OperationsRuleService {

    private final OperationsRuleRepository ruleRepository;
    private final OperationsRepository taskRepository;
    private final DataPermissionService dataPermissionService;

    public OperationsRuleService(
            OperationsRuleRepository ruleRepository,
            OperationsRepository taskRepository,
            DataPermissionService dataPermissionService
    ) {
        this.ruleRepository = ruleRepository;
        this.taskRepository = taskRepository;
        this.dataPermissionService = dataPermissionService;
    }

    /** 输入: 城市; 输出: 自动任务规则列表。 */
    public List<TaskRule> findRules(String cityCode, PlatformPrincipal principal) {
        validateCityCode(cityCode);
        var permission = dataPermissionService.resolve(principal);
        return ruleRepository.findRules(cityCode).stream()
                .filter(rule -> permission.includes(rule.orgId())).toList();
    }

    /** 输入: 规则配置和管理员; 输出: 新建规则。 */
    @Transactional
    public TaskRule create(TaskRuleRequest request, PlatformPrincipal principal) {
        requireAdmin(principal);
        dataPermissionService.requireOrganization(dataPermissionService.resolve(principal), request.orgId());
        validateRequest(request);
        var id = "RULE-" + UUID.randomUUID().toString().substring(0, 12);
        try {
            ruleRepository.insertRule(id, request, principal.userId());
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("同一组织和城市下已存在同名规则");
        }
        return ruleRepository.findRule(id).orElseThrow();
    }

    /** 输入: 规则编号、版本、新配置和管理员; 输出: 更新后的规则。 */
    @Transactional
    public TaskRule update(
            String ruleId,
            int version,
            TaskRuleRequest request,
            PlatformPrincipal principal
    ) {
        requireAdmin(principal);
        var permission = dataPermissionService.resolve(principal);
        dataPermissionService.requireOrganization(permission, request.orgId());
        validateRequest(request);
        var existing = ruleRepository.findRule(ruleId).orElse(null);
        if (existing == null || !permission.includes(existing.orgId())) {
            throw new NotFoundException("自动任务规则不存在: " + ruleId);
        }
        try {
            if (ruleRepository.updateRule(ruleId, version, request) == 0) {
                throw new ConflictException("规则已被其他管理员修改，请刷新后重试");
            }
        } catch (DuplicateKeyException exception) {
            throw new ConflictException("同一组织和城市下已存在同名规则");
        }
        return ruleRepository.findRule(ruleId).orElseThrow();
    }

    /** 输入: 规则配置; 输出: 无, 检查阈值和组织归属。 */
    private void validateRequest(TaskRuleRequest request) {
        validateCityCode(request.cityCode());
        var organization = taskRepository.findOrganization(request.orgId())
                .orElseThrow(() -> new IllegalArgumentException("规则组织不存在"));
        if (!organization.active()
                || (organization.cityCode() != null && !organization.cityCode().equals(request.cityCode()))) {
            throw new IllegalArgumentException("规则组织未启用或不负责所选城市");
        }
        if (request.triggerType() == TriggerType.LOW_BATTERY && request.thresholdValue() == null) {
            throw new IllegalArgumentException("低电量规则必须填写电量阈值");
        }
        if (request.triggerType() != TriggerType.LOW_BATTERY && request.thresholdValue() != null) {
            throw new IllegalArgumentException("当前规则类型不需要阈值");
        }
    }

    private void validateCityCode(String cityCode) {
        if (cityCode == null || !cityCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("cityCode 必须是 6 位行政区划代码");
        }
    }

    private void requireAdmin(PlatformPrincipal principal) {
        if (principal.role() != UserRole.ADMIN) {
            throw new AccessDeniedException("只有管理员可以维护自动任务规则");
        }
    }
}
